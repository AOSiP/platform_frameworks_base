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
 * limitations under the License
 */

package com.android.settingslib;

import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.ServiceManager;
import android.os.UserHandle;

public class ThemeUtils {
    private static final String TAG = "ThemeUtils";

    private IOverlayManager mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));

    private int mCurrentUserId = UserHandle.USER_CURRENT;

     // Check for the dark system theme
    public static boolean isUsingDarkTheme() {
        return isOverlayEnabled("com.android.system.theme.dark");
    }

    // Check for the blackaf system theme
    public static boolean isUsingBlackAFTheme() {
        return isOverlayEnabled("com.android.system.theme.blackaf");
    }

    // Checks if the overlay is enabled
    public static boolean isOverlayEnabled(String packageName) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = mOverlayManager.getOverlayInfo(packageName,
                    mCurrentUserId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }
}