/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.INPUT_CONSUMER_PIP;
import static android.view.WindowManager.INPUT_CONSUMER_WALLPAPER;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_DRAG;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_FOCUS_LIGHT;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_INPUT;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_TASK_POSITIONING;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.app.ActivityManagerNative;
import android.graphics.Rect;
import android.os.Debug;
import android.os.Looper;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;
import android.view.InputChannel;
import android.view.InputEventReceiver;
import android.view.KeyEvent;
import android.view.WindowManager;

import android.view.WindowManagerPolicy;
import com.android.server.input.InputApplicationHandle;
import com.android.server.input.InputManagerService;
import com.android.server.input.InputWindowHandle;

import java.io.PrintWriter;
import java.util.Arrays;

final class InputMonitor implements InputManagerService.WindowManagerCallbacks {
    private final WindowManagerService mService;

    // Current window with input focus for keys and other non-touch events.  May be null.
    private WindowState mInputFocus;

    // When true, prevents input dispatch from proceeding until set to false again.
    private boolean mInputDispatchFrozen;

    // The reason the input is currently frozen or null if the input isn't frozen.
    private String mInputFreezeReason = null;

    // When true, input dispatch proceeds normally.  Otherwise all events are dropped.
    // Initially false, so that input does not get dispatched until boot is finished at
    // which point the ActivityManager will enable dispatching.
    private boolean mInputDispatchEnabled;

    // When true, need to call updateInputWindowsLw().
    private boolean mUpdateInputWindowsNeeded = true;

    // Array of window handles to provide to the input dispatcher.
    private InputWindowHandle[] mInputWindowHandles;
    private int mInputWindowHandleCount;

    // Set to true when the first input device configuration change notification
    // is received to indicate that the input devices are ready.
    private final Object mInputDevicesReadyMonitor = new Object();
    private boolean mInputDevicesReady;

    /**
     * The set of input consumer added to the window manager by name, which consumes input events
     * for the windows below it.
     */
    private final ArrayMap<String, InputConsumerImpl> mInputConsumers = new ArrayMap();

    private static final class EventReceiverInputConsumer extends InputConsumerImpl
            implements WindowManagerPolicy.InputConsumer {
        private InputMonitor mInputMonitor;
        private final InputEventReceiver mInputEventReceiver;

        EventReceiverInputConsumer(WindowManagerService service, InputMonitor monitor,
                                   Looper looper, String name,
                                   InputEventReceiver.Factory inputEventReceiverFactory) {
            super(service, name, null);
            mInputMonitor = monitor;
            mInputEventReceiver = inputEventReceiverFactory.createInputEventReceiver(
                    mClientChannel, looper);
        }

        @Override
        public void dismiss() {
            synchronized (mService.mWindowMap) {
                if (mInputMonitor.destroyInputConsumer(mWindowHandle.name)) {
                    mInputEventReceiver.dispose();
                }
            }
        }
    }

    public InputMonitor(WindowManagerService service) {
        mService = service;
    }

    void addInputConsumer(String name, InputConsumerImpl consumer) {
        mInputConsumers.put(name, consumer);
        updateInputWindowsLw(true /* force */);
    }

    boolean destroyInputConsumer(String name) {
        if (disposeInputConsumer(mInputConsumers.remove(name))) {
            updateInputWindowsLw(true /* force */);
            return true;
        }
        return false;
    }

    private boolean disposeInputConsumer(InputConsumerImpl consumer) {
        if (consumer != null) {
            consumer.disposeChannelsLw();
            return true;
        }
        return false;
    }

    InputConsumerImpl getInputConsumer(String name) {
        return mInputConsumers.get(name);
    }

    void layoutInputConsumers(int dw, int dh) {
        for (int i = mInputConsumers.size() - 1; i >= 0; i--) {
            mInputConsumers.valueAt(i).layout(dw, dh);
        }
    }

    WindowManagerPolicy.InputConsumer createInputConsumer(Looper looper, String name,
            InputEventReceiver.Factory inputEventReceiverFactory) {
        if (mInputConsumers.containsKey(name)) {
            throw new IllegalStateException("Existing input consumer found with name: " + name);
        }

        final EventReceiverInputConsumer consumer = new EventReceiverInputConsumer(mService,
                this, looper, name, inputEventReceiverFactory);
        addInputConsumer(name, consumer);
        return consumer;
    }

    void createInputConsumer(String name, InputChannel inputChannel) {
        if (mInputConsumers.containsKey(name)) {
            throw new IllegalStateException("Existing input consumer found with name: " + name);
        }

        final InputConsumerImpl consumer = new InputConsumerImpl(mService, name, inputChannel);
        switch (name) {
            case INPUT_CONSUMER_WALLPAPER:
                consumer.mWindowHandle.hasWallpaper = true;
                break;
            case INPUT_CONSUMER_PIP:
                // The touchable region of the Pip input window is cropped to the bounds of the
                // stack, and we need FLAG_NOT_TOUCH_MODAL to ensure other events fall through
                consumer.mWindowHandle.layoutParamsFlags |=
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                break;
        }
        addInputConsumer(name, consumer);
    }

    /* Notifies the window manager about a broken input channel.
     *
     * Called by the InputManager.
     */
    @Override
    public void notifyInputChannelBroken(InputWindowHandle inputWindowHandle) {
        if (inputWindowHandle == null) {
            return;
        }

        synchronized (mService.mWindowMap) {
            WindowState windowState = (WindowState) inputWindowHandle.windowState;
            if (windowState != null) {
                Slog.i(TAG_WM, "WINDOW DIED " + windowState);
                windowState.removeIfPossible();
            }
        }
    }

    /* Notifies the window manager about an application that is not responding.
     * Returns a new timeout to continue waiting in nanoseconds, or 0 to abort dispatch.
     *
     * Called by the InputManager.
     */
    @Override
    public long notifyANR(InputApplicationHandle inputApplicationHandle,
            InputWindowHandle inputWindowHandle, String reason) {
        AppWindowToken appWindowToken = null;
        WindowState windowState = null;
        boolean aboveSystem = false;
        synchronized (mService.mWindowMap) {
            if (inputWindowHandle != null) {
                windowState = (WindowState) inputWindowHandle.windowState;
                if (windowState != null) {
                    appWindowToken = windowState.mAppToken;
                }
            }
            if (appWindowToken == null && inputApplicationHandle != null) {
                appWindowToken = (AppWindowToken)inputApplicationHandle.appWindowToken;
            }

            if (windowState != null) {
                Slog.i(TAG_WM, "Input event dispatching timed out "
                        + "sending to " + windowState.mAttrs.getTitle()
                        + ".  Reason: " + reason);
                // Figure out whether this window is layered above system windows.
                // We need to do this here to help the activity manager know how to
                // layer its ANR dialog.
                int systemAlertLayer = mService.mPolicy.windowTypeToLayerLw(
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                aboveSystem = windowState.mBaseLayer > systemAlertLayer;
            } else if (appWindowToken != null) {
                Slog.i(TAG_WM, "Input event dispatching timed out "
                        + "sending to application " + appWindowToken.stringName
                        + ".  Reason: " + reason);
            } else {
                Slog.i(TAG_WM, "Input event dispatching timed out "
                        + ".  Reason: " + reason);
            }

            mService.saveANRStateLocked(appWindowToken, windowState, reason);
        }

        if (appWindowToken != null && appWindowToken.appToken != null) {
            try {
                // Notify the activity manager about the timeout and let it decide whether
                // to abort dispatching or keep waiting.
                boolean abort = appWindowToken.appToken.keyDispatchingTimedOut(reason);
                if (! abort) {
                    // The activity manager declined to abort dispatching.
                    // Wait a bit longer and timeout again later.
                    return appWindowToken.inputDispatchingTimeoutNanos;
                }
            } catch (RemoteException ex) {
            }
        } else if (windowState != null) {
            try {
                // Notify the activity manager about the timeout and let it decide whether
                // to abort dispatching or keep waiting.
                long timeout = ActivityManagerNative.getDefault().inputDispatchingTimedOut(
                        windowState.mSession.mPid, aboveSystem, reason);
                if (timeout >= 0) {
                    // The activity manager declined to abort dispatching.
                    // Wait a bit longer and timeout again later.
                    return timeout * 1000000L; // nanoseconds
                }
            } catch (RemoteException ex) {
            }
        }
        return 0; // abort dispatching
    }

    void addInputWindowHandle(final InputWindowHandle windowHandle) {
        if (mInputWindowHandles == null) {
            mInputWindowHandles = new InputWindowHandle[16];
        }
        if (mInputWindowHandleCount >= mInputWindowHandles.length) {
            mInputWindowHandles = Arrays.copyOf(mInputWindowHandles,
                    mInputWindowHandleCount * 2);
        }
        mInputWindowHandles[mInputWindowHandleCount++] = windowHandle;
    }

    void addInputWindowHandle(final InputWindowHandle inputWindowHandle,
            final WindowState child, int flags, final int type, final boolean isVisible,
            final boolean hasFocus, final boolean hasWallpaper) {
        // Add a window to our list of input windows.
        inputWindowHandle.name = child.toString();
        flags = child.getTouchableRegion(inputWindowHandle.touchableRegion, flags);
        inputWindowHandle.layoutParamsFlags = flags;
        inputWindowHandle.layoutParamsType = type;
        inputWindowHandle.dispatchingTimeoutNanos = child.getInputDispatchingTimeoutNanos();
        inputWindowHandle.visible = isVisible;
        inputWindowHandle.canReceiveKeys = child.canReceiveKeys();
        inputWindowHandle.hasFocus = hasFocus;
        inputWindowHandle.hasWallpaper = hasWallpaper;
        inputWindowHandle.paused = child.mAppToken != null ? child.mAppToken.paused : false;
        inputWindowHandle.layer = child.mLayer;
        inputWindowHandle.ownerPid = child.mSession.mPid;
        inputWindowHandle.ownerUid = child.mSession.mUid;
        inputWindowHandle.inputFeatures = child.mAttrs.inputFeatures;

        final Rect frame = child.mFrame;
        inputWindowHandle.frameLeft = frame.left;
        inputWindowHandle.frameTop = frame.top;
        inputWindowHandle.frameRight = frame.right;
        inputWindowHandle.frameBottom = frame.bottom;

        if (child.mGlobalScale != 1) {
            // If we are scaling the window, input coordinates need
            // to be inversely scaled to map from what is on screen
            // to what is actually being touched in the UI.
            inputWindowHandle.scaleFactor = 1.0f/child.mGlobalScale;
        } else {
            inputWindowHandle.scaleFactor = 1;
        }

        if (DEBUG_INPUT) {
            Slog.d(TAG_WM, "addInputWindowHandle: "
                    + child + ", " + inputWindowHandle);
        }
        addInputWindowHandle(inputWindowHandle);
    }

    private void clearInputWindowHandlesLw() {
        while (mInputWindowHandleCount != 0) {
            mInputWindowHandles[--mInputWindowHandleCount] = null;
        }
    }

    public void setUpdateInputWindowsNeededLw() {
        mUpdateInputWindowsNeeded = true;
    }

    /* Updates the cached window information provided to the input dispatcher. */
    public void updateInputWindowsLw(boolean force) {
        if (!force && !mUpdateInputWindowsNeeded) {
            return;
        }
        mUpdateInputWindowsNeeded = false;

        if (false) Slog.d(TAG_WM, ">>>>>> ENTERED updateInputWindowsLw");

        // Populate the input window list with information about all of the windows that
        // could potentially receive input.
        // As an optimization, we could try to prune the list of windows but this turns
        // out to be difficult because only the native code knows for sure which window
        // currently has touch focus.

        // If there's a drag in flight, provide a pseudo-window to catch drag input
        final boolean inDrag = (mService.mDragState != null);
        if (inDrag) {
            if (DEBUG_DRAG) {
                Log.d(TAG_WM, "Inserting drag window");
            }
            final InputWindowHandle dragWindowHandle = mService.mDragState.getInputWindowHandle();
            if (dragWindowHandle != null) {
                addInputWindowHandle(dragWindowHandle);
            } else {
                Slog.w(TAG_WM, "Drag is in progress but there is no "
                        + "drag window handle.");
            }
        }

        final boolean inPositioning = (mService.mTaskPositioner != null);
        if (inPositioning) {
            if (DEBUG_TASK_POSITIONING) {
                Log.d(TAG_WM, "Inserting window handle for repositioning");
            }
            final InputWindowHandle dragWindowHandle = mService.mTaskPositioner.mDragWindowHandle;
            if (dragWindowHandle != null) {
                addInputWindowHandle(dragWindowHandle);
            } else {
                Slog.e(TAG_WM,
                        "Repositioning is in progress but there is no drag window handle.");
            }
        }

        // Add all windows on the default display.
        mService.mRoot.updateInputWindows(this, mInputFocus, inDrag);

        // Send windows to native code.
        mService.mInputManager.setInputWindows(mInputWindowHandles);

        // Clear the list in preparation for the next round.
        clearInputWindowHandlesLw();

        if (false) Slog.d(TAG_WM, "<<<<<<< EXITED updateInputWindowsLw");
    }

    /* Notifies that the input device configuration has changed. */
    @Override
    public void notifyConfigurationChanged() {
        // TODO(multi-display): Notify proper displays that are associated with this input device.
        mService.sendNewConfiguration(DEFAULT_DISPLAY);

        synchronized (mInputDevicesReadyMonitor) {
            if (!mInputDevicesReady) {
                mInputDevicesReady = true;
                mInputDevicesReadyMonitor.notifyAll();
            }
        }
    }

    /* Waits until the built-in input devices have been configured. */
    public boolean waitForInputDevicesReady(long timeoutMillis) {
        synchronized (mInputDevicesReadyMonitor) {
            if (!mInputDevicesReady) {
                try {
                    mInputDevicesReadyMonitor.wait(timeoutMillis);
                } catch (InterruptedException ex) {
                }
            }
            return mInputDevicesReady;
        }
    }

    /* Notifies that the lid switch changed state. */
    @Override
    public void notifyLidSwitchChanged(long whenNanos, boolean lidOpen) {
        mService.mPolicy.notifyLidSwitchChanged(whenNanos, lidOpen);
    }

    /* Notifies that the camera lens cover state has changed. */
    @Override
    public void notifyCameraLensCoverSwitchChanged(long whenNanos, boolean lensCovered) {
        mService.mPolicy.notifyCameraLensCoverSwitchChanged(whenNanos, lensCovered);
    }

    /* Provides an opportunity for the window manager policy to intercept early key
     * processing as soon as the key has been read from the device. */
    @Override
    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags) {
        return mService.mPolicy.interceptKeyBeforeQueueing(event, policyFlags);
    }

    /* Provides an opportunity for the window manager policy to intercept early motion event
     * processing when the device is in a non-interactive state since these events are normally
     * dropped. */
    @Override
    public int interceptMotionBeforeQueueingNonInteractive(long whenNanos, int policyFlags) {
        return mService.mPolicy.interceptMotionBeforeQueueingNonInteractive(
                whenNanos, policyFlags);
    }

    /* Provides an opportunity for the window manager policy to process a key before
     * ordinary dispatch. */
    @Override
    public long interceptKeyBeforeDispatching(
            InputWindowHandle focus, KeyEvent event, int policyFlags) {
        WindowState windowState = focus != null ? (WindowState) focus.windowState : null;
        return mService.mPolicy.interceptKeyBeforeDispatching(windowState, event, policyFlags);
    }

    /* Provides an opportunity for the window manager policy to process a key that
     * the application did not handle. */
    @Override
    public KeyEvent dispatchUnhandledKey(
            InputWindowHandle focus, KeyEvent event, int policyFlags) {
        WindowState windowState = focus != null ? (WindowState) focus.windowState : null;
        return mService.mPolicy.dispatchUnhandledKey(windowState, event, policyFlags);
    }

    /* Callback to get pointer layer. */
    @Override
    public int getPointerLayer() {
        return mService.mPolicy.windowTypeToLayerLw(WindowManager.LayoutParams.TYPE_POINTER)
                * WindowManagerService.TYPE_LAYER_MULTIPLIER
                + WindowManagerService.TYPE_LAYER_OFFSET;
    }

    /* Called when the current input focus changes.
     * Layer assignment is assumed to be complete by the time this is called.
     */
    public void setInputFocusLw(WindowState newWindow, boolean updateInputWindows) {
        if (DEBUG_FOCUS_LIGHT || DEBUG_INPUT) {
            Slog.d(TAG_WM, "Input focus has changed to " + newWindow);
        }

        if (newWindow != mInputFocus) {
            if (newWindow != null && newWindow.canReceiveKeys()) {
                // Displaying a window implicitly causes dispatching to be unpaused.
                // This is to protect against bugs if someone pauses dispatching but
                // forgets to resume.
                newWindow.mToken.paused = false;
            }

            mInputFocus = newWindow;
            setUpdateInputWindowsNeededLw();

            if (updateInputWindows) {
                updateInputWindowsLw(false /*force*/);
            }
        }
    }

    public void setFocusedAppLw(AppWindowToken newApp) {
        // Focused app has changed.
        if (newApp == null) {
            mService.mInputManager.setFocusedApplication(null);
        } else {
            final InputApplicationHandle handle = newApp.mInputApplicationHandle;
            handle.name = newApp.toString();
            handle.dispatchingTimeoutNanos = newApp.inputDispatchingTimeoutNanos;

            mService.mInputManager.setFocusedApplication(handle);
        }
    }

    public void pauseDispatchingLw(WindowToken window) {
        if (! window.paused) {
            if (DEBUG_INPUT) {
                Slog.v(TAG_WM, "Pausing WindowToken " + window);
            }

            window.paused = true;
            updateInputWindowsLw(true /*force*/);
        }
    }

    public void resumeDispatchingLw(WindowToken window) {
        if (window.paused) {
            if (DEBUG_INPUT) {
                Slog.v(TAG_WM, "Resuming WindowToken " + window);
            }

            window.paused = false;
            updateInputWindowsLw(true /*force*/);
        }
    }

    public void freezeInputDispatchingLw() {
        if (!mInputDispatchFrozen) {
            if (DEBUG_INPUT) {
                Slog.v(TAG_WM, "Freezing input dispatching");
            }

            mInputDispatchFrozen = true;

            if (DEBUG_INPUT || true) {
                mInputFreezeReason = Debug.getCallers(6);
            }
            updateInputDispatchModeLw();
        }
    }

    public void thawInputDispatchingLw() {
        if (mInputDispatchFrozen) {
            if (DEBUG_INPUT) {
                Slog.v(TAG_WM, "Thawing input dispatching");
            }

            mInputDispatchFrozen = false;
            mInputFreezeReason = null;
            updateInputDispatchModeLw();
        }
    }

    public void setEventDispatchingLw(boolean enabled) {
        if (mInputDispatchEnabled != enabled) {
            if (DEBUG_INPUT) {
                Slog.v(TAG_WM, "Setting event dispatching to " + enabled);
            }

            mInputDispatchEnabled = enabled;
            updateInputDispatchModeLw();
        }
    }

    private void updateInputDispatchModeLw() {
        mService.mInputManager.setInputDispatchMode(mInputDispatchEnabled, mInputDispatchFrozen);
    }

    void dump(PrintWriter pw, String prefix) {
        if (mInputFreezeReason != null) {
            pw.println(prefix + "mInputFreezeReason=" + mInputFreezeReason);
        }
    }
}
