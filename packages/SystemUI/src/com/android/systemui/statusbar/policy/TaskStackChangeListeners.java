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
import android.app.IActivityManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks all the task stack listeners
 */
public class TaskStackChangeListeners extends TaskStackListener {

    private static final String TAG = TaskStackChangeListeners.class.getSimpleName();

    /**
     * List of {@link TaskStackChangeListener} registered from {@link #addListener}.
     */
    private final List<TaskStackChangeListener> mTaskStackListeners = new ArrayList<>();
    private final List<TaskStackChangeListener> mTmpListeners = new ArrayList<>();

    private final Handler mHandler;
    private boolean mRegistered;

    public TaskStackChangeListeners(Looper looper) {
        mHandler = new H(looper);
    }

    public void addListener(IActivityManager am, TaskStackChangeListener listener) {
        mTaskStackListeners.add(listener);
        if (!mRegistered) {
            // Register mTaskStackListener to IActivityManager only once if needed.
            try {
                am.registerTaskStackListener(this);
                mRegistered = true;
            } catch (Exception e) {
                Log.w(TAG, "Failed to call registerTaskStackListener", e);
            }
        }
    }

    public void removeListener(TaskStackChangeListener listener) {
        mTaskStackListeners.remove(listener);
    }

    @Override
    public void onTaskStackChanged() throws RemoteException {
        // Call the task changed callback for the non-ui thread listeners first
        synchronized (mTaskStackListeners) {
            mTmpListeners.clear();
            mTmpListeners.addAll(mTaskStackListeners);
        }
        for (int i = mTmpListeners.size() - 1; i >= 0; i--) {
            mTmpListeners.get(i).onTaskStackChangedBackground();
        }

        mHandler.removeMessages(H.ON_TASK_STACK_CHANGED);
        mHandler.sendEmptyMessage(H.ON_TASK_STACK_CHANGED);
    }

    @Override
    public void onActivityPinned(String packageName, int userId, int taskId)
            throws RemoteException {

    }

    @Override
    public void onActivityUnpinned() throws RemoteException {

    }

    @Override
    public void onPinnedActivityRestartAttempt(boolean clearedTask)
            throws RemoteException{

    }

    @Override
    public void onPinnedStackAnimationStarted() throws RemoteException {

    }

    @Override
    public void onPinnedStackAnimationEnded() throws RemoteException {

    }

    @Override
    public void onActivityForcedResizable(String packageName, int taskId, int reason)
            throws RemoteException {

    }

    @Override
    public void onActivityDismissingDockedStack() throws RemoteException {

    }

    @Override
    public void onActivityLaunchOnSecondaryDisplayFailed() throws RemoteException {

    }

    @Override
    public void onTaskProfileLocked(int taskId, int userId) {

    }

    @Override
    public void onTaskSnapshotChanged(int taskId, TaskSnapshot snapshot) throws RemoteException {

    }

    @Override
    public void onTaskCreated(int taskId, ComponentName componentName) throws RemoteException {

    }

    @Override
    public void onTaskRemoved(int taskId) throws RemoteException {
        mHandler.obtainMessage(H.ON_TASK_REMOVED, taskId, 0).sendToTarget();
    }

    @Override
    public void onTaskMovedToFront(int taskId) throws RemoteException {
        mHandler.obtainMessage(H.ON_TASK_MOVED_TO_FRONT, taskId, 0).sendToTarget();
    }

    @Override
    public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation)
            throws RemoteException {
        mHandler.obtainMessage(H.ON_ACTIVITY_REQUESTED_ORIENTATION_CHANGE, taskId,
                requestedOrientation).sendToTarget();
    }

    private final class H extends Handler {
        private static final int ON_TASK_STACK_CHANGED = 1;
        private static final int ON_TASK_REMOVED = 13;
        private static final int ON_TASK_MOVED_TO_FRONT = 14;
        private static final int ON_ACTIVITY_REQUESTED_ORIENTATION_CHANGE = 15;


        public H(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            synchronized (mTaskStackListeners) {
                switch (msg.what) {
                    case ON_TASK_STACK_CHANGED: {
                        Trace.beginSection("onTaskStackChanged");
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            mTaskStackListeners.get(i).onTaskStackChanged();
                        }
                        Trace.endSection();
                        break;
                    }
                    case ON_TASK_REMOVED: {
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            mTaskStackListeners.get(i).onTaskRemoved(msg.arg1);
                        }
                        break;
                    }
                    case ON_TASK_MOVED_TO_FRONT: {
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            mTaskStackListeners.get(i).onTaskMovedToFront(msg.arg1);
                        }
                        break;
                    }
                    case ON_ACTIVITY_REQUESTED_ORIENTATION_CHANGE: {
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            mTaskStackListeners.get(i).onActivityRequestedOrientationChanged(
                                    msg.arg1, msg.arg2);
                        }
                        break;
                    }
                }
            }
        }
    }
}
