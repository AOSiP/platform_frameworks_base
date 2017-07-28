/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.substratum;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageInstallObserver2;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.substratum.ISubstratumService;
import android.graphics.Typeface;
import android.media.RingtoneManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import com.android.server.SystemService;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class SubstratumService extends SystemService {

    private static final String TAG = "SubstratumService";
    private static final String SUBSTRATUM_PACKAGE = "projekt.substratum";
    private static final boolean DEBUG = true;
    private static IOverlayManager mOM;
    private static IPackageManager mPM;
    private static boolean isWaiting = false;
    private static String installedPackageName;
    private static List<Sound> SOUNDS = Arrays.asList(
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "Effect_Tick",
                "Effect_Tick", RingtoneManager.TYPE_RINGTONE),
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "lock_sound",
                "Lock"),
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "unlock_sound",
                "Unlock"),
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/",
                "low_battery_sound", "LowBattery"),
        new Sound(IOUtils.SYSTEM_THEME_ALARM_PATH, "/SoundsCache/alarms/", "alarm", "alarm",
                RingtoneManager.TYPE_ALARM),
        new Sound(IOUtils.SYSTEM_THEME_NOTIFICATION_PATH, "/SoundsCache/notifications/",
                "notification", "notification", RingtoneManager.TYPE_NOTIFICATION),
        new Sound(IOUtils.SYSTEM_THEME_RINGTONE_PATH, "/SoundsCache/ringtones/", "ringtone",
                "ringtone", RingtoneManager.TYPE_RINGTONE)
    );
    private final Object mLock = new Object();
    private Context mContext;

    public SubstratumService(@NonNull final Context context) {
        super(context);
        mContext = context;
        IOUtils.createThemeDirIfNotExists();
        publishBinderService("substratum", mService);
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onSwitchUser(final int newUserId) {
    }

    private final IBinder mService = new ISubstratumService.Stub() {
        @Override
        public void installOverlay(List<String> paths) {
            final long ident = Binder.clearCallingIdentity();
            final int packageVerifierEnable = Settings.Global.getInt(
                    mContext.getContentResolver(),
                    Settings.Global.PACKAGE_VERIFIER_ENABLE, 1);
            try {
                synchronized (mLock) {
                    PackageInstallObserver installObserver = new PackageInstallObserver();
                    PackageDeleteObserver deleteObserver = new PackageDeleteObserver();
                    for (String path : paths) {
                        installedPackageName = null;
                        File apkFile = new File(path);
                        if (apkFile.exists()) {
                            log("Installer - installing package from path \'" + path + "\'");
                            isWaiting = true;
                            Settings.Global.putInt(mContext.getContentResolver(),
                                    Settings.Global.PACKAGE_VERIFIER_ENABLE, 0);
                            getPM().installPackageAsUser(
                                    path,
                                    installObserver,
                                    PackageManager.INSTALL_REPLACE_EXISTING,
                                    null,
                                    UserHandle.USER_SYSTEM);
                            while (isWaiting) {
                                Thread.sleep(500);
                            }

                            if (installedPackageName != null) {
                                PackageInfo pi = getPM().getPackageInfo(installedPackageName,
                                        0, UserHandle.USER_SYSTEM);
                                if ((pi.applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0 ||
                                        pi.overlayTarget == null) {
                                    isWaiting = true;
                                    int versionCode = getPM().getPackageInfo(installedPackageName, 0, UserHandle.USER_SYSTEM)
                                            .versionCode;
                                    getPM().deletePackageAsUser(
                                            installedPackageName,
                                            versionCode,
                                            deleteObserver,
                                            0,
                                            UserHandle.USER_SYSTEM);
                                    while (isWaiting) {
                                        Thread.sleep(500);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "", e);
            } finally {
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.PACKAGE_VERIFIER_ENABLE, packageVerifierEnable);
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void uninstallOverlay(List<String> packages, boolean restartUi) {
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    PackageDeleteObserver observer = new PackageDeleteObserver();
                    for (String p : packages) {
                        if (isOverlayEnabled(p)) {
                            log("Remover - disabling overlay for \'" + p + "\'...");
                            switchOverlayState(p, false);
                        }

                        log("Remover - uninstalling \'" + p + "\'...");
                        isWaiting = true;
                        int versionCode = getPM().getPackageInfo(installedPackageName, 0, UserHandle.USER_SYSTEM)
                                .versionCode;
                        getPM().deletePackageAsUser(
                                p,
                                versionCode,
                                observer,
                                0,
                                UserHandle.USER_SYSTEM);
                        while (isWaiting) {
                            Thread.sleep(500);
                        }
                    }
                    if (restartUi) {
                        restartUi();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "", e);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void switchOverlay(List<String> packages, boolean enable, boolean restartUi) {
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    for (String p : packages) {
                        log(enable ? "Enabling" : "Disabling" + " overlay " + p);
                        switchOverlayState(p, enable);
                    }
                    if (restartUi) {
                        restartUi();
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void setPriority(List<String> packages, boolean restartUi) {
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    log("PriorityJob - processing priority changes...");
                    for (int i = 0; i < packages.size() - 1; i++) {
                        String parentName = packages.get(i);
                        String packageName = packages.get(i + 1);

                        getOM().setPriority(packageName, parentName,
                                UserHandle.USER_SYSTEM);
                    }
                    if (restartUi) {
                        restartUi();
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void restartSystemUI() {
            final long ident = Binder.clearCallingIdentity();
            try {
                log("Restarting SystemUI...");
                restartUi();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void copy(String source, String destination) {
            final long ident = Binder.clearCallingIdentity();
            try {
                log("CopyJob - copying \'" + source + "\' to \'" + destination + "\'...");
                File sourceFile = new File(source);
                if (sourceFile.exists()) {
                    if (sourceFile.isFile()) {
                        IOUtils.bufferedCopy(source, destination);
                    } else {
                        IOUtils.copyFolder(source, destination);
                    }
                } else {
                    Log.e(TAG, "CopyJob - \'" + source + "\' does not exist, aborting...");
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void move(String source, String destination) {
            final long ident = Binder.clearCallingIdentity();
            try {
                log("MoveJob - moving \'" + source + "\' to \'" + destination + "\'...");
                File sourceFile = new File(source);
                if (sourceFile.exists()) {
                    if (sourceFile.isFile()) {
                        IOUtils.bufferedCopy(source, destination);
                    } else {
                        IOUtils.copyFolder(source, destination);
                    }
                    IOUtils.deleteRecursive(sourceFile);
                } else {
                    Log.e(TAG, "MoveJob - \'" + source + "\' does not exist, aborting...");
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void mkdir(String destination) {
            final long ident = Binder.clearCallingIdentity();
            try {
                log("MkdirJob - creating \'" + destination + "\'...");
                IOUtils.createDirIfNotExists(destination);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void deleteDirectory(String directory, boolean withParent) {
            final long ident = Binder.clearCallingIdentity();
            try {
                if (withParent) {
                    delete(directory);
                } else {
                    for (File child : new File(directory).listFiles()) {
                        delete(child.getAbsolutePath());
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void applyBootanimation(String name) {
            final long ident = Binder.clearCallingIdentity();
            try {
                if (name == null) {
                    log("Restoring system boot animation...");
                    clearBootAnimation();
                } else {
                    log("Configuring themed boot animation...");
                    copyBootAnimation(name);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void applyFonts(String pid, String fileName) {
            final long ident = Binder.clearCallingIdentity();
            try {
                if (pid == null) {
                    log("Restoring system font...");
                    clearFonts();
                } else {
                    log("Configuring theme font...");
                    copyFonts(pid, fileName);
                }
                restartUi();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void applySounds(String pid, String fileName) {
            final long ident = Binder.clearCallingIdentity();
            try {
                if (pid == null) {
                    log("Restoring system sounds...");
                    clearSounds();
                } else {
                    log("Configuring theme sounds...");
                    applyThemedSounds(pid, fileName);
                }
                restartUi();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void applyProfile(List<String> enable, List<String> disable, String name,
                boolean restartUi) {
            final long ident = Binder.clearCallingIdentity();
            try {
                log("ProfileJob - Applying profile: " + name);
                boolean mRestartUi = restartUi;

                // Clear system theme folder content
                File themeDir = new File(IOUtils.SYSTEM_THEME_PATH);
                for (File f : themeDir.listFiles()) {
                    IOUtils.deleteRecursive(f);
                }

                // Process theme folder
                File profileDir = new File(Environment.getExternalStorageDirectory()
                        .getAbsolutePath() + "/substratum/profiles/" +
                        name + "/theme");

                if (profileDir.exists()) {
                    File profileFonts = new File(profileDir, "fonts");
                    if (profileFonts.exists()) {
                        IOUtils.copyFolder(profileFonts, new File(IOUtils.SYSTEM_THEME_FONT_PATH));
                        refreshFonts();
                        mRestartUi = true;
                    } else {
                        clearFonts();
                    }

                    File profileSounds = new File(profileDir, "audio");
                    if (profileSounds.exists()) {
                        IOUtils.copyFolder(profileSounds, new File(IOUtils.SYSTEM_THEME_AUDIO_PATH));
                        refreshSounds();
                        mRestartUi = true;
                    } else {
                        clearSounds();
                    }
                }

                // Disable all overlays installed
                for (String overlay : disable) {
                    switchOverlayState(overlay, false);
                }

                // Enable provided overlays
                for (String overlay : enable) {
                    switchOverlayState(overlay, true);
                }

                // Restart SystemUI when needed
                if (mRestartUi) {
                    restartUi();
                }

                log("ProfileJob - " + name + " successfully applied.");
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    };

    private static IOverlayManager getOM() {
        if (mOM == null) {
            mOM = IOverlayManager.Stub.asInterface(
                    ServiceManager.getService(Context.OVERLAY_SERVICE));
        }
        return mOM;
    }

    private static IPackageManager getPM() {
        if (mPM == null) {
            mPM = IPackageManager.Stub.asInterface(
                    ServiceManager.getService("package"));
        }
        return mPM;
    }

    private Context getAppContext(String packageName) {
        Context ctx = null;
        try {
            ctx = mContext.createPackageContext(packageName,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "", e);
        }
        return ctx;
    }

    private Context getSubsContext() {
        return getAppContext(SUBSTRATUM_PACKAGE);
    }

    private void switchOverlayState(String packageName, boolean enable) {
        try {
            getOM().setEnabled(packageName, enable, UserHandle.USER_SYSTEM);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    private boolean isOverlayEnabled(String packageName) {
        boolean enabled = false;
        try {
            OverlayInfo info = getOM().getOverlayInfo(packageName, UserHandle.USER_SYSTEM);
            if (info != null) {
                enabled = info.isEnabled();
            } else {
                Log.e(TAG, "OverlayInfo is null.");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
        return enabled;
    }

    private void restartUi() {
        try {
            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            Class ActivityManagerNative = Class.forName("android.app.ActivityManagerNative");
            Method getDefault = ActivityManagerNative.getDeclaredMethod("getDefault", null);
            Object amn = getDefault.invoke(null, null);
            Method killApplicationProcess = amn.getClass().getDeclaredMethod
                    ("killApplicationProcess", String.class, int.class);

            mContext.stopService(new Intent().setComponent(new ComponentName(
                    "com.android.systemui", "com.android.systemui.SystemUIService")));
            am.killBackgroundProcesses("com.android.systemui");

            for (ActivityManager.RunningAppProcessInfo app : am.getRunningAppProcesses()) {
                if ("com.android.systemui".equals(app.processName)) {
                    killApplicationProcess.invoke(amn, app.processName, app.uid);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private void delete(String mFileOrDirectory) {
        log("DeleteJob - deleting \'" + mFileOrDirectory + "\'...");

        File file = new File(mFileOrDirectory);
        if (file.exists()) {
            IOUtils.deleteRecursive(file);
        } else {
            Log.e(TAG, "DeleteJob - \'" + mFileOrDirectory + "\' is already deleted.");
        }
    }

    private void copyBootAnimation(String fileName) {
        try {
            clearBootAnimation();

            File source = new File(fileName);
            File dest = new File(IOUtils.SYSTEM_THEME_BOOTANIMATION_PATH);

            IOUtils.bufferedCopy(source, dest);

            boolean deleted = source.delete();
            if (!deleted) {
                Log.e(TAG, "Could not delete source file...");
            }

            IOUtils.setPermissions(dest,
                    FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IROTH);
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private void clearBootAnimation() {
        try {
            File f = new File(IOUtils.SYSTEM_THEME_BOOTANIMATION_PATH);
            if (f.exists()) {
                boolean deleted = f.delete();
                if (!deleted) {
                    Log.e(TAG, "Could not delete themed boot animation...");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private void copyFonts(String pid, String zipFileName) {
        // Prepare local cache dir for font package assembly
        log("Copy Fonts - Package ID = " + pid + " filename = " + zipFileName);

        File cacheDir = new File(IOUtils.SYSTEM_THEME_CACHE_PATH, "FontCache");
        if (cacheDir.exists()) {
            IOUtils.deleteRecursive(cacheDir);
        }

        boolean created = cacheDir.mkdirs();
        if (!created) {
            Log.e(TAG, "Could not create cache directory...");
        }

        // Copy system fonts into our cache dir
        IOUtils.copyFolder("/system/fonts", cacheDir.getAbsolutePath());

        // Append zip to filename since it is probably removed
        // for list presentation
        if (!zipFileName.endsWith(".zip")) {
            zipFileName = zipFileName + ".zip";
        }

        // Copy target themed fonts zip to our cache dir
        Context themeContext = getAppContext(pid);
        AssetManager am = themeContext.getAssets();
        File fontZip = new File(cacheDir, zipFileName);
        try {
            InputStream inputStream = am.open("fonts/" + zipFileName);
            OutputStream outputStream = new FileOutputStream(fontZip);
            IOUtils.bufferedCopy(inputStream, outputStream);
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        // Unzip new fonts and delete zip file, overwriting any system fonts
        IOUtils.unzip(fontZip.getAbsolutePath(), cacheDir.getAbsolutePath());

        boolean deleted = fontZip.delete();
        if (!deleted) {
            Log.e(TAG, "Could not delete ZIP file...");
        }

        // Check if theme zip included a fonts.xml. If not, Substratum
        // is kind enough to provide one for us in it's assets
        try {
            File testConfig = new File(cacheDir, "fonts.xml");
            if (!testConfig.exists()) {
                Context subContext = getSubsContext();
                AssetManager subsAm = subContext.getAssets();
                InputStream inputStream = subsAm.open("fonts.xml");
                OutputStream outputStream = new FileOutputStream(testConfig);
                IOUtils.bufferedCopy(inputStream, outputStream);
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        // Prepare system theme fonts folder and copy new fonts folder from our cache
        IOUtils.deleteThemedFonts();
        IOUtils.createFontDirIfNotExists();
        IOUtils.copyFolder(cacheDir.getAbsolutePath(), IOUtils.SYSTEM_THEME_FONT_PATH);

        // Let system know it's time for a font change
        IOUtils.clearThemeCache();
        refreshFonts();
    }

    private void clearFonts() {
        IOUtils.deleteThemedFonts();
        refreshFonts();
    }

    private void refreshFonts() {
        // Set permissions on font files and config xml
        File themeFonts = new File(IOUtils.SYSTEM_THEME_FONT_PATH);
        if (themeFonts.exists()) {
            // Set permissions
            IOUtils.setPermissionsRecursive(themeFonts,
                    FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IRWXO,
                    FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH);
        }

        // Let system know it's time for a font change
        SystemProperties.set("sys.refresh_theme", "1");
        //TODO: Uncomment the next line when we have full fonts support
        //Typeface.recreateDefaults();
        float fontSize = Settings.System.getFloatForUser(mContext.getContentResolver(),
                Settings.System.FONT_SCALE, 1.0f, UserHandle.USER_CURRENT);
        Settings.System.putFloatForUser(mContext.getContentResolver(),
                Settings.System.FONT_SCALE, (fontSize + 0.0000001f), UserHandle.USER_CURRENT);
    }

    private void applyThemedSounds(String pid, String zipFileName) {
        // Prepare local cache dir for font package assembly
        log("CopySounds - Package ID = \'" + pid + "\'");
        log("CopySounds - File name = \'" + zipFileName + "\'");

        File cacheDir = new File(IOUtils.SYSTEM_THEME_CACHE_PATH, "SoundsCache");
        if (cacheDir.exists()) {
            IOUtils.deleteRecursive(cacheDir);
        }

        boolean created = cacheDir.mkdirs();
        if (!created) {
            Log.e(TAG, "Could not create cache directory...");
        }

        // Append zip to filename since it is probably removed
        // for list presentation
        if (!zipFileName.endsWith(".zip")) {
            zipFileName = zipFileName + ".zip";
        }

        // Copy target themed sounds zip to our cache dir
        Context themeContext = getAppContext(pid);
        AssetManager am = themeContext.getAssets();
        File soundsZip = new File(cacheDir, zipFileName);
        try {
            InputStream inputStream = am.open("audio/" + zipFileName);
            OutputStream outputStream = new FileOutputStream(soundsZip);
            IOUtils.bufferedCopy(inputStream, outputStream);
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        // Unzip new sounds and delete zip file
        IOUtils.unzip(soundsZip.getAbsolutePath(), cacheDir.getAbsolutePath());

        boolean deleted = soundsZip.delete();
        if (!deleted) {
            Log.e(TAG, "Could not delete ZIP file...");
        }

        clearSounds();
        IOUtils.createAudioDirIfNotExists();

        for (Sound sound : SOUNDS) {
            File soundsCache = new File(IOUtils.SYSTEM_THEME_CACHE_PATH, sound.cachePath);

            if (!(soundsCache.exists() && soundsCache.isDirectory())) {
                continue;
            }

            IOUtils.createDirIfNotExists(sound.themePath);

            File mp3 = new File(IOUtils.SYSTEM_THEME_CACHE_PATH, sound.cachePath + sound.soundPath + ".mp3");
            File ogg = new File(IOUtils.SYSTEM_THEME_CACHE_PATH, sound.cachePath + sound.soundPath + ".ogg");
            if (ogg.exists()) {
                IOUtils.bufferedCopy(ogg,
                        new File(sound.themePath + File.separator + sound.soundPath + ".ogg"));
            } else if (mp3.exists()) {
                IOUtils.bufferedCopy(mp3,
                        new File(sound.themePath + File.separator + sound.soundPath + ".mp3"));
            }
        }

        // Let system know it's time for a sound change
        IOUtils.clearThemeCache();
        refreshSounds();
    }

    private void clearSounds() {
        IOUtils.deleteThemedAudio();
        SoundUtils.setDefaultAudible(mContext, RingtoneManager.TYPE_ALARM);
        SoundUtils.setDefaultAudible(mContext, RingtoneManager.TYPE_NOTIFICATION);
        SoundUtils.setDefaultAudible(mContext, RingtoneManager.TYPE_RINGTONE);
        SoundUtils.setDefaultUISounds(mContext.getContentResolver(), "lock_sound", "Lock.ogg");
        SoundUtils.setDefaultUISounds(mContext.getContentResolver(), "unlock_sound", "Unlock.ogg");
        SoundUtils.setDefaultUISounds(mContext.getContentResolver(), "low_battery_sound", "LowBattery.ogg");
    }

    private void refreshSounds() {
        File soundsDir = new File(IOUtils.SYSTEM_THEME_AUDIO_PATH);

        if (!soundsDir.exists()) {
            return;
        }

        // Set permissions
        IOUtils.setPermissionsRecursive(soundsDir,
                FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IRWXO,
                FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH);

        int metaDataId = getSubsContext().getResources().getIdentifier(
                "content_resolver_notification_metadata",
                "string", SUBSTRATUM_PACKAGE);

        for (Sound sound : SOUNDS) {
            File themePath = new File(sound.themePath);

            if (!(themePath.exists() && themePath.isDirectory())) {
                continue;
            }

            File mp3 = new File(themePath, sound.soundPath + ".mp3");
            File ogg = new File(themePath, sound.soundPath + ".ogg");

            if (ogg.exists()) {
                if (sound.themePath.equals(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH) && sound.type !=
                        0) {
                    SoundUtils.setUIAudible(mContext, ogg, ogg, sound.type, sound.soundName);
                } else if (sound.themePath.equals(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH)) {
                    SoundUtils.setUISounds(mContext.getContentResolver(), sound.soundName, ogg
                            .getAbsolutePath());
                } else {
                    SoundUtils.setAudible(mContext, ogg, ogg, sound.type, getSubsContext().getString
                            (metaDataId));
                }
            } else if (mp3.exists()) {
                if (sound.themePath.equals(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH) && sound.type !=
                        0) {
                    SoundUtils.setUIAudible(mContext, mp3, mp3, sound.type, sound.soundName);
                } else if (sound.themePath.equals(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH)) {
                    SoundUtils.setUISounds(mContext.getContentResolver(), sound.soundName,
                            mp3.getAbsolutePath());
                } else {
                    SoundUtils.setAudible(mContext, mp3, mp3, sound.type, getSubsContext().getString
                            (metaDataId));
                }
            } else {
                if (sound.themePath.equals(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH)) {
                    SoundUtils.setDefaultUISounds(mContext.getContentResolver(),
                            sound.soundName, sound.soundPath + ".ogg");
                } else {
                    SoundUtils.setDefaultAudible(mContext, sound.type);
                }
            }
        }
    }

    private void log(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    private static class Sound {
        String themePath;
        String cachePath;
        String soundName;
        String soundPath;
        int type;

        Sound(String themePath, String cachePath, String soundName, String soundPath) {
            this.themePath = themePath;
            this.cachePath = cachePath;
            this.soundName = soundName;
            this.soundPath = soundPath;
        }

        Sound(String themePath, String cachePath, String soundName, String soundPath, int type) {
            this.themePath = themePath;
            this.cachePath = cachePath;
            this.soundName = soundName;
            this.soundPath = soundPath;
            this.type = type;
        }
    }

    private class PackageInstallObserver extends IPackageInstallObserver2.Stub {
        @Override
        public void onUserActionRequired(Intent intent) throws RemoteException {
            log("Installer - user action required callback");
            isWaiting = false;
        }

        @Override
        public void onPackageInstalled(String packageName, int returnCode,
                                       String msg, Bundle extras) {
            log("Installer - successfully installed \'" + packageName + "\'!");
            installedPackageName = packageName;
            isWaiting = false;
        }
    }

    private class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        @Override
        public void packageDeleted(String packageName, int returnCode) {
            log("Remover - successfully removed \'" + packageName + "\'");
            isWaiting = false;
        }
    }
}

