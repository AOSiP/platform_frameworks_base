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

package com.android.systemui.statusbar.policy;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;


public class ActivityManagerWrapper {

    private static final String TAG = "ActivityManagerWrapper";

    private static final ActivityManagerWrapper sInstance = new ActivityManagerWrapper();

    private final PackageManager mPackageManager;
    private final TaskStackChangeListeners mTaskStackChangeListeners;

    private ActivityManagerWrapper() {
        final Context context = AppGlobals.getInitialApplication();
        mPackageManager = context.getPackageManager();
        mTaskStackChangeListeners = new TaskStackChangeListeners(Looper.getMainLooper());
    }

    public static ActivityManagerWrapper getInstance() {
        return sInstance;
    }

    /**
     * Registers a task stack listener with the system.
     * This should be called on the main thread.
     */
    public void registerTaskStackListener(TaskStackChangeListener listener) {
        synchronized (mTaskStackChangeListeners) {
            mTaskStackChangeListeners.addListener(ActivityManager.getService(), listener);
        }
    }

    /**
     * Unregisters a task stack listener with the system.
     * This should be called on the main thread.
     */
    public void unregisterTaskStackListener(TaskStackChangeListener listener) {
        synchronized (mTaskStackChangeListeners) {
            mTaskStackChangeListeners.removeListener(listener);
        }
    }

}
