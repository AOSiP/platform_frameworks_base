/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.statusbar;

import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;

public class ThemeAccentUtils {

    public static final String TAG = "ThemeAccentUtils";

    // Vendor overlays to ignore
    public static final String[] BLACKLIST_VENDOR_OVERLAYS = {
        "SysuiDarkTheme",
        "Pixel",
        "DisplayCutoutEmulationCorner",
        "DisplayCutoutEmulationDouble",
        "DisplayCutoutEmulationNarrow",
        "DisplayCutoutEmulationWide",
    };

    // Stock dark theme package
    private static final String STOCK_DARK_THEME = "com.android.systemui.theme.dark";

      // Light themes
      private static final String[] LIGHT_THEMES = {
        "com.google.intelligence.sense.theme.light", // 0
        "com.android.gboard.theme.light", // 1
    };

    // Dark themes
    private static final String[] DARK_THEMES = {
        "com.android.system.theme.dark", // 0
        "com.android.systemui.theme.custom.dark", // 1
        "com.android.settings.theme.dark", // 2
        "com.android.settings.intelligence.theme.dark", // 3
        "com.android.gboard.theme.dark", // 4
        "com.google.intelligence.sense.theme.dark", // 5
        "com.android.updater.theme.dark", // 6
        "com.android.wellbeing.theme.dark", // 7
    };

    // BlackAF themes
    private static final String[] BLACKAF_THEMES = {
        "com.android.system.theme.blackaf", // 0
        "com.android.systemui.theme.custom.blackaf", // 1
        "com.android.settings.theme.blackaf", // 2
        "com.android.settings.intelligence.theme.blackaf", // 3
        "com.android.gboard.theme.blackaf", // 4
        "com.google.intelligence.sense.theme.blackaf", // 5
        "com.android.updater.theme.blackaf", // 6
        "com.android.wellbeing.theme.blackaf", // 7
    };

    // Unloads the stock dark theme
    public static void unloadStockDarkTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(STOCK_DARK_THEME,
                    userId);
            if (themeInfo != null && themeInfo.isEnabled()) {
                om.setEnabled(STOCK_DARK_THEME,
                        false /*disable*/, userId);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    // Check for the dark system theme
    public static boolean isUsingDarkTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(DARK_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }

    // Check for the blackaf system theme
    public static boolean isUsingBlackAFTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(BLACKAF_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }

    // Set light / dark theme
    public static void setLightDarkTheme(IOverlayManager om, int userId, boolean useDarkTheme) {
        for (String theme : DARK_THEMES) {
            try {
                om.setEnabled(theme,
                        useDarkTheme, userId);
                if (useDarkTheme) {
                    unloadStockDarkTheme(om, userId);
                }
            } catch (RemoteException e) {
            }
        }
        for (String theme : LIGHT_THEMES) {
            try {
                om.setEnabled(theme,
                        !useDarkTheme, userId);
            } catch (RemoteException e) {
            }
        }
    }

    // Set black theme
    public static void setLightBlackAFTheme(IOverlayManager om, int userId, boolean useBlackAFTheme) {
        for (String theme : BLACKAF_THEMES) {
            try {
                om.setEnabled(theme,
                        useBlackAFTheme, userId);
                if (useBlackAFTheme) {
                    unloadStockDarkTheme(om, userId);
                }
            } catch (RemoteException e) {
            }
        }
    }
}
