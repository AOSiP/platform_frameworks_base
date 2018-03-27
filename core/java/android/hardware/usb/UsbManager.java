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


package android.hardware.usb;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.RequiresFeature;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.usb.gadget.V1_0.GadgetFunction;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.util.Preconditions;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * This class allows you to access the state of USB and communicate with USB devices.
 * Currently only host mode is supported in the public API.
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about communicating with USB hardware, read the
 * <a href="{@docRoot}guide/topics/connectivity/usb/index.html">USB developer guide</a>.</p>
 * </div>
 */
@SystemService(Context.USB_SERVICE)
public class UsbManager {
    private static final String TAG = "UsbManager";

   /**
     * Broadcast Action:  A sticky broadcast for USB state change events when in device mode.
     *
     * This is a sticky broadcast for clients that includes USB connected/disconnected state,
     * <ul>
     * <li> {@link #USB_CONNECTED} boolean indicating whether USB is connected or disconnected.
     * <li> {@link #USB_HOST_CONNECTED} boolean indicating whether USB is connected or
     *     disconnected as host.
     * <li> {@link #USB_CONFIGURED} boolean indicating whether USB is configured.
     * currently zero if not configured, one for configured.
     * <li> {@link #USB_FUNCTION_ADB} boolean extra indicating whether the
     * adb function is enabled
     * <li> {@link #USB_FUNCTION_RNDIS} boolean extra indicating whether the
     * RNDIS ethernet function is enabled
     * <li> {@link #USB_FUNCTION_MTP} boolean extra indicating whether the
     * MTP function is enabled
     * <li> {@link #USB_FUNCTION_PTP} boolean extra indicating whether the
     * PTP function is enabled
     * <li> {@link #USB_FUNCTION_ACCESSORY} boolean extra indicating whether the
     * accessory function is enabled
     * <li> {@link #USB_FUNCTION_AUDIO_SOURCE} boolean extra indicating whether the
     * audio source function is enabled
     * <li> {@link #USB_FUNCTION_MIDI} boolean extra indicating whether the
     * MIDI function is enabled
     * </ul>
     * If the sticky intent has not been found, that indicates USB is disconnected,
     * USB is not configued, MTP function is enabled, and all the other functions are disabled.
     *
     * {@hide}
     */
    public static final String ACTION_USB_STATE =
            "android.hardware.usb.action.USB_STATE";

    /**
     * Broadcast Action: A broadcast for USB port changes.
     *
     * This intent is sent when a USB port is added, removed, or changes state.
     * <ul>
     * <li> {@link #EXTRA_PORT} containing the {@link android.hardware.usb.UsbPort}
     * for the port.
     * <li> {@link #EXTRA_PORT_STATUS} containing the {@link android.hardware.usb.UsbPortStatus}
     * for the port, or null if the port has been removed
     * </ul>
     *
     * @hide
     */
    public static final String ACTION_USB_PORT_CHANGED =
            "android.hardware.usb.action.USB_PORT_CHANGED";

   /**
     * Activity intent sent when user attaches a USB device.
     *
     * This intent is sent when a USB device is attached to the USB bus when in host mode.
     * <ul>
     * <li> {@link #EXTRA_DEVICE} containing the {@link android.hardware.usb.UsbDevice}
     * for the attached device
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_USB_DEVICE_ATTACHED =
            "android.hardware.usb.action.USB_DEVICE_ATTACHED";

   /**
     * Broadcast Action:  A broadcast for USB device detached event.
     *
     * This intent is sent when a USB device is detached from the USB bus when in host mode.
     * <ul>
     * <li> {@link #EXTRA_DEVICE} containing the {@link android.hardware.usb.UsbDevice}
     * for the detached device
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_USB_DEVICE_DETACHED =
            "android.hardware.usb.action.USB_DEVICE_DETACHED";

   /**
     * Activity intent sent when user attaches a USB accessory.
     *
     * <ul>
     * <li> {@link #EXTRA_ACCESSORY} containing the {@link android.hardware.usb.UsbAccessory}
     * for the attached accessory
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_USB_ACCESSORY_ATTACHED =
            "android.hardware.usb.action.USB_ACCESSORY_ATTACHED";

   /**
     * Broadcast Action:  A broadcast for USB accessory detached event.
     *
     * This intent is sent when a USB accessory is detached.
     * <ul>
     * <li> {@link #EXTRA_ACCESSORY} containing the {@link UsbAccessory}
     * for the attached accessory that was detached
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_USB_ACCESSORY_DETACHED =
            "android.hardware.usb.action.USB_ACCESSORY_DETACHED";

    /**
     * Boolean extra indicating whether USB is connected or disconnected.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast.
     *
     * {@hide}
     */
    public static final String USB_CONNECTED = "connected";

    /**
     * Boolean extra indicating whether USB is connected or disconnected as host.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast.
     *
     * {@hide}
     */
    public static final String USB_HOST_CONNECTED = "host_connected";

    /**
     * Boolean extra indicating whether USB is configured.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast.
     *
     * {@hide}
     */
    public static final String USB_CONFIGURED = "configured";

    /**
     * Boolean extra indicating whether confidential user data, such as photos, should be
     * made available on the USB connection. This variable will only be set when the user
     * has explicitly asked for this data to be unlocked.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast.
     *
     * {@hide}
     */
    public static final String USB_DATA_UNLOCKED = "unlocked";

    /**
     * A placeholder indicating that no USB function is being specified.
     * Used for compatibility with old init scripts to indicate no functions vs. charging function.
     *
     * {@hide}
     */
    public static final String USB_FUNCTION_NONE = "none";

    /**
     * Name of the adb USB function.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast
     *
     * {@hide}
     */
    public static final String USB_FUNCTION_ADB = "adb";

    /**
     * Name of the RNDIS ethernet USB function.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast
     *
     * {@hide}
     */
    public static final String USB_FUNCTION_RNDIS = "rndis";

    /**
     * Name of the MTP USB function.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast
     *
     * {@hide}
     */
    public static final String USB_FUNCTION_MTP = "mtp";

    /**
     * Name of the PTP USB function.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast
     *
     * {@hide}
     */
    public static final String USB_FUNCTION_PTP = "ptp";

    /**
     * Name of the audio source USB function.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast
     *
     * {@hide}
     */
    public static final String USB_FUNCTION_AUDIO_SOURCE = "audio_source";

    /**
     * Name of the MIDI USB function.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast
     *
     * {@hide}
     */
    public static final String USB_FUNCTION_MIDI = "midi";

    /**
     * Name of the Accessory USB function.
     * Used in extras for the {@link #ACTION_USB_STATE} broadcast
     *
     * {@hide}
     */
    public static final String USB_FUNCTION_ACCESSORY = "accessory";

    /**
     * Name of extra for {@link #ACTION_USB_PORT_CHANGED}
     * containing the {@link UsbPort} object for the port.
     *
     * @hide
     */
    public static final String EXTRA_PORT = "port";

    /**
     * Name of extra for {@link #ACTION_USB_PORT_CHANGED}
     * containing the {@link UsbPortStatus} object for the port, or null if the port
     * was removed.
     *
     * @hide
     */
    public static final String EXTRA_PORT_STATUS = "portStatus";

    /**
     * Name of extra for {@link #ACTION_USB_DEVICE_ATTACHED} and
     * {@link #ACTION_USB_DEVICE_DETACHED} broadcasts
     * containing the {@link UsbDevice} object for the device.
     */
    public static final String EXTRA_DEVICE = "device";

    /**
     * Name of extra for {@link #ACTION_USB_ACCESSORY_ATTACHED} and
     * {@link #ACTION_USB_ACCESSORY_DETACHED} broadcasts
     * containing the {@link UsbAccessory} object for the accessory.
     */
    public static final String EXTRA_ACCESSORY = "accessory";

    /**
     * Name of extra added to the {@link android.app.PendingIntent}
     * passed into {@link #requestPermission(UsbDevice, PendingIntent)}
     * or {@link #requestPermission(UsbAccessory, PendingIntent)}
     * containing a boolean value indicating whether the user granted permission or not.
     */
    public static final String EXTRA_PERMISSION_GRANTED = "permission";

    /**
     * Code for the charging usb function. Passed into {@link #setCurrentFunctions(long)}
     * {@hide}
     */
    public static final long FUNCTION_NONE = 0;

    /**
     * Code for the mtp usb function. Passed as a mask into {@link #setCurrentFunctions(long)}
     * {@hide}
     */
    public static final long FUNCTION_MTP = GadgetFunction.MTP;

    /**
     * Code for the ptp usb function. Passed as a mask into {@link #setCurrentFunctions(long)}
     * {@hide}
     */
    public static final long FUNCTION_PTP = GadgetFunction.PTP;

    /**
     * Code for the rndis usb function. Passed as a mask into {@link #setCurrentFunctions(long)}
     * {@hide}
     */
    public static final long FUNCTION_RNDIS = GadgetFunction.RNDIS;

    /**
     * Code for the midi usb function. Passed as a mask into {@link #setCurrentFunctions(long)}
     * {@hide}
     */
    public static final long FUNCTION_MIDI = GadgetFunction.MIDI;

    /**
     * Code for the accessory usb function.
     * {@hide}
     */
    public static final long FUNCTION_ACCESSORY = GadgetFunction.ACCESSORY;

    /**
     * Code for the audio source usb function.
     * {@hide}
     */
    public static final long FUNCTION_AUDIO_SOURCE = GadgetFunction.AUDIO_SOURCE;

    /**
     * Code for the adb usb function.
     * {@hide}
     */
    public static final long FUNCTION_ADB = GadgetFunction.ADB;

    private static final long SETTABLE_FUNCTIONS = FUNCTION_MTP | FUNCTION_PTP | FUNCTION_RNDIS
            | FUNCTION_MIDI;

    private static final Map<String, Long> FUNCTION_NAME_TO_CODE = new HashMap<>();

    static {
        FUNCTION_NAME_TO_CODE.put(UsbManager.USB_FUNCTION_MTP, FUNCTION_MTP);
        FUNCTION_NAME_TO_CODE.put(UsbManager.USB_FUNCTION_PTP, FUNCTION_PTP);
        FUNCTION_NAME_TO_CODE.put(UsbManager.USB_FUNCTION_RNDIS, FUNCTION_RNDIS);
        FUNCTION_NAME_TO_CODE.put(UsbManager.USB_FUNCTION_MIDI, FUNCTION_MIDI);
        FUNCTION_NAME_TO_CODE.put(UsbManager.USB_FUNCTION_ACCESSORY, FUNCTION_ACCESSORY);
        FUNCTION_NAME_TO_CODE.put(UsbManager.USB_FUNCTION_AUDIO_SOURCE, FUNCTION_AUDIO_SOURCE);
        FUNCTION_NAME_TO_CODE.put(UsbManager.USB_FUNCTION_ADB, FUNCTION_ADB);
    }

    private final Context mContext;
    private final IUsbManager mService;

    /**
     * {@hide}
     */
    public UsbManager(Context context, IUsbManager service) {
        mContext = context;
        mService = service;
    }

    /**
     * Returns a HashMap containing all USB devices currently attached.
     * USB device name is the key for the returned HashMap.
     * The result will be empty if no devices are attached, or if
     * USB host mode is inactive or unsupported.
     *
     * @return HashMap containing all connected USB devices.
     */
    @RequiresFeature(PackageManager.FEATURE_USB_HOST)
    public HashMap<String,UsbDevice> getDeviceList() {
        HashMap<String,UsbDevice> result = new HashMap<String,UsbDevice>();
        if (mService == null) {
            return result;
        }
        Bundle bundle = new Bundle();
        try {
            mService.getDeviceList(bundle);
            for (String name : bundle.keySet()) {
                result.put(name, (UsbDevice)bundle.get(name));
            }
            return result;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Opens the device so it can be used to send and receive
     * data using {@link android.hardware.usb.UsbRequest}.
     *
     * @param device the device to open
     * @return a {@link UsbDeviceConnection}, or {@code null} if open failed
     */
    @RequiresFeature(PackageManager.FEATURE_USB_HOST)
    public UsbDeviceConnection openDevice(UsbDevice device) {
        try {
            String deviceName = device.getDeviceName();
            ParcelFileDescriptor pfd = mService.openDevice(deviceName, mContext.getPackageName());
            if (pfd != null) {
                UsbDeviceConnection connection = new UsbDeviceConnection(device);
                boolean result = connection.open(deviceName, pfd, mContext);
                pfd.close();
                if (result) {
                    return connection;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "exception in UsbManager.openDevice", e);
        }
        return null;
    }

    /**
     * Returns a list of currently attached USB accessories.
     * (in the current implementation there can be at most one)
     *
     * @return list of USB accessories, or null if none are attached.
     */
    @RequiresFeature(PackageManager.FEATURE_USB_ACCESSORY)
    public UsbAccessory[] getAccessoryList() {
        if (mService == null) {
            return null;
        }
        try {
            UsbAccessory accessory = mService.getCurrentAccessory();
            if (accessory == null) {
                return null;
            } else {
                return new UsbAccessory[] { accessory };
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Opens a file descriptor for reading and writing data to the USB accessory.
     *
     * <p>If data is read from the {@link java.io.InputStream} created from this file descriptor all
     * data of a USB transfer should be read at once. If only a partial request is read the rest of
     * the transfer is dropped.
     *
     * @param accessory the USB accessory to open
     * @return file descriptor, or null if the accessory could not be opened.
     */
    @RequiresFeature(PackageManager.FEATURE_USB_ACCESSORY)
    public ParcelFileDescriptor openAccessory(UsbAccessory accessory) {
        try {
            return mService.openAccessory(accessory);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the functionfs control file descriptor for the given function, with
     * the usb descriptors and strings already written. The file descriptor is used
     * by the function implementation to handle events and control requests.
     *
     * @param function to get control fd for. Currently {@link #FUNCTION_MTP} and
     * {@link #FUNCTION_PTP} are supported.
     * @return A ParcelFileDescriptor holding the valid fd, or null if the fd was not found.
     *
     * {@hide}
     */
    public ParcelFileDescriptor getControlFd(long function) {
        try {
            return mService.getControlFd(function);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns true if the caller has permission to access the device.
     * Permission might have been granted temporarily via
     * {@link #requestPermission(UsbDevice, PendingIntent)} or
     * by the user choosing the caller as the default application for the device.
     * Permission for USB devices of class {@link UsbConstants#USB_CLASS_VIDEO} for clients that
     * target SDK {@link android.os.Build.VERSION_CODES#P} and above can be granted only if they
     * have additionally the {@link android.Manifest.permission#CAMERA} permission.
     *
     * @param device to check permissions for
     * @return true if caller has permission
     */
    @RequiresFeature(PackageManager.FEATURE_USB_HOST)
    public boolean hasPermission(UsbDevice device) {
        if (mService == null) {
            return false;
        }
        try {
            return mService.hasDevicePermission(device, mContext.getPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns true if the caller has permission to access the accessory.
     * Permission might have been granted temporarily via
     * {@link #requestPermission(UsbAccessory, PendingIntent)} or
     * by the user choosing the caller as the default application for the accessory.
     *
     * @param accessory to check permissions for
     * @return true if caller has permission
     */
    @RequiresFeature(PackageManager.FEATURE_USB_ACCESSORY)
    public boolean hasPermission(UsbAccessory accessory) {
        if (mService == null) {
            return false;
        }
        try {
            return mService.hasAccessoryPermission(accessory);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Requests temporary permission for the given package to access the device.
     * This may result in a system dialog being displayed to the user
     * if permission had not already been granted.
     * Success or failure is returned via the {@link android.app.PendingIntent} pi.
     * If successful, this grants the caller permission to access the device only
     * until the device is disconnected.
     *
     * The following extras will be added to pi:
     * <ul>
     * <li> {@link #EXTRA_DEVICE} containing the device passed into this call
     * <li> {@link #EXTRA_PERMISSION_GRANTED} containing boolean indicating whether
     * permission was granted by the user
     * </ul>
     *
     * Permission for USB devices of class {@link UsbConstants#USB_CLASS_VIDEO} for clients that
     * target SDK {@link android.os.Build.VERSION_CODES#P} and above can be granted only if they
     * have additionally the {@link android.Manifest.permission#CAMERA} permission.
     *
     * @param device to request permissions for
     * @param pi PendingIntent for returning result
     */
    @RequiresFeature(PackageManager.FEATURE_USB_HOST)
    public void requestPermission(UsbDevice device, PendingIntent pi) {
        try {
            mService.requestDevicePermission(device, mContext.getPackageName(), pi);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Requests temporary permission for the given package to access the accessory.
     * This may result in a system dialog being displayed to the user
     * if permission had not already been granted.
     * Success or failure is returned via the {@link android.app.PendingIntent} pi.
     * If successful, this grants the caller permission to access the accessory only
     * until the device is disconnected.
     *
     * The following extras will be added to pi:
     * <ul>
     * <li> {@link #EXTRA_ACCESSORY} containing the accessory passed into this call
     * <li> {@link #EXTRA_PERMISSION_GRANTED} containing boolean indicating whether
     * permission was granted by the user
     * </ul>
     *
     * @param accessory to request permissions for
     * @param pi PendingIntent for returning result
     */
    @RequiresFeature(PackageManager.FEATURE_USB_ACCESSORY)
    public void requestPermission(UsbAccessory accessory, PendingIntent pi) {
        try {
            mService.requestAccessoryPermission(accessory, mContext.getPackageName(), pi);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Grants permission for USB device without showing system dialog.
     * Only system components can call this function.
     * @param device to request permissions for
     *
     * {@hide}
     */
    public void grantPermission(UsbDevice device) {
        grantPermission(device, Process.myUid());
    }

    /**
     * Grants permission for USB device to given uid without showing system dialog.
     * Only system components can call this function.
     * @param device to request permissions for
     * @uid uid to give permission
     *
     * {@hide}
     */
    public void grantPermission(UsbDevice device, int uid) {
        try {
            mService.grantDevicePermission(device, uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Grants permission to specified package for USB device without showing system dialog.
     * Only system components can call this function, as it requires the MANAGE_USB permission.
     * @param device to request permissions for
     * @param packageName of package to grant permissions
     *
     * {@hide}
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MANAGE_USB)
    public void grantPermission(UsbDevice device, String packageName) {
        try {
            int uid = mContext.getPackageManager()
                .getPackageUidAsUser(packageName, mContext.getUserId());
            grantPermission(device, uid);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Package " + packageName + " not found.", e);
        }
    }

    /**
     * Returns true if the specified USB function is currently enabled when in device mode.
     * <p>
     * USB functions represent interfaces which are published to the host to access
     * services offered by the device.
     * </p>
     *
     * @deprecated use getCurrentFunctions() instead.
     * @param function name of the USB function
     * @return true if the USB function is enabled
     *
     * {@hide}
     */
    @Deprecated
    public boolean isFunctionEnabled(String function) {
        try {
            return mService.isFunctionEnabled(function);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the current USB functions when in device mode.
     * <p>
     * USB functions represent interfaces which are published to the host to access
     * services offered by the device.
     * </p><p>
     * This method is intended to select among primary USB functions.  The system may
     * automatically activate additional functions such as {@link #USB_FUNCTION_ADB}
     * or {@link #USB_FUNCTION_ACCESSORY} based on other settings and states.
     * </p><p>
     * An argument of 0 indicates that the device is charging, and can pick any
     * appropriate function for that purpose.
     * </p><p>
     * Note: This function is asynchronous and may fail silently without applying
     * the requested changes.
     * </p>
     *
     * @param functions the USB function(s) to set, as a bitwise mask.
     *                  Must satisfy {@link UsbManager#areSettableFunctions}
     *
     * {@hide}
     */
    public void setCurrentFunctions(long functions) {
        try {
            mService.setCurrentFunctions(functions);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the current USB functions when in device mode.
     *
     * @deprecated use setCurrentFunctions(long) instead.
     * @param functions the USB function(s) to set.
     * @param usbDataUnlocked unused

     * {@hide}
     */
    @Deprecated
    public void setCurrentFunction(String functions, boolean usbDataUnlocked) {
        try {
            mService.setCurrentFunction(functions, usbDataUnlocked);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the current USB functions in device mode.
     * <p>
     * This function returns the state of primary USB functions and can return a
     * mask containing any usb function(s) except for ADB.
     * </p>
     *
     * @return The currently enabled functions, in a bitwise mask.
     * A zero mask indicates that the current function is the charging function.
     *
     * {@hide}
     */
    public long getCurrentFunctions() {
        try {
            return mService.getCurrentFunctions();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the screen unlocked functions, which are persisted and set as the current functions
     * whenever the screen is unlocked.
     * <p>
     * A zero mask has the effect of switching off this feature, so functions
     * no longer change on screen unlock.
     * </p><p>
     * Note: When the screen is on, this method will apply given functions as current functions,
     * which is asynchronous and may fail silently without applying the requested changes.
     * </p>
     *
     * @param functions functions to set, in a bitwise mask.
     *                  Must satisfy {@link UsbManager#areSettableFunctions}
     *
     * {@hide}
     */
    public void setScreenUnlockedFunctions(long functions) {
        try {
            mService.setScreenUnlockedFunctions(functions);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the current screen unlocked functions.
     *
     * @return The currently set screen enabled functions.
     * A zero mask indicates that the screen unlocked functions feature is not enabled.
     *
     * {@hide}
     */
    public long getScreenUnlockedFunctions() {
        try {
            return mService.getScreenUnlockedFunctions();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a list of physical USB ports on the device.
     * <p>
     * This list is guaranteed to contain all dual-role USB Type C ports but it might
     * be missing other ports depending on whether the kernel USB drivers have been
     * updated to publish all of the device's ports through the new "dual_role_usb"
     * device class (which supports all types of ports despite its name).
     * </p>
     *
     * @return The list of USB ports, or null if none.
     *
     * @hide
     */
    public UsbPort[] getPorts() {
        if (mService == null) {
            return null;
        }
        try {
            return mService.getPorts();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the status of the specified USB port.
     *
     * @param port The port to query.
     * @return The status of the specified USB port, or null if unknown.
     *
     * @hide
     */
    public UsbPortStatus getPortStatus(UsbPort port) {
        Preconditions.checkNotNull(port, "port must not be null");

        try {
            return mService.getPortStatus(port.getId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the desired role combination of the port.
     * <p>
     * The supported role combinations depend on what is connected to the port and may be
     * determined by consulting
     * {@link UsbPortStatus#isRoleCombinationSupported UsbPortStatus.isRoleCombinationSupported}.
     * </p><p>
     * Note: This function is asynchronous and may fail silently without applying
     * the requested changes.  If this function does cause a status change to occur then
     * a {@link #ACTION_USB_PORT_CHANGED} broadcast will be sent.
     * </p>
     *
     * @param powerRole The desired power role: {@link UsbPort#POWER_ROLE_SOURCE}
     * or {@link UsbPort#POWER_ROLE_SINK}, or 0 if no power role.
     * @param dataRole The desired data role: {@link UsbPort#DATA_ROLE_HOST}
     * or {@link UsbPort#DATA_ROLE_DEVICE}, or 0 if no data role.
     *
     * @hide
     */
    public void setPortRoles(UsbPort port, int powerRole, int dataRole) {
        Preconditions.checkNotNull(port, "port must not be null");
        UsbPort.checkRoles(powerRole, dataRole);

        Log.d(TAG, "setPortRoles Package:" + mContext.getPackageName());
        try {
            mService.setPortRoles(port.getId(), powerRole, dataRole);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the component that will handle USB device connection.
     * <p>
     * Setting component allows to specify external USB host manager to handle use cases, where
     * selection dialog for an activity that will handle USB device is undesirable.
     * Only system components can call this function, as it requires the MANAGE_USB permission.
     *
     * @param usbDeviceConnectionHandler The component to handle usb connections,
     * {@code null} to unset.
     *
     * {@hide}
     */
    public void setUsbDeviceConnectionHandler(@Nullable ComponentName usbDeviceConnectionHandler) {
        try {
            mService.setUsbDeviceConnectionHandler(usbDeviceConnectionHandler);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether the given functions are valid inputs to UsbManager.
     * Currently the empty functions or any of MTP, PTP, RNDIS, MIDI are accepted.
     *
     * @return Whether the mask is settable.
     *
     * {@hide}
     */
    public static boolean areSettableFunctions(long functions) {
        return functions == FUNCTION_NONE
                || ((~SETTABLE_FUNCTIONS & functions) == 0 && Long.bitCount(functions) == 1);
    }

    /**
     * Converts the given function mask to string. Maintains ordering with respect to init scripts.
     *
     * @return String representation of given mask
     *
     * {@hide}
     */
    public static String usbFunctionsToString(long functions) {
        StringJoiner joiner = new StringJoiner(",");
        if ((functions & FUNCTION_MTP) != 0) {
            joiner.add(UsbManager.USB_FUNCTION_MTP);
        }
        if ((functions & FUNCTION_PTP) != 0) {
            joiner.add(UsbManager.USB_FUNCTION_PTP);
        }
        if ((functions & FUNCTION_RNDIS) != 0) {
            joiner.add(UsbManager.USB_FUNCTION_RNDIS);
        }
        if ((functions & FUNCTION_MIDI) != 0) {
            joiner.add(UsbManager.USB_FUNCTION_MIDI);
        }
        if ((functions & FUNCTION_ACCESSORY) != 0) {
            joiner.add(UsbManager.USB_FUNCTION_ACCESSORY);
        }
        if ((functions & FUNCTION_AUDIO_SOURCE) != 0) {
            joiner.add(UsbManager.USB_FUNCTION_AUDIO_SOURCE);
        }
        if ((functions & FUNCTION_ADB) != 0) {
            joiner.add(UsbManager.USB_FUNCTION_ADB);
        }
        return joiner.toString();
    }

    /**
     * Parses a string of usb functions that are comma separated.
     *
     * @return A mask of all valid functions in the string
     *
     * {@hide}
     */
    public static long usbFunctionsFromString(String functions) {
        if (functions == null || functions.equals(USB_FUNCTION_NONE)) {
            return FUNCTION_NONE;
        }
        long ret = 0;
        for (String function : functions.split(",")) {
            if (FUNCTION_NAME_TO_CODE.containsKey(function)) {
                ret |= FUNCTION_NAME_TO_CODE.get(function);
            } else if (function.length() > 0) {
                throw new IllegalArgumentException("Invalid usb function " + functions);
            }
        }
        return ret;
    }
}
