/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static android.app.ActivityManager.DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT;
import static android.app.ActivityManager.DOCKED_STACK_CREATE_MODE_BOTTOM_OR_RIGHT;
import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.HOME_STACK_ID;
import static android.app.ActivityManager.StackId.PINNED_STACK_ID;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSET;
import static android.content.res.Configuration.DENSITY_DPI_UNDEFINED;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.WindowManager.DOCKED_BOTTOM;
import static android.view.WindowManager.DOCKED_INVALID;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;
import static android.view.WindowManager.DOCKED_TOP;
import static android.view.WindowManagerPolicy.FINISH_LAYOUT_REDO_WALLPAPER;
import static com.android.server.wm.DragResizeMode.DRAG_RESIZE_MODE_DOCKED_DIVIDER;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_ANIM;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_TASK_MOVEMENT;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;
import static com.android.server.wm.WindowManagerService.H.RESIZE_STACK;
import static com.android.server.wm.WindowManagerService.LAYER_OFFSET_DIM;

import android.app.ActivityManager.StackId;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Debug;
import android.os.RemoteException;
import android.util.EventLog;
import android.util.Slog;
import android.util.SparseArray;
import android.view.DisplayInfo;
import android.view.Surface;

import android.view.WindowManagerPolicy;
import com.android.internal.policy.DividerSnapAlgorithm;
import com.android.internal.policy.DividerSnapAlgorithm.SnapTarget;
import com.android.internal.policy.DockedDividerUtils;
import com.android.server.EventLogTags;

import java.io.PrintWriter;

public class TaskStack extends WindowContainer<Task> implements DimLayer.DimLayerUser,
        BoundsAnimationController.AnimateBoundsUser {
    /** Minimum size of an adjusted stack bounds relative to original stack bounds. Used to
     * restrict IME adjustment so that a min portion of top stack remains visible.*/
    private static final float ADJUSTED_STACK_FRACTION_MIN = 0.3f;

    /** Dimming amount for non-focused stack when stacks are IME-adjusted. */
    private static final float IME_ADJUST_DIM_AMOUNT = 0.25f;

    /** Unique identifier */
    final int mStackId;

    /** The service */
    private final WindowManagerService mService;

    /** The display this stack sits under. */
    // TODO: Track parent marks like this in WindowContainer.
    private DisplayContent mDisplayContent;

    /** For comparison with DisplayContent bounds. */
    private Rect mTmpRect = new Rect();
    private Rect mTmpRect2 = new Rect();

    /** Content limits relative to the DisplayContent this sits in. */
    private Rect mBounds = new Rect();

    /** Stack bounds adjusted to screen content area (taking into account IM windows, etc.) */
    private final Rect mAdjustedBounds = new Rect();

    /**
     * Fully adjusted IME bounds. These are different from {@link #mAdjustedBounds} because they
     * represent the state when the animation has ended.
     */
    private final Rect mFullyAdjustedImeBounds = new Rect();

    /** Whether mBounds is fullscreen */
    private boolean mFillsParent = true;

    // Device rotation as of the last time {@link #mBounds} was set.
    private int mRotation;

    /** Density as of last time {@link #mBounds} was set. */
    private int mDensity;

    /** Support for non-zero {@link android.view.animation.Animation#getBackgroundColor()} */
    private DimLayer mAnimationBackgroundSurface;

    /** The particular window with an Animation with non-zero background color. */
    private WindowStateAnimator mAnimationBackgroundAnimator;

    /** Application tokens that are exiting, but still on screen for animations. */
    final AppTokenList mExitingAppTokens = new AppTokenList();

    /** Detach this stack from its display when animation completes. */
    // TODO: maybe tie this to WindowContainer#removeChild some how...
    boolean mDeferRemoval;

    private final Rect mTmpAdjustedBounds = new Rect();
    private boolean mAdjustedForIme;
    private boolean mImeGoingAway;
    private WindowState mImeWin;
    private float mMinimizeAmount;
    private float mAdjustImeAmount;
    private float mAdjustDividerAmount;
    private final int mDockedStackMinimizeThickness;

    // If this is true, we are in the bounds animating mode. The task will be down or upscaled to
    // perfectly fit the region it would have been cropped to. We may also avoid certain logic we
    // would otherwise apply while resizing, while resizing in the bounds animating mode.
    private boolean mBoundsAnimating = false;

    // Temporary storage for the new bounds that should be used after the configuration change.
    // Will be cleared once the client retrieves the new bounds via getBoundsForNewConfiguration().
    private final Rect mBoundsAfterRotation = new Rect();

    TaskStack(WindowManagerService service, int stackId) {
        mService = service;
        mStackId = stackId;
        mDockedStackMinimizeThickness = service.mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.docked_stack_minimize_thickness);
        EventLog.writeEvent(EventLogTags.WM_STACK_CREATED, stackId);
    }

    DisplayContent getDisplayContent() {
        return mDisplayContent;
    }

    Task findHomeTask() {
        if (mStackId != HOME_STACK_ID) {
            return null;
        }

        for (int i = mChildren.size() - 1; i >= 0; i--) {
            if (mChildren.get(i).isHomeTask()) {
                return mChildren.get(i);
            }
        }
        return null;
    }

    boolean hasMultipleTaskWithHomeTaskNotTop() {
        return mChildren.size() > 1 && !mChildren.get(mChildren.size() - 1).isHomeTask();
    }

    boolean topTaskIsOnTopLauncher() {
        return mChildren.get(mChildren.size() - 1).isOnTopLauncher();
    }

    /**
     * Set the bounds of the stack and its containing tasks.
     * @param stackBounds New stack bounds. Passing in null sets the bounds to fullscreen.
     * @param configs Configuration for individual tasks, keyed by task id.
     * @param taskBounds Bounds for individual tasks, keyed by task id.
     * @return True if the stack bounds was changed.
     * */
    boolean setBounds(
            Rect stackBounds, SparseArray<Configuration> configs, SparseArray<Rect> taskBounds,
            SparseArray<Rect> taskTempInsetBounds) {
        setBounds(stackBounds);

        // Update bounds of containing tasks.
        for (int taskNdx = mChildren.size() - 1; taskNdx >= 0; --taskNdx) {
            final Task task = mChildren.get(taskNdx);
            Configuration config = configs.get(task.mTaskId);
            if (config != null) {
                Rect bounds = taskBounds.get(task.mTaskId);
                task.resizeLocked(bounds, config, false /* forced */);
                task.setTempInsetBounds(taskTempInsetBounds != null ?
                        taskTempInsetBounds.get(task.mTaskId) : null);
            } else {
                Slog.wtf(TAG_WM, "No config for task: " + task + ", is there a mismatch with AM?");
            }
        }
        return true;
    }

    void prepareFreezingTaskBounds() {
        for (int taskNdx = mChildren.size() - 1; taskNdx >= 0; --taskNdx) {
            final Task task = mChildren.get(taskNdx);
            task.prepareFreezingBounds();
        }
    }

    boolean isFullscreenBounds(Rect bounds) {
        if (mDisplayContent == null || bounds == null) {
            return true;
        }
        mDisplayContent.getLogicalDisplayRect(mTmpRect);
        return mTmpRect.equals(bounds);
    }

    /**
     * Overrides the adjusted bounds, i.e. sets temporary layout bounds which are different from
     * the normal task bounds.
     *
     * @param bounds The adjusted bounds.
     */
    private void setAdjustedBounds(Rect bounds) {
        if (mAdjustedBounds.equals(bounds) && !isAnimatingForIme()) {
            return;
        }

        mAdjustedBounds.set(bounds);
        final boolean adjusted = !mAdjustedBounds.isEmpty();
        Rect insetBounds = null;
        if (adjusted && isAdjustedForMinimizedDock()) {
            insetBounds = mBounds;
        } else if (adjusted && mAdjustedForIme) {
            if (mImeGoingAway) {
                insetBounds = mBounds;
            } else {
                insetBounds = mFullyAdjustedImeBounds;
            }
        }
        alignTasksToAdjustedBounds(adjusted ? mAdjustedBounds : mBounds, insetBounds);
        mDisplayContent.setLayoutNeeded();
    }

    private void alignTasksToAdjustedBounds(Rect adjustedBounds, Rect tempInsetBounds) {
        if (mFillsParent) {
            return;
        }

        final boolean alignBottom = mAdjustedForIme && getDockSide() == DOCKED_TOP;

        // Update bounds of containing tasks.
        for (int taskNdx = mChildren.size() - 1; taskNdx >= 0; --taskNdx) {
            final Task task = mChildren.get(taskNdx);
            task.alignToAdjustedBounds(adjustedBounds, tempInsetBounds, alignBottom);
        }
    }

    private boolean setBounds(Rect bounds) {
        boolean oldFullscreen = mFillsParent;
        int rotation = Surface.ROTATION_0;
        int density = DENSITY_DPI_UNDEFINED;
        if (mDisplayContent != null) {
            mDisplayContent.getLogicalDisplayRect(mTmpRect);
            rotation = mDisplayContent.getDisplayInfo().rotation;
            density = mDisplayContent.getDisplayInfo().logicalDensityDpi;
            mFillsParent = bounds == null;
            if (mFillsParent) {
                bounds = mTmpRect;
            }
        }

        if (bounds == null) {
            // Can't set to fullscreen if we don't have a display to get bounds from...
            return false;
        }
        if (mBounds.equals(bounds) && oldFullscreen == mFillsParent && mRotation == rotation) {
            return false;
        }

        if (mDisplayContent != null) {
            mDisplayContent.mDimLayerController.updateDimLayer(this);
            mAnimationBackgroundSurface.setBounds(bounds);
        }

        mBounds.set(bounds);
        mRotation = rotation;
        mDensity = density;

        updateAdjustedBounds();

        return true;
    }

    /** Bounds of the stack without adjusting for other factors in the system like visibility
     * of docked stack.
     * Most callers should be using {@link #getBounds} as it take into consideration other system
     * factors. */
    void getRawBounds(Rect out) {
        out.set(mBounds);
    }

    /** Return true if the current bound can get outputted to the rest of the system as-is. */
    private boolean useCurrentBounds() {
        if (mFillsParent
                || !StackId.isResizeableByDockedStack(mStackId)
                || mDisplayContent == null
                || mDisplayContent.getDockedStackLocked() != null) {
            return true;
        }
        return false;
    }

    public void getBounds(Rect out) {
        if (useCurrentBounds()) {
            // If we're currently adjusting for IME or minimized docked stack, we use the adjusted
            // bounds; otherwise, no need to adjust the output bounds if fullscreen or the docked
            // stack is visible since it is already what we want to represent to the rest of the
            // system.
            if (!mAdjustedBounds.isEmpty()) {
                out.set(mAdjustedBounds);
            } else {
                out.set(mBounds);
            }
            return;
        }

        // The bounds has been adjusted to accommodate for a docked stack, but the docked stack
        // is not currently visible. Go ahead a represent it as fullscreen to the rest of the
        // system.
        mDisplayContent.getLogicalDisplayRect(out);
    }

    /** Bounds of the stack with other system factors taken into consideration. */
    @Override
    public void getDimBounds(Rect out) {
        getBounds(out);
    }

    void updateDisplayInfo(Rect bounds) {
        if (mDisplayContent == null) {
            return;
        }

        for (int taskNdx = mChildren.size() - 1; taskNdx >= 0; --taskNdx) {
            mChildren.get(taskNdx).updateDisplayInfo(mDisplayContent);
        }
        if (bounds != null) {
            setBounds(bounds);
            return;
        } else if (mFillsParent) {
            setBounds(null);
            return;
        }

        mTmpRect2.set(mBounds);
        final int newRotation = mDisplayContent.getDisplayInfo().rotation;
        final int newDensity = mDisplayContent.getDisplayInfo().logicalDensityDpi;
        if (mRotation == newRotation && mDensity == newDensity) {
            setBounds(mTmpRect2);
        }

        // If the rotation or density didn't match, we'll update it in onConfigurationChanged.
    }

    /** @return true if bounds were updated to some non-empty value. */
    boolean updateBoundsAfterConfigChange() {
        if (mDisplayContent == null) {
            // If the stack is already detached we're not updating anything,
            // as it's going away soon anyway.
            return false;
        }
        final int newRotation = getDisplayInfo().rotation;
        final int newDensity = getDisplayInfo().logicalDensityDpi;

        if (mRotation == newRotation && mDensity == newDensity) {
            // Nothing to do here as we already update the state in updateDisplayInfo.
            return false;
        }

        if (mFillsParent) {
            // Update stack bounds again since rotation changed since updateDisplayInfo().
            setBounds(null);
            // Return false since we don't need the client to resize.
            return false;
        }

        mTmpRect2.set(mBounds);
        mDisplayContent.rotateBounds(mRotation, newRotation, mTmpRect2);
        if (mStackId == DOCKED_STACK_ID) {
            repositionDockedStackAfterRotation(mTmpRect2);
            snapDockedStackAfterRotation(mTmpRect2);
            final int newDockSide = getDockSide(mTmpRect2);

            // Update the dock create mode and clear the dock create bounds, these
            // might change after a rotation and the original values will be invalid.
            mService.setDockedStackCreateStateLocked(
                    (newDockSide == DOCKED_LEFT || newDockSide == DOCKED_TOP)
                    ? DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT
                    : DOCKED_STACK_CREATE_MODE_BOTTOM_OR_RIGHT,
                    null);
            mDisplayContent.getDockedDividerController().notifyDockSideChanged(newDockSide);
        }

        mBoundsAfterRotation.set(mTmpRect2);
        return true;
    }

    void getBoundsForNewConfiguration(Rect outBounds) {
        outBounds.set(mBoundsAfterRotation);
        mBoundsAfterRotation.setEmpty();
    }

    /**
     * Some dock sides are not allowed by the policy. This method queries the policy and moves
     * the docked stack around if needed.
     *
     * @param inOutBounds the bounds of the docked stack to adjust
     */
    private void repositionDockedStackAfterRotation(Rect inOutBounds) {
        int dockSide = getDockSide(inOutBounds);
        if (mService.mPolicy.isDockSideAllowed(dockSide)) {
            return;
        }
        mDisplayContent.getLogicalDisplayRect(mTmpRect);
        dockSide = DockedDividerUtils.invertDockSide(dockSide);
        switch (dockSide) {
            case DOCKED_LEFT:
                int movement = inOutBounds.left;
                inOutBounds.left -= movement;
                inOutBounds.right -= movement;
                break;
            case DOCKED_RIGHT:
                movement = mTmpRect.right - inOutBounds.right;
                inOutBounds.left += movement;
                inOutBounds.right += movement;
                break;
            case DOCKED_TOP:
                movement = inOutBounds.top;
                inOutBounds.top -= movement;
                inOutBounds.bottom -= movement;
                break;
            case DOCKED_BOTTOM:
                movement = mTmpRect.bottom - inOutBounds.bottom;
                inOutBounds.top += movement;
                inOutBounds.bottom += movement;
                break;
        }
    }

    /**
     * Snaps the bounds after rotation to the closest snap target for the docked stack.
     */
    private void snapDockedStackAfterRotation(Rect outBounds) {

        // Calculate the current position.
        final DisplayInfo displayInfo = mDisplayContent.getDisplayInfo();
        final int dividerSize = mDisplayContent.getDockedDividerController().getContentWidth();
        final int dockSide = getDockSide(outBounds);
        final int dividerPosition = DockedDividerUtils.calculatePositionForBounds(outBounds,
                dockSide, dividerSize);
        final int displayWidth = mDisplayContent.getDisplayInfo().logicalWidth;
        final int displayHeight = mDisplayContent.getDisplayInfo().logicalHeight;

        // Snap the position to a target.
        final int rotation = displayInfo.rotation;
        final int orientation = mDisplayContent.getConfiguration().orientation;
        mService.mPolicy.getStableInsetsLw(rotation, displayWidth, displayHeight, outBounds);
        final DividerSnapAlgorithm algorithm = new DividerSnapAlgorithm(
                mService.mContext.getResources(), displayWidth, displayHeight,
                dividerSize, orientation == Configuration.ORIENTATION_PORTRAIT, outBounds);
        final SnapTarget target = algorithm.calculateNonDismissingSnapTarget(dividerPosition);

        // Recalculate the bounds based on the position of the target.
        DockedDividerUtils.calculateBoundsForPosition(target.position, dockSide,
                outBounds, displayInfo.logicalWidth, displayInfo.logicalHeight,
                dividerSize);
    }

    // TODO: Checkout the call points of this method and the ones below to see how they can fit in WC.
    void addTask(Task task, boolean toTop) {
        addTask(task, toTop, task.showForAllUsers());
    }

    /**
     * Put a Task in this stack. Used for adding and moving.
     * @param task The task to add.
     * @param toTop Whether to add it to the top or bottom.
     * @param showForAllUsers Whether to show the task regardless of the current user.
     */
    void addTask(Task task, boolean toTop, boolean showForAllUsers) {
        positionTask(task, toTop ? mChildren.size() : 0, showForAllUsers);
    }

    // TODO: We should really have users as a window container in the hierarchy so that we don't
    // have to do complicated things like we are doing in this method and also also the method to
    // just be WindowContainer#addChild
    void positionTask(Task task, int position, boolean showForAllUsers) {
        final boolean canShowTask =
                showForAllUsers || mService.isCurrentProfileLocked(task.mUserId);
        if (mChildren.contains(task)) {
            super.removeChild(task);
        }
        int stackSize = mChildren.size();
        int minPosition = 0;
        int maxPosition = stackSize;

        if (canShowTask) {
            minPosition = computeMinPosition(minPosition, stackSize);
        } else {
            maxPosition = computeMaxPosition(maxPosition);
        }
        // Reset position based on minimum/maximum possible positions.
        position = Math.min(Math.max(position, minPosition), maxPosition);

        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG_WM,
                "positionTask: task=" + task + " position=" + position);
        addChild(task, position);
        task.mStack = this;
        task.updateDisplayInfo(mDisplayContent);
        boolean toTop = position == mChildren.size() - 1;
        if (toTop) {
            // TODO: Have a WidnowContainer method that moves all parents of a container to the
            // front for cases like this.
            mDisplayContent.moveStack(this, true);
        }

        if (StackId.windowsAreScaleable(mStackId)) {
            // We force windows out of SCALING_MODE_FREEZE so that we can continue to animate them
            // while a resize is pending.
            task.forceWindowsScaleable(true);
        } else {
            task.forceWindowsScaleable(false);
        }
        EventLog.writeEvent(EventLogTags.WM_TASK_MOVED, task.mTaskId, toTop ? 1 : 0, position);
    }

    /** Calculate the minimum possible position for a task that can be shown to the user.
     *  The minimum position will be above all other tasks that can't be shown.
     *  @param minPosition The minimum position the caller is suggesting.
     *                  We will start adjusting up from here.
     *  @param size The size of the current task list.
     */
    private int computeMinPosition(int minPosition, int size) {
        while (minPosition < size) {
            final Task tmpTask = mChildren.get(minPosition);
            final boolean canShowTmpTask =
                    tmpTask.showForAllUsers()
                            || mService.isCurrentProfileLocked(tmpTask.mUserId);
            if (canShowTmpTask) {
                break;
            }
            minPosition++;
        }
        return minPosition;
    }

    /** Calculate the maximum possible position for a task that can't be shown to the user.
     *  The maximum position will be below all other tasks that can be shown.
     *  @param maxPosition The maximum position the caller is suggesting.
     *                  We will start adjusting down from here.
     */
    private int computeMaxPosition(int maxPosition) {
        while (maxPosition > 0) {
            final Task tmpTask = mChildren.get(maxPosition - 1);
            final boolean canShowTmpTask =
                    tmpTask.showForAllUsers()
                            || mService.isCurrentProfileLocked(tmpTask.mUserId);
            if (!canShowTmpTask) {
                break;
            }
            maxPosition--;
        }
        return maxPosition;
    }

    // TODO: Have functionality in WC to move things to the bottom or top. Also, look at the call
    // points for this methods to see if we need functionality to move something to the front/bottom
    // with its parents.
    void moveTaskToTop(Task task) {
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG_WM, "moveTaskToTop: task=" + task + " Callers="
                + Debug.getCallers(6));
        addTask(task, true);
    }

    void moveTaskToBottom(Task task) {
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG_WM, "moveTaskToBottom: task=" + task);
        addTask(task, false);
    }

    /**
     * Delete a Task from this stack. If it is the last Task in the stack, move this stack to the
     * back.
     * @param task The Task to delete.
     */
    @Override
    void removeChild(Task task) {
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG_WM, "removeChild: task=" + task);

        super.removeChild(task);

        if (mDisplayContent != null) {
            if (mChildren.isEmpty()) {
                mDisplayContent.moveStack(this, false);
            }
            mDisplayContent.setLayoutNeeded();
        }
        for (int appNdx = mExitingAppTokens.size() - 1; appNdx >= 0; --appNdx) {
            final AppWindowToken wtoken = mExitingAppTokens.get(appNdx);
            if (wtoken.mTask == task) {
                wtoken.mIsExiting = false;
                mExitingAppTokens.remove(appNdx);
            }
        }
    }

    void onDisplayChanged(DisplayContent dc) {
        if (mDisplayContent != null) {
            throw new IllegalStateException("onDisplayChanged: Already attached");
        }

        mDisplayContent = dc;
        mAnimationBackgroundSurface = new DimLayer(mService, this, mDisplayContent.getDisplayId(),
                "animation background stackId=" + mStackId);

        Rect bounds = null;
        final TaskStack dockedStack = mService.mStackIdToStack.get(DOCKED_STACK_ID);
        if (mStackId == DOCKED_STACK_ID
                || (dockedStack != null && StackId.isResizeableByDockedStack(mStackId)
                        && !dockedStack.fillsParent())) {
            // The existence of a docked stack affects the size of other static stack created since
            // the docked stack occupies a dedicated region on screen, but only if the dock stack is
            // not fullscreen. If it's fullscreen, it means that we are in the transition of
            // dismissing it, so we must not resize this stack.
            bounds = new Rect();
            dc.getLogicalDisplayRect(mTmpRect);
            mTmpRect2.setEmpty();
            if (dockedStack != null) {
                dockedStack.getRawBounds(mTmpRect2);
            }
            final boolean dockedOnTopOrLeft = mService.mDockedStackCreateMode
                    == DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT;
            getStackDockedModeBounds(mTmpRect, bounds, mStackId, mTmpRect2,
                    mDisplayContent.mDividerControllerLocked.getContentWidth(),
                    dockedOnTopOrLeft);
        }

        updateDisplayInfo(bounds);

        super.onDisplayChanged(dc);
    }

    void getStackDockedModeBoundsLocked(Rect outBounds, boolean ignoreVisibility) {
        if ((mStackId != DOCKED_STACK_ID && !StackId.isResizeableByDockedStack(mStackId))
                || mDisplayContent == null) {
            outBounds.set(mBounds);
            return;
        }

        final TaskStack dockedStack = mService.mStackIdToStack.get(DOCKED_STACK_ID);
        if (dockedStack == null) {
            // Not sure why you are calling this method when there is no docked stack...
            throw new IllegalStateException(
                    "Calling getStackDockedModeBoundsLocked() when there is no docked stack.");
        }
        if (!ignoreVisibility && !dockedStack.isVisible()) {
            // The docked stack is being dismissed, but we caught before it finished being
            // dismissed. In that case we want to treat it as if it is not occupying any space and
            // let others occupy the whole display.
            mDisplayContent.getLogicalDisplayRect(outBounds);
            return;
        }

        final int dockedSide = dockedStack.getDockSide();
        if (dockedSide == DOCKED_INVALID) {
            // Not sure how you got here...Only thing we can do is return current bounds.
            Slog.e(TAG_WM, "Failed to get valid docked side for docked stack=" + dockedStack);
            outBounds.set(mBounds);
            return;
        }

        mDisplayContent.getLogicalDisplayRect(mTmpRect);
        dockedStack.getRawBounds(mTmpRect2);
        final boolean dockedOnTopOrLeft = dockedSide == DOCKED_TOP || dockedSide == DOCKED_LEFT;
        getStackDockedModeBounds(mTmpRect, outBounds, mStackId, mTmpRect2,
                mDisplayContent.mDividerControllerLocked.getContentWidth(), dockedOnTopOrLeft);

    }

    /**
     * Outputs the bounds a stack should be given the presence of a docked stack on the display.
     * @param displayRect The bounds of the display the docked stack is on.
     * @param outBounds Output bounds that should be used for the stack.
     * @param stackId Id of stack we are calculating the bounds for.
     * @param dockedBounds Bounds of the docked stack.
     * @param dockDividerWidth We need to know the width of the divider make to the output bounds
     *                         close to the side of the dock.
     * @param dockOnTopOrLeft If the docked stack is on the top or left side of the screen.
     */
    private void getStackDockedModeBounds(
            Rect displayRect, Rect outBounds, int stackId, Rect dockedBounds, int dockDividerWidth,
            boolean dockOnTopOrLeft) {
        final boolean dockedStack = stackId == DOCKED_STACK_ID;
        final boolean splitHorizontally = displayRect.width() > displayRect.height();

        outBounds.set(displayRect);
        if (dockedStack) {
            if (mService.mDockedStackCreateBounds != null) {
                outBounds.set(mService.mDockedStackCreateBounds);
                return;
            }

            // The initial bounds of the docked stack when it is created about half the screen space
            // and its bounds can be adjusted after that. The bounds of all other stacks are
            // adjusted to occupy whatever screen space the docked stack isn't occupying.
            final DisplayInfo di = mDisplayContent.getDisplayInfo();
            mService.mPolicy.getStableInsetsLw(di.rotation, di.logicalWidth, di.logicalHeight,
                    mTmpRect2);
            final int position = new DividerSnapAlgorithm(mService.mContext.getResources(),
                    di.logicalWidth,
                    di.logicalHeight,
                    dockDividerWidth,
                    mDisplayContent.getConfiguration().orientation == ORIENTATION_PORTRAIT,
                    mTmpRect2).getMiddleTarget().position;

            if (dockOnTopOrLeft) {
                if (splitHorizontally) {
                    outBounds.right = position;
                } else {
                    outBounds.bottom = position;
                }
            } else {
                if (splitHorizontally) {
                    outBounds.left = position + dockDividerWidth;
                } else {
                    outBounds.top = position + dockDividerWidth;
                }
            }
            return;
        }

        // Other stacks occupy whatever space is left by the docked stack.
        if (!dockOnTopOrLeft) {
            if (splitHorizontally) {
                outBounds.right = dockedBounds.left - dockDividerWidth;
            } else {
                outBounds.bottom = dockedBounds.top - dockDividerWidth;
            }
        } else {
            if (splitHorizontally) {
                outBounds.left = dockedBounds.right + dockDividerWidth;
            } else {
                outBounds.top = dockedBounds.bottom + dockDividerWidth;
            }
        }
        DockedDividerUtils.sanitizeStackBounds(outBounds, !dockOnTopOrLeft);
    }

    void resetDockedStackToMiddle() {
        if (mStackId != DOCKED_STACK_ID) {
            throw new IllegalStateException("Not a docked stack=" + this);
        }

        mService.mDockedStackCreateBounds = null;

        final Rect bounds = new Rect();
        getStackDockedModeBoundsLocked(bounds, true /*ignoreVisibility*/);
        mService.mH.obtainMessage(RESIZE_STACK, DOCKED_STACK_ID,
                1 /*allowResizeInDockedMode*/, bounds).sendToTarget();
    }

    @Override
    void removeIfPossible() {
        if (isAnimating()) {
            mDeferRemoval = true;
            return;
        }
        removeImmediately();
    }

    @Override
    void removeImmediately() {
        super.removeImmediately();

        mDisplayContent.mDimLayerController.removeDimLayerUser(this);
        EventLog.writeEvent(EventLogTags.WM_STACK_REMOVED, mStackId);

        if (mAnimationBackgroundSurface != null) {
            mAnimationBackgroundSurface.destroySurface();
            mAnimationBackgroundSurface = null;
        }
        final DockedStackDividerController dividerController =
                mDisplayContent.mDividerControllerLocked;
        mDisplayContent = null;

        mService.mWindowPlacerLocked.requestTraversal();

        if (mStackId == DOCKED_STACK_ID) {
            dividerController.notifyDockedStackExistsChanged(false);
        }
    }

    void resetAnimationBackgroundAnimator() {
        mAnimationBackgroundAnimator = null;
        mAnimationBackgroundSurface.hide();
    }

    void setAnimationBackground(WindowStateAnimator winAnimator, int color) {
        int animLayer = winAnimator.mAnimLayer;
        if (mAnimationBackgroundAnimator == null
                || animLayer < mAnimationBackgroundAnimator.mAnimLayer) {
            mAnimationBackgroundAnimator = winAnimator;
            animLayer = mDisplayContent.getLayerForAnimationBackground(winAnimator);
            mAnimationBackgroundSurface.show(animLayer - LAYER_OFFSET_DIM,
                    ((color >> 24) & 0xff) / 255f, 0);
        }
    }

    // TODO: Should each user have there own stacks?
    void switchUser() {
        int top = mChildren.size();
        for (int taskNdx = 0; taskNdx < top; ++taskNdx) {
            Task task = mChildren.get(taskNdx);
            if (mService.isCurrentProfileLocked(task.mUserId) || task.showForAllUsers()) {
                mChildren.remove(taskNdx);
                mChildren.add(task);
                --top;
            }
        }
    }

    /**
     * Adjusts the stack bounds if the IME is visible.
     *
     * @param imeWin The IME window.
     */
    void setAdjustedForIme(WindowState imeWin, boolean forceUpdate) {
        mImeWin = imeWin;
        mImeGoingAway = false;
        if (!mAdjustedForIme || forceUpdate) {
            mAdjustedForIme = true;
            mAdjustImeAmount = 0f;
            mAdjustDividerAmount = 0f;
            updateAdjustForIme(0f, 0f, true /* force */);
        }
    }

    boolean isAdjustedForIme() {
        return mAdjustedForIme;
    }

    boolean isAnimatingForIme() {
        return mImeWin != null && mImeWin.isAnimatingLw();
    }

    /**
     * Update the stack's bounds (crop or position) according to the IME window's
     * current position. When IME window is animated, the bottom stack is animated
     * together to track the IME window's current position, and the top stack is
     * cropped as necessary.
     *
     * @return true if a traversal should be performed after the adjustment.
     */
    boolean updateAdjustForIme(float adjustAmount, float adjustDividerAmount, boolean force) {
        if (adjustAmount != mAdjustImeAmount
                || adjustDividerAmount != mAdjustDividerAmount || force) {
            mAdjustImeAmount = adjustAmount;
            mAdjustDividerAmount = adjustDividerAmount;
            updateAdjustedBounds();
            return isVisible(true /* ignoreKeyguard */);
        } else {
            return false;
        }
    }

    /**
     * Resets the adjustment after it got adjusted for the IME.
     * @param adjustBoundsNow if true, reset and update the bounds immediately and forget about
     *                        animations; otherwise, set flag and animates the window away together
     *                        with IME window.
     */
    void resetAdjustedForIme(boolean adjustBoundsNow) {
        if (adjustBoundsNow) {
            mImeWin = null;
            mAdjustedForIme = false;
            mImeGoingAway = false;
            mAdjustImeAmount = 0f;
            mAdjustDividerAmount = 0f;
            updateAdjustedBounds();
            mService.setResizeDimLayer(false, mStackId, 1.0f);
        } else {
            mImeGoingAway |= mAdjustedForIme;
        }
    }

    /**
     * Sets the amount how much we currently minimize our stack.
     *
     * @param minimizeAmount The amount, between 0 and 1.
     * @return Whether the amount has changed and a layout is needed.
     */
    boolean setAdjustedForMinimizedDock(float minimizeAmount) {
        if (minimizeAmount != mMinimizeAmount) {
            mMinimizeAmount = minimizeAmount;
            updateAdjustedBounds();
            return isVisible(true /* ignoreKeyguard */);
        } else {
            return false;
        }
    }

    boolean isAdjustedForMinimizedDock() {
        return mMinimizeAmount != 0f;
    }

    /**
     * Puts all visible tasks that are adjusted for IME into resizing mode and adds the windows
     * to the list of to be drawn windows the service is waiting for.
     */
    void beginImeAdjustAnimation() {
        for (int j = mChildren.size() - 1; j >= 0; j--) {
            final Task task = mChildren.get(j);
            if (task.hasContentToDisplay()) {
                task.setDragResizing(true, DRAG_RESIZE_MODE_DOCKED_DIVIDER);
                task.setWaitingForDrawnIfResizingChanged();
            }
        }
    }

    /**
     * Resets the resizing state of all windows.
     */
    void endImeAdjustAnimation() {
        for (int j = mChildren.size() - 1; j >= 0; j--) {
            mChildren.get(j).setDragResizing(false, DRAG_RESIZE_MODE_DOCKED_DIVIDER);
        }
    }

    int getMinTopStackBottom(final Rect displayContentRect, int originalStackBottom) {
        return displayContentRect.top + (int)
                ((originalStackBottom - displayContentRect.top) * ADJUSTED_STACK_FRACTION_MIN);
    }

    private boolean adjustForIME(final WindowState imeWin) {
        final int dockedSide = getDockSide();
        final boolean dockedTopOrBottom = dockedSide == DOCKED_TOP || dockedSide == DOCKED_BOTTOM;
        if (imeWin == null || !dockedTopOrBottom) {
            return false;
        }

        final Rect displayContentRect = mTmpRect;
        final Rect contentBounds = mTmpRect2;

        // Calculate the content bounds excluding the area occupied by IME
        getDisplayContent().getContentRect(displayContentRect);
        contentBounds.set(displayContentRect);
        int imeTop = Math.max(imeWin.getFrameLw().top, contentBounds.top);

        imeTop += imeWin.getGivenContentInsetsLw().top;
        if (contentBounds.bottom > imeTop) {
            contentBounds.bottom = imeTop;
        }

        final int yOffset = displayContentRect.bottom - contentBounds.bottom;

        final int dividerWidth =
                getDisplayContent().mDividerControllerLocked.getContentWidth();
        final int dividerWidthInactive =
                getDisplayContent().mDividerControllerLocked.getContentWidthInactive();

        if (dockedSide == DOCKED_TOP) {
            // If this stack is docked on top, we make it smaller so the bottom stack is not
            // occluded by IME. We shift its bottom up by the height of the IME, but
            // leaves at least 30% of the top stack visible.
            final int minTopStackBottom =
                    getMinTopStackBottom(displayContentRect, mBounds.bottom);
            final int bottom = Math.max(
                    mBounds.bottom - yOffset + dividerWidth - dividerWidthInactive,
                    minTopStackBottom);
            mTmpAdjustedBounds.set(mBounds);
            mTmpAdjustedBounds.bottom =
                    (int) (mAdjustImeAmount * bottom + (1 - mAdjustImeAmount) * mBounds.bottom);
            mFullyAdjustedImeBounds.set(mBounds);
        } else {
            // When the stack is on bottom and has no focus, it's only adjusted for divider width.
            final int dividerWidthDelta = dividerWidthInactive - dividerWidth;

            // When the stack is on bottom and has focus, it needs to be moved up so as to
            // not occluded by IME, and at the same time adjusted for divider width.
            // We try to move it up by the height of the IME window, but only to the extent
            // that leaves at least 30% of the top stack visible.
            // 'top' is where the top of bottom stack will move to in this case.
            final int topBeforeImeAdjust = mBounds.top - dividerWidth + dividerWidthInactive;
            final int minTopStackBottom =
                    getMinTopStackBottom(displayContentRect, mBounds.top - dividerWidth);
            final int top = Math.max(
                    mBounds.top - yOffset, minTopStackBottom + dividerWidthInactive);

            mTmpAdjustedBounds.set(mBounds);
            // Account for the adjustment for IME and divider width separately.
            // (top - topBeforeImeAdjust) is the amount of movement due to IME only,
            // and dividerWidthDelta is due to divider width change only.
            mTmpAdjustedBounds.top = mBounds.top +
                    (int) (mAdjustImeAmount * (top - topBeforeImeAdjust) +
                            mAdjustDividerAmount * dividerWidthDelta);
            mFullyAdjustedImeBounds.set(mBounds);
            mFullyAdjustedImeBounds.top = top;
            mFullyAdjustedImeBounds.bottom = top + mBounds.height();
        }
        return true;
    }

    private boolean adjustForMinimizedDockedStack(float minimizeAmount) {
        final int dockSide = getDockSide();
        if (dockSide == DOCKED_INVALID && !mTmpAdjustedBounds.isEmpty()) {
            return false;
        }

        if (dockSide == DOCKED_TOP) {
            mService.getStableInsetsLocked(mTmpRect);
            int topInset = mTmpRect.top;
            mTmpAdjustedBounds.set(mBounds);
            mTmpAdjustedBounds.bottom =
                    (int) (minimizeAmount * topInset + (1 - minimizeAmount) * mBounds.bottom);
        } else if (dockSide == DOCKED_LEFT) {
            mTmpAdjustedBounds.set(mBounds);
            final int width = mBounds.width();
            mTmpAdjustedBounds.right =
                    (int) (minimizeAmount * mDockedStackMinimizeThickness
                            + (1 - minimizeAmount) * mBounds.right);
            mTmpAdjustedBounds.left = mTmpAdjustedBounds.right - width;
        } else if (dockSide == DOCKED_RIGHT) {
            mTmpAdjustedBounds.set(mBounds);
            mTmpAdjustedBounds.left =
                    (int) (minimizeAmount * (mBounds.right - mDockedStackMinimizeThickness)
                            + (1 - minimizeAmount) * mBounds.left);
        }
        return true;
    }

    /**
     * @return the distance in pixels how much the stack gets minimized from it's original size
     */
    int getMinimizeDistance() {
        final int dockSide = getDockSide();
        if (dockSide == DOCKED_INVALID) {
            return 0;
        }

        if (dockSide == DOCKED_TOP) {
            mService.getStableInsetsLocked(mTmpRect);
            int topInset = mTmpRect.top;
            return mBounds.bottom - topInset;
        } else if (dockSide == DOCKED_LEFT || dockSide == DOCKED_RIGHT) {
            return mBounds.width() - mDockedStackMinimizeThickness;
        } else {
            return 0;
        }
    }

    /**
     * Updates the adjustment depending on it's current state.
     */
    private void updateAdjustedBounds() {
        boolean adjust = false;
        if (mMinimizeAmount != 0f) {
            adjust = adjustForMinimizedDockedStack(mMinimizeAmount);
        } else if (mAdjustedForIme) {
            adjust = adjustForIME(mImeWin);
        }
        if (!adjust) {
            mTmpAdjustedBounds.setEmpty();
        }
        setAdjustedBounds(mTmpAdjustedBounds);

        final boolean isImeTarget = (mService.getImeFocusStackLocked() == this);
        if (mAdjustedForIme && adjust && !isImeTarget) {
            final float alpha = Math.max(mAdjustImeAmount, mAdjustDividerAmount)
                    * IME_ADJUST_DIM_AMOUNT;
            mService.setResizeDimLayer(true, mStackId, alpha);
        }
    }

    void applyAdjustForImeIfNeeded(Task task) {
        if (mMinimizeAmount != 0f || !mAdjustedForIme || mAdjustedBounds.isEmpty()) {
            return;
        }

        final Rect insetBounds = mImeGoingAway ? mBounds : mFullyAdjustedImeBounds;
        task.alignToAdjustedBounds(mAdjustedBounds, insetBounds, getDockSide() == DOCKED_TOP);
        mDisplayContent.setLayoutNeeded();
    }

    boolean isAdjustedForMinimizedDockedStack() {
        return mMinimizeAmount != 0f;
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "mStackId=" + mStackId);
        pw.println(prefix + "mDeferRemoval=" + mDeferRemoval);
        pw.println(prefix + "mFillsParent=" + mFillsParent);
        pw.println(prefix + "mBounds=" + mBounds.toShortString());
        if (mMinimizeAmount != 0f) {
            pw.println(prefix + "mMinimizeAmount=" + mMinimizeAmount);
        }
        if (mAdjustedForIme) {
            pw.println(prefix + "mAdjustedForIme=true");
            pw.println(prefix + "mAdjustImeAmount=" + mAdjustImeAmount);
            pw.println(prefix + "mAdjustDividerAmount=" + mAdjustDividerAmount);
        }
        if (!mAdjustedBounds.isEmpty()) {
            pw.println(prefix + "mAdjustedBounds=" + mAdjustedBounds.toShortString());
        }
        for (int taskNdx = mChildren.size() - 1; taskNdx >= 0; taskNdx--) {
            mChildren.get(taskNdx).dump(prefix + "  ", pw);
        }
        if (mAnimationBackgroundSurface.isDimming()) {
            pw.println(prefix + "mWindowAnimationBackgroundSurface:");
            mAnimationBackgroundSurface.printTo(prefix + "  ", pw);
        }
        if (!mExitingAppTokens.isEmpty()) {
            pw.println();
            pw.println("  Exiting application tokens:");
            for (int i = mExitingAppTokens.size() - 1; i >= 0; i--) {
                WindowToken token = mExitingAppTokens.get(i);
                pw.print("  Exiting App #"); pw.print(i);
                pw.print(' '); pw.print(token);
                pw.println(':');
                token.dump(pw, "    ");
            }
        }
    }

    /** Fullscreen status of the stack without adjusting for other factors in the system like
     * visibility of docked stack.
     * Most callers should be using {@link #fillsParent} as it take into consideration other
     * system factors. */
    boolean getRawFullscreen() {
        return mFillsParent;
    }

    @Override
    public boolean dimFullscreen() {
        return mStackId == HOME_STACK_ID || fillsParent();
    }

    @Override
    boolean fillsParent() {
        if (useCurrentBounds()) {
            return mFillsParent;
        }
        // The bounds has been adjusted to accommodate for a docked stack, but the docked stack
        // is not currently visible. Go ahead a represent it as fullscreen to the rest of the
        // system.
        return true;
    }

    @Override
    public DisplayInfo getDisplayInfo() {
        return mDisplayContent.getDisplayInfo();
    }

    @Override
    public String toString() {
        return "{stackId=" + mStackId + " tasks=" + mChildren + "}";
    }

    String getName() {
        return toShortString();
    }

    @Override
    public String toShortString() {
        return "Stack=" + mStackId;
    }

    /**
     * For docked workspace (or workspace that's side-by-side to the docked), provides
     * information which side of the screen was the dock anchored.
     */
    int getDockSide() {
        return getDockSide(mBounds);
    }

    int getDockSide(Rect bounds) {
        if (mStackId != DOCKED_STACK_ID && !StackId.isResizeableByDockedStack(mStackId)) {
            return DOCKED_INVALID;
        }
        if (mDisplayContent == null) {
            return DOCKED_INVALID;
        }
        mDisplayContent.getLogicalDisplayRect(mTmpRect);
        final int orientation = mDisplayContent.getConfiguration().orientation;
        return getDockSideUnchecked(bounds, mTmpRect, orientation);
    }

    static int getDockSideUnchecked(Rect bounds, Rect displayRect, int orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Portrait mode, docked either at the top or the bottom.
            if (bounds.top - displayRect.top <= displayRect.bottom - bounds.bottom) {
                return DOCKED_TOP;
            } else {
                return DOCKED_BOTTOM;
            }
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Landscape mode, docked either on the left or on the right.
            if (bounds.left - displayRect.left <= displayRect.right - bounds.right) {
                return DOCKED_LEFT;
            } else {
                return DOCKED_RIGHT;
            }
        } else {
            return DOCKED_INVALID;
        }
    }

    boolean isVisible() {
        return isVisible(false /* ignoreKeyguard */);
    }

    boolean isVisible(boolean ignoreKeyguard) {
        final boolean keyguardOn = mService.mPolicy.isKeyguardShowingOrOccluded()
                && !mService.mAnimator.mKeyguardGoingAway;
        if (!ignoreKeyguard && keyguardOn && !StackId.isAllowedOverLockscreen(mStackId)) {
            // The keyguard is showing and the stack shouldn't show on top of the keyguard.
            return false;
        }

        return super.isVisible();
    }

    boolean hasTaskForUser(int userId) {
        for (int i = mChildren.size() - 1; i >= 0; i--) {
            final Task task = mChildren.get(i);
            if (task.mUserId == userId) {
                return true;
            }
        }
        return false;
    }

    int taskIdFromPoint(int x, int y) {
        getBounds(mTmpRect);
        if (!mTmpRect.contains(x, y) || isAdjustedForMinimizedDockedStack()) {
            return -1;
        }

        for (int taskNdx = mChildren.size() - 1; taskNdx >= 0; --taskNdx) {
            final Task task = mChildren.get(taskNdx);
            final WindowState win = task.getTopVisibleAppMainWindow();
            if (win == null) {
                continue;
            }
            // We need to use the task's dim bounds (which is derived from the visible bounds of its
            // apps windows) for any touch-related tests. Can't use the task's original bounds
            // because it might be adjusted to fit the content frame. For example, the presence of
            // the IME adjusting the windows frames when the app window is the IME target.
            task.getDimBounds(mTmpRect);
            if (mTmpRect.contains(x, y)) {
                return task.mTaskId;
            }
        }

        return -1;
    }

    void findTaskForResizePoint(int x, int y, int delta,
            DisplayContent.TaskForResizePointSearchResult results) {
        if (!StackId.isTaskResizeAllowed(mStackId)) {
            results.searchDone = true;
            return;
        }

        for (int i = mChildren.size() - 1; i >= 0; --i) {
            final Task task = mChildren.get(i);
            if (task.isFullscreen()) {
                results.searchDone = true;
                return;
            }

            // We need to use the task's dim bounds (which is derived from the visible bounds of
            // its apps windows) for any touch-related tests. Can't use the task's original
            // bounds because it might be adjusted to fit the content frame. One example is when
            // the task is put to top-left quadrant, the actual visible area would not start at
            // (0,0) after it's adjusted for the status bar.
            task.getDimBounds(mTmpRect);
            mTmpRect.inset(-delta, -delta);
            if (mTmpRect.contains(x, y)) {
                mTmpRect.inset(delta, delta);

                results.searchDone = true;

                if (!mTmpRect.contains(x, y)) {
                    results.taskForResize = task;
                    return;
                }
                // User touched inside the task. No need to look further,
                // focus transfer will be handled in ACTION_UP.
                return;
            }
        }
    }

    void setTouchExcludeRegion(Task focusedTask, int delta, Region touchExcludeRegion,
            Rect contentRect, Rect postExclude) {
        for (int i = mChildren.size() - 1; i >= 0; --i) {
            final Task task = mChildren.get(i);
            AppWindowToken token = task.getTopVisibleAppToken();
            if (token == null || !token.hasContentToDisplay()) {
                continue;
            }

            /**
             * Exclusion region is the region that TapDetector doesn't care about.
             * Here we want to remove all non-focused tasks from the exclusion region.
             * We also remove the outside touch area for resizing for all freeform
             * tasks (including the focused).
             *
             * We save the focused task region once we find it, and add it back at the end.
             */

            task.getDimBounds(mTmpRect);

            if (task == focusedTask) {
                // Add the focused task rect back into the exclude region once we are done
                // processing stacks.
                postExclude.set(mTmpRect);
            }

            final boolean isFreeformed = task.inFreeformWorkspace();
            if (task != focusedTask || isFreeformed) {
                if (isFreeformed) {
                    // If the task is freeformed, enlarge the area to account for outside
                    // touch area for resize.
                    mTmpRect.inset(-delta, -delta);
                    // Intersect with display content rect. If we have system decor (status bar/
                    // navigation bar), we want to exclude that from the tap detection.
                    // Otherwise, if the app is partially placed under some system button (eg.
                    // Recents, Home), pressing that button would cause a full series of
                    // unwanted transfer focus/resume/pause, before we could go home.
                    mTmpRect.intersect(contentRect);
                }
                touchExcludeRegion.op(mTmpRect, Region.Op.DIFFERENCE);
            }
        }
    }

    @Override  // AnimatesBounds
    public boolean setSize(Rect bounds) {
        synchronized (mService.mWindowMap) {
            if (mDisplayContent == null) {
                return false;
            }
        }
        try {
            mService.mActivityManager.resizeStack(mStackId, bounds, false, true, false, -1);
        } catch (RemoteException e) {
        }
        return true;
    }

    public boolean setPinnedStackSize(Rect bounds, Rect tempTaskBounds) {
        synchronized (mService.mWindowMap) {
            if (mDisplayContent == null) {
                return false;
            }
            if (mStackId != PINNED_STACK_ID) {
                Slog.w(TAG_WM, "Attempt to use pinned stack resize animation helper on"
                        + "non pinned stack");
                return false;
            }
        }
        try {
            mService.mActivityManager.resizePinnedStack(bounds, tempTaskBounds);
        } catch (RemoteException e) {
            // I don't believe you.
        }
        return true;
    }

    @Override  // AnimatesBounds
    public void onAnimationStart() {
        synchronized (mService.mWindowMap) {
            mBoundsAnimating = true;
        }
    }

    @Override  // AnimatesBounds
    public void onAnimationEnd() {
        synchronized (mService.mWindowMap) {
            mBoundsAnimating = false;
            mService.requestTraversal();
        }
        if (mStackId == PINNED_STACK_ID) {
            try {
                mService.mActivityManager.notifyPinnedStackAnimationEnded();
            } catch (RemoteException e) {
                // I don't believe you...
            }
        }
    }

    @Override
    public void moveToFullscreen() {
        try {
            mService.mActivityManager.moveTasksToFullscreenStack(mStackId, true);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getFullScreenBounds(Rect bounds) {
        getDisplayContent().getContentRect(bounds);
    }

    public boolean hasMovementAnimations() {
        return StackId.hasMovementAnimations(mStackId);
    }

    public boolean getForceScaleToCrop() {
        return mBoundsAnimating;
    }

    public boolean getBoundsAnimating() {
        return mBoundsAnimating;
    }

    /** Returns true if a removal action is still being deferred. */
    boolean checkCompleteDeferredRemoval() {
        if (isAnimating()) {
            return true;
        }
        if (mDeferRemoval) {
            removeImmediately();
        }

        return super.checkCompleteDeferredRemoval();
    }

    void stepAppWindowsAnimation(long currentTime) {
        super.stepAppWindowsAnimation(currentTime);

        // TODO: Why aren't we just using the loop above for this? mAppAnimator.animating isn't set
        // below but is set in the loop above. See if it really matters...
        final int exitingCount = mExitingAppTokens.size();
        for (int i = 0; i < exitingCount; i++) {
            final AppWindowAnimator appAnimator = mExitingAppTokens.get(i).mAppAnimator;
            appAnimator.wasAnimating = appAnimator.animating;
            if (appAnimator.stepAnimationLocked(currentTime)) {
                mService.mAnimator.setAnimating(true);
                mService.mAnimator.mAppWindowAnimating = true;
            } else if (appAnimator.wasAnimating) {
                // stopped animating, do one more pass through the layout
                appAnimator.mAppToken.setAppLayoutChanges(FINISH_LAYOUT_REDO_WALLPAPER,
                        "exiting appToken " + appAnimator.mAppToken + " done");
                if (DEBUG_ANIM) Slog.v(TAG_WM,
                        "updateWindowsApps...: done animating exiting " + appAnimator.mAppToken);
            }
        }
    }

    void getWindowOnDisplayBeforeToken(DisplayContent dc, WindowToken token,
            DisplayContent.GetWindowOnDisplaySearchResult result) {
        for (int i = mChildren.size() - 1; i >= 0; --i) {
            final Task task = mChildren.get(i);
            task.getWindowOnDisplayBeforeToken(dc, token, result);
            if (result.reachedToken) {
                // We have reach the token we are interested in. End search.
                return;
            }
        }
    }

    void getWindowOnDisplayAfterToken(DisplayContent dc, WindowToken token,
            DisplayContent.GetWindowOnDisplaySearchResult result) {
        for (int i = mChildren.size() - 1; i >= 0; --i) {
            final Task task = mChildren.get(i);
            task.getWindowOnDisplayAfterToken(dc, token, result);
            if (result.foundWindow != null) {
                // We have found a window after the token. End search.
                return;
            }
        }
    }

    @Override
    int getOrientation() {
        return (StackId.canSpecifyOrientation(mStackId))
                ? super.getOrientation() : SCREEN_ORIENTATION_UNSET;
    }
}
