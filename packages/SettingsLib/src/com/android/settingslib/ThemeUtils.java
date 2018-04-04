/*
 * Copyright (C) 2018 AOSiP
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

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;

public class ThemeUtils {
    private static final String TAG = "ThemeUtils";

    private static IOverlayManager mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));

    private static int mCurrentUserId = UserHandle.USER_CURRENT;

    private String ACCENTS_PACKAGE_NAME_PREFIX = "com.accents.";
    private String ACCENTS[] = { "red", "pink", "purple", "deeppurple", "indigo",
            "blue", "lightblue", "cyan", "teal", "green", "lightgreen", "lime",
            "yellow", "amber", "orange", "deeporange", "brown", "blue",
            "bluegrey", canUseBlackAccent() ? "black" : "white" };

     // Check if black accent should be used
    private boolean canUseBlackAccent() {
        return !isUsingDarkTheme() && !isUsingBlackAFTheme();
    }

    private boolean isUsingDarkTheme() {
        return isOverlayEnabled("com.android.system.theme.dark");
    }

    private boolean isUsingBlackAFTheme() {
        return isOverlayEnabled("com.android.system.theme.blackaf");
    }

    // Checks if the overlay is enabled
    private boolean isOverlayEnabled(String packageName) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = mOverlayManager.getOverlayInfo(packageName,
                    mCurrentUserId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }

    // Switches theme accent from to another or back to stock
    public void updateAccents(Context mContext) {
        int accentSetting = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.ACCENT_PICKER, 0, mCurrentUserId);

        if (accentSetting == 0) {
            unloadAccents();
        } else {
            try {
                mOverlayManager.setEnabled(
                    ACCENTS_PACKAGE_NAME_PREFIX + ACCENTS[accentSetting - 1],
                    true, mCurrentUserId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change theme", e);
            } catch (IndexOutOfBoundsException e) {
                Log.w(TAG, "WTF Happened here?", e);
            }
        }
    }

    // Unload all the theme accents
    public void unloadAccents(Context mContext) {
        try {
            for (String accent : ACCENTS) {
                mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + accent,
                        false, mCurrentUserId);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

        // Check for black and white accent overlays
    public void unfuckBlackWhiteAccent() {
        OverlayInfo themeInfo = null;
        try {
             if (!canUseBlackAccent()) {
                themeInfo = mOverlayManager.getOverlayInfo(ACCENTS_PACKAGE_NAME_PREFIX + "black",
                        mCurrentUserId);
                if (themeInfo != null && themeInfo.isEnabled()) {
                    mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + "black",
                            false /*disable*/, mCurrentUserId);
                    mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + "white",
                            true, mCurrentUserId);
                }
            } else {
                themeInfo = mOverlayManager.getOverlayInfo(ACCENTS_PACKAGE_NAME_PREFIX + "white",
                        mCurrentUserId);
                if (themeInfo != null && themeInfo.isEnabled()) {
                    mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + "white",
                            false /*disable*/, mCurrentUserId);
                    mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + "black",
                            true, mCurrentUserId);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    // Unloads the stock dark theme
    private void unloadStockDarkTheme() {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = mOverlayManager.getOverlayInfo("com.android.systemui.theme.dark",
                    mCurrentUserId);
            if (themeInfo != null && themeInfo.isEnabled()) {
                mOverlayManager.setEnabled("com.android.systemui.theme.dark",
                        false /*disable*/, mCurrentUserId);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void handleDarkThemeEnablement(boolean useDarkTheme, boolean useBlackAFTheme) {
        // Check for black and white accent so we don't end up
        // with white on white or black on black
        unfuckBlackWhiteAccent();
        if (useDarkTheme || useBlackAFTheme) unloadStockDarkTheme();
        if (ThemeUtils.isUsingDarkTheme() != useDarkTheme) {
            try {
                mOverlayManager.setEnabled("com.android.system.theme.dark",
                        useDarkTheme, mCurrentUserId);
                mOverlayManager.setEnabled("com.android.settings.theme.dark",
                        useDarkTheme, mCurrentUserId);
                mOverlayManager.setEnabled("com.android.dui.theme.dark",
                        useDarkTheme, mCurrentUserId);
                mOverlayManager.setEnabled("com.android.gboard.theme.dark",
                        useDarkTheme, mCurrentUserId);
                mOverlayManager.setEnabled("com.android.updater.theme.dark",
                        useDarkTheme, mCurrentUserId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change theme", e);
            }
        }
        if (ThemeUtils.isUsingBlackAFTheme() != useBlackAFTheme) {
            try {
                mOverlayManager.setEnabled("com.android.system.theme.blackaf",
                        useBlackAFTheme, mCurrentUserId);
                mOverlayManager.setEnabled("com.android.settings.theme.blackaf",
                        useBlackAFTheme, mCurrentUserId);
                mOverlayManager.setEnabled("com.android.dui.theme.blackaf",
                        useBlackAFTheme, mCurrentUserId);
                mOverlayManager.setEnabled("com.android.gboard.theme.blackaf",
                        useBlackAFTheme, mCurrentUserId);
                mOverlayManager.setEnabled("com.android.updater.theme.blackaf",
                        useBlackAFTheme, mCurrentUserId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change theme", e);
            }
        }
    }
}