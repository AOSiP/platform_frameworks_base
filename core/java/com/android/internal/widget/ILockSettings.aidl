/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.internal.widget;

import android.app.trust.IStrongAuthTracker;
import com.android.internal.widget.ICheckCredentialProgressCallback;
import com.android.internal.widget.VerifyCredentialResponse;

/** {@hide} */
interface ILockSettings {
    void setBoolean(in String key, in boolean value, in int userId);
    void setLong(in String key, in long value, in int userId);
    void setString(in String key, in String value, in int userId);
    boolean getBoolean(in String key, in boolean defaultValue, in int userId);
    long getLong(in String key, in long defaultValue, in int userId);
    String getString(in String key, in String defaultValue, in int userId);
    void setLockCredential(in String credential, int type, in String savedCredential, int requestedQuality, int userId);
    void resetKeyStore(int userId);
    VerifyCredentialResponse checkCredential(in String credential, int type, int userId,
            in ICheckCredentialProgressCallback progressCallback);
    VerifyCredentialResponse verifyCredential(in String credential, int type, long challenge, int userId);
    VerifyCredentialResponse verifyTiedProfileChallenge(String credential, int type, long challenge, int userId);
    byte getLockPatternSize(int userId);
    boolean checkVoldPassword(int userId);
    boolean havePattern(int userId);
    boolean havePassword(int userId);
    void setSeparateProfileChallengeEnabled(int userId, boolean enabled, String managedUserPassword);
    boolean getSeparateProfileChallengeEnabled(int userId);
    void registerStrongAuthTracker(in IStrongAuthTracker tracker);
    void unregisterStrongAuthTracker(in IStrongAuthTracker tracker);
    void requireStrongAuth(int strongAuthReason, int userId);
    void systemReady();
    void userPresent(int userId);
    int getStrongAuthForUser(int userId);

    long addEscrowToken(in byte[] token, int userId);
    boolean removeEscrowToken(long handle, int userId);
    boolean isEscrowTokenActive(long handle, int userId);
    boolean setLockCredentialWithToken(String credential, int type, long tokenHandle,
            in byte[] token, int requestedQuality, int userId);
    void unlockUserWithToken(long tokenHandle, in byte[] token, int userId);
    void sanitizePassword();
    String getPassword();
}
