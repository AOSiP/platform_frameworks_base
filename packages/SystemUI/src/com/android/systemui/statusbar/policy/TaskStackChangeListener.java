/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.app.ActivityManager.TaskSnapshot;
import android.content.ComponentName;
import android.os.UserHandle;
import android.util.Log;

/**
 * An interface to track task stack changes. Classes should implement this instead of
 * {@link android.app.ITaskStackListener} to reduce IPC calls from system services.
 */
public abstract class TaskStackChangeListener {

    // Binder thread callbacks
    public void onTaskStackChangedBackground() { }

    // Main thread callbacks
    public void onTaskStackChanged() { }
    public void onTaskRemoved(int taskId) { }
    public void onTaskMovedToFront(int taskId) { }
    public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation) { }
}
