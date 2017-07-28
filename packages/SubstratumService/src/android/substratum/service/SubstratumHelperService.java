package android.substratum.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.Process;
import android.os.SELinux;
import android.util.Log;

import com.android.internal.substratum.ISubstratumHelperService;

import java.io.File;

public class SubstratumHelperService extends Service {
    private static final String TAG = "SubstratumService";

    private final File EXTERNAL_CACHE_DIR =
            new File(Environment.getExternalStorageDirectory(), ".substratum");
    private final File SYSTEM_THEME_DIR = new File(Environment.getDataSystemDirectory(), "theme");

    ISubstratumHelperService mISubstratumHelperService = new ISubstratumHelperService.Stub() {
        @Override
        public void applyBootAnimation() {
            if (!isAuthorized(Binder.getCallingUid())) return;

            File src = new File(EXTERNAL_CACHE_DIR, "bootanimation.zip");
            File dst = new File(SYSTEM_THEME_DIR, "bootanimation.zip");
            int perms = FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IROTH;

            if (dst.exists()) dst.delete();
            FileUtils.copyFile(src, dst);
            FileUtils.setPermissions(dst, perms, -1, -1);
            SELinux.restorecon(dst);
            src.delete();
        }

        @Override
        public void applyShutdownAnimation() {
            if (!isAuthorized(Binder.getCallingUid())) return;

            File src = new File(EXTERNAL_CACHE_DIR, "shutdownanimation.zip");
            File dst = new File(SYSTEM_THEME_DIR, "shutdownanimation.zip");
            int perms = FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IROTH;

            if (dst.exists()) dst.delete();
            FileUtils.copyFile(src, dst);
            FileUtils.setPermissions(dst, perms, -1, -1);
            SELinux.restorecon(dst);
            src.delete();
        }

        @Override
        public void applyProfile(String name) {
            if (!isAuthorized(Binder.getCallingUid())) return;

            FileUtils.deleteContents(SYSTEM_THEME_DIR);

            File profileDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/substratum/profiles/" + name + "/theme");
            if (profileDir.exists()) {
                File profileFonts = new File(profileDir, "fonts");
                if (profileFonts.exists()) {
                    File dst = new File(SYSTEM_THEME_DIR, "fonts");
                    copyDir(profileFonts, dst);
                }

                File profileSounds = new File(profileDir, "audio");
                if (profileSounds.exists()) {
                    File dst = new File(SYSTEM_THEME_DIR, "audio");
                    copyDir(profileSounds, dst);
                }

                File profileBootAnimation = new File(profileDir, "bootanimation.zip");
                if (profileBootAnimation.exists()) {
                    File dst = new File(SYSTEM_THEME_DIR, "bootanimation.zip");
                    FileUtils.copyFile(profileBootAnimation, dst);
                    int perms = FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IROTH;
                    FileUtils.setPermissions(dst, perms, -1, -1);
                }

                File profileShutdownAnimation = new File(profileDir, "shutdownanimation.zip");
                if (profileShutdownAnimation.exists()) {
                    File dst = new File(SYSTEM_THEME_DIR, "shutdownanimation.zip");
                    FileUtils.copyFile(profileShutdownAnimation, dst);
                    int perms = FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IROTH;
                    FileUtils.setPermissions(dst, perms, -1, -1);
                }

                SELinux.restorecon(SYSTEM_THEME_DIR);
            }
        }

        private boolean isAuthorized(int uid) {
            return Process.SYSTEM_UID == uid;
        }

        private boolean copyDir(File src, File dst) {
            File[] files = src.listFiles();
            boolean success = true;

            if (files != null) {
                for (File file : files) {
                    File newFile = new File(dst, file.getName());
                    if (file.isDirectory()) {
                        success &= copyDir(file, newFile);
                    } else {
                        success &= FileUtils.copyFile(file, newFile);
                    }
                }
            } else {
                // not a directory
                success = false;
            }
            return success;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mISubstratumHelperService.asBinder();
    }
}
