/*
 * Copyright (C) 2020 Paranoid Android
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

package com.android.server.biometrics.face;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.face.Face;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;

import com.android.server.biometrics.AuthenticationClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import vendor.pa.biometrics.face.V1_0.ISenseService;
import vendor.pa.biometrics.face.V1_0.ISenseServiceReceiver;

/**
 * A manager that connects to the Paranoid FaceSense services which
 * dispacthes biometric face calls
 *
 * @hide
 */
public class ParanoidSenseManager {

    protected static final String TAG = "ParanoidSenseManager";

    public static final int SENSE_ID = 1109;

    private Context mContext;
    private FaceService mFaceService;
    private Handler mHandler;

    private int mCurrentUserId;

    private Handler mSenseServiceHandler;
    public boolean mBound = false;
    private static final boolean mSenseEnabled = SystemProperties.getBoolean("ro.face.sense_service", false);
    private final BroadcastReceiver mUserUnlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mSenseEnabled) {
                if (getService(mCurrentUserId) == null) {
                    bind(mCurrentUserId);
                }
            }
        }
    };

    SparseArray<ISenseService> mServices = new SparseArray<>();
    ISenseServiceReceiver mReceiver = new ISenseServiceReceiver.Stub() {
        @Override
        public void onEnrollResult(int faceId, int userId, int remaining) {
            mHandler.post(new Runnable() {
                @Override
                public final void run() {
                    mFaceService.handleEnrollResult(new Face(
                            mFaceService.getBiometricUtils().getUniqueName(
                            mContext, userId), faceId, SENSE_ID), remaining);
                }
            });
        }

        @Override
        public void onAuthenticated(int faceId, int userId, byte[] token) {
            mHandler.post(new Runnable() {
                @Override
                public final void run() {
                    Face face = new Face("", faceId, SENSE_ID);
                    ArrayList<Byte> token_AL = new ArrayList<>(token.length);
                    for (byte b : token) {
                        token_AL.add(new Byte(b));
                    }
                    mFaceService.handleAuthenticated(face, token_AL);
                }
            });
        }

        @Override
        public void onAcquired(int userId, int acquiredInfo, int vendorCode) {
            mHandler.post(new Runnable() {
                @Override
                public final void run() {
                    mFaceService.handleAcquired(SENSE_ID, acquiredInfo, vendorCode);
                }
            });
        }

        @Override
        public void onError(int error, int vendorCode) {
            mHandler.post(new Runnable() {
                @Override
                public final void run() {
                    mFaceService.handleError(SENSE_ID, error, vendorCode);
                }
            });
        }

        @Override
        public void onRemoved(int[] faceIds, int userId) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public final void run() {
                    if (faceIds.length > 0) {
                        for (int i = 0; i < faceIds.length; i++) {
                            mFaceService.handleRemoved(new Face("", faceIds[i], SENSE_ID), (faceIds.length - i) - 1);
                        }
                        return;
                    }
                    mFaceService.handleRemoved(new Face("", 0, SENSE_ID), 0);
                }
            });
        }

        @Override
        public void onEnumerate(int[] faceIds, int userId) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public final void run() {
                    if (faceIds.length > 0) {
                        for (int i = 0; i < faceIds.length; i++) {
                            mFaceService.handleEnumerate(new Face("", faceIds[i], SENSE_ID), (faceIds.length - i) - 1);
                        }
                        return;
                    }
                    mFaceService.handleEnumerate(null, 0);
                }
            });
        }

        @Override
        public void onLockoutChanged(long duration) throws RemoteException {
            if (duration == 0) {
                mFaceService.mCurrentUserLockoutMode = AuthenticationClient.LOCKOUT_NONE;
            } else if (duration == Long.MAX_VALUE) {
                mFaceService.mCurrentUserLockoutMode = AuthenticationClient.LOCKOUT_PERMANENT;
            } else {
                mFaceService.mCurrentUserLockoutMode = AuthenticationClient.LOCKOUT_TIMED;
            }
            mHandler.post(new Runnable() {
                @Override
                public final void run() {
                    if (duration == 0) {
                        mFaceService.notifyLockoutResetMonitors();
                    }
                }
            });
        }
    };

    public ParanoidSenseManager(Context context, FaceService service, Handler handler) {
        mContext = context;
        mFaceService = service;
        mHandler = handler;
        mContext.registerReceiver(mUserUnlockReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
    }

    public int authenticate(long operationId) {
        ISenseService service = getService(mCurrentUserId);
        if (service != null) {
            try{
                service.authenticate(operationId);
            } catch (Exception e) {
                Slog.e(TAG, "authenticate failed", e);
            }
            return 0;
        }
        bind(mCurrentUserId);
        Slog.w(TAG, "authenticate(): sense service not started!");
        return 3;
    }

    public int cancel() {
        ISenseService service = getService(mCurrentUserId);
        if (service == null) {
            return 0;
        }

        try{
            service.cancel();
        }catch (Exception e) {
            Slog.e(TAG, "cancel failed", e);
        }
        return 0;
    }

    public int remove(int biometricId) {
        ISenseService service = getService(mCurrentUserId);
        if (service != null) {
            try{
                service.remove(biometricId);
            }catch (Exception e) {
                Slog.e(TAG, "remove failed", e);
            }
            return 0;
        }
        bind(mCurrentUserId);
        Slog.w(TAG, "remove(): sense service not started!");
        return 3;
    }

    public int enumerate() {
        ISenseService service = getService(mCurrentUserId);
        if (service != null) {
            mSenseServiceHandler.post(new Runnable() {
                @Override
                public final void run() {
                    try {
                        service.enumerate();
                    } catch (Exception e) {
                        Slog.e(TAG, "enumerate failed", e);
                        mFaceService.handleError(SENSE_ID, 8, 0);
                    }
                }
            });
            return 0;
        }
        bind(mCurrentUserId);
        Slog.w(TAG, "enumerate(): sense service not started!");
        return 3;
    }

    public int enroll(byte[] cryptoToken, int timeout, int[] disabledFeatures) {
        ISenseService service = getService(mCurrentUserId);
        if (service != null) {
            try{
                service.enroll(cryptoToken, timeout, disabledFeatures);
            } catch (Exception e) {
                Slog.e(TAG, "enroll failed", e);
            }
            return 0;
        }
        bind(mCurrentUserId);
        Slog.w(FaceService.TAG, "enroll(): sense service not started!");
        return 3;
    }

    public void resetLockout(byte[] cryptoToken) {
        ISenseService service = getService(mCurrentUserId);
        if (service != null) {
            try{
                service.resetLockout(cryptoToken);
            } catch (Exception e) {
                Slog.e(TAG, "resetLockout failed", e);
            }
            return;
        }
        bind(mCurrentUserId);
        Slog.w(TAG, "resetLockout(): sense service not started!");
    }

    public int getAuthenticatorId() {
        int authId = 0;
        ISenseService service = getService(mCurrentUserId);
        if (service != null) {
            try{
                authId = service.getAuthenticatorId();
            } catch (Exception e) {
                Slog.e(TAG, "getAuthenticatorId failed", e);
            }
            return authId;
        }
        bind(mCurrentUserId);
        Slog.w(TAG, "updateActiveGroup(): sense service not started!");
        return authId;
    }

    public long generateChallenge(int timeout) {
        ISenseService service = getService(mCurrentUserId);
        if (service != null) {
            try {
                return service.generateChallenge(timeout);
            } catch (Exception e) {
                Slog.e(TAG, "generateChallenge failed", e);
            }
        } else {
            bind(mCurrentUserId);
            Slog.w(TAG, "startGenerateChallenge(): sense service not started!");
        }
        return 0;
    }

    public int revokeChallenge() {
        ISenseService service = getService(mCurrentUserId);
        if (service != null) {
            try {
                return service.revokeChallenge();
            } catch (Exception e) {
                Slog.e(TAG, "startRevokeChallenge failed", e);
            }
        }
        return 0;
    }

    public void setCurrentUserId(int userId) {
        mCurrentUserId = userId;
    }

    public void setServiceHandler(Handler handler) {
        mSenseServiceHandler = handler;
    }

    public boolean callForBind(int userId) {
        return bind(userId);
    }

    private boolean bind(int userId) {
        Slog.d(TAG, "bind");
        if (!isServiceEnabled()) {
            Slog.d(TAG, "Sense service disabled");
            return false;
        } else if (mBound) {
            Slog.d(TAG, "Sense service is binding");
            return true;
        } else {
            if (userId != UserHandle.USER_NULL && getService(userId) == null) {
                if (createService(userId)) {
                    return true;
                }
                return false;
            }
            return false;
        }
    }

    private boolean createService(int userId) {
        try {
            Intent intent = getServiceIntent();
            if (intent == null) {
                Slog.d(TAG, "Sense service not found");
                return false;
            }
            boolean result = mContext.bindServiceAsUser(intent, new SenseServiceConnection(userId), 65, UserHandle.of(userId));
            if (result) {
                mBound = true;
            }
            return result;
        } catch (Exception e) {
            Slog.e(TAG, "bind failed", e);
        }
        return false;
    }

    public ISenseService getService(int userId) {
        if (userId == UserHandle.USER_NULL) {
            mFaceService.updateActiveGroup(ActivityManager.getCurrentUser(), null);
        }
        return mServices.get(mCurrentUserId);
    }

    private Intent getServiceIntent() {
        Intent intent = new Intent("sense:remote");
        intent.setComponent(ComponentName.unflattenFromString(
                "co.aospa.facesense/co.aospa.facesense.SenseService"));
        return intent;
    }

    private boolean isServiceEnabled() {
        PackageManager pm = mContext.getPackageManager();
        if (!mSenseEnabled) {
            return false;
        }
        Intent intent = getServiceIntent();
        ResolveInfo info = pm.resolveService(intent, 131072);
        if (info == null || !info.serviceInfo.isEnabled()) {
            return false;
        }
        return true;
    }

    public boolean isEnabled() {
        return mSenseEnabled;
    }

    public boolean isDetected() {
        boolean enabled = isServiceEnabled();
        if (enabled) {
            mHandler.post(new Runnable() {
                @Override
                public final void run() {
                    if (getService(mCurrentUserId) == null) {
                        bind(mCurrentUserId);
                    }
                }
            });
        }
        return enabled;
    }

    private class SenseServiceConnection implements ServiceConnection {
        int mUserId;

        public SenseServiceConnection(int userId) {
            mUserId = userId;
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Slog.d(TAG, "Sense service connected");
            ISenseService senseService = ISenseService.Stub.asInterface(service);
            if (senseService != null) {
                synchronized (mServices) {
                    try {
                        senseService.setCallback(mReceiver);
                        senseService.asBinder().linkToDeath(new IBinder.DeathRecipient() {
                            @Override
                            public void binderDied() {
                                Slog.e(TAG, "Sense service binder died");
                                mServices.remove(mUserId);
                                if (mUserId == mCurrentUserId) {
                                    bind(mUserId);
                                }
                            }
                        }, 0);
                        mServices.put(mUserId, senseService);
                        mHandler.post(new Runnable() {
                            @Override
                            public final void run() {
                                if (mServices.size() == 1) {
                                    mFaceService.loadAuthenticatorIds();
                                }
                                mFaceService.updateActiveGroup(mUserId, null);
                                mFaceService.doTemplateCleanupForUser(mUserId);
                            }
                        });
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    mBound = false;
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Slog.d(TAG, "Sense service disconnected");
            mServices.remove(mUserId);
            mBound = false;
            if (mUserId == mCurrentUserId) {
                bind(mUserId);
            }
        }
    }
}
