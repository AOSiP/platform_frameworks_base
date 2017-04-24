/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.recents;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Rect;

import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.recents.misc.SystemServicesProxy;

/**
 * Application resources that can be retrieved from the application context and are not specifically
 * tied to the current activity.
 */
public class RecentsConfiguration {

    private static final int LARGE_SCREEN_MIN_DP = 600;
    private static final int XLARGE_SCREEN_MIN_DP = 720;

    /** Levels of svelte in increasing severity/austerity. */
    // No svelting.
    public static final int SVELTE_NONE = 0;
    // Limit thumbnail cache to number of visible thumbnails when Recents was loaded, disable
    // caching thumbnails as you scroll.
    public static final int SVELTE_LIMIT_CACHE = 1;
    // Disable the thumbnail cache, load thumbnails asynchronously when the activity loads and
    // evict all thumbnails when hidden.
    public static final int SVELTE_DISABLE_CACHE = 2;
    // Disable all thumbnail loading.
    public static final int SVELTE_DISABLE_LOADING = 3;

    // Launch states
    public RecentsActivityLaunchState mLaunchState = new RecentsActivityLaunchState();

    // Since the positions in Recents has to be calculated globally (before the RecentsActivity
    // starts), we need to calculate some resource values ourselves, instead of relying on framework
    // resources.
    public final boolean isLargeScreen;
    public final boolean isXLargeScreen;
    public final int smallestWidth;

    /** Misc **/
    public boolean fakeShadows;
    public int svelteLevel;
    private Context mContext;

    // Whether this product supports Grid-based Recents. If this is field is set to true, then
    // Recents will layout task views in a grid mode when there's enough space in the screen.
    public int fabEnterAnimDuration;
    public int fabEnterAnimDelay;
    public int fabExitAnimDuration;
    private boolean isGridEnabledDefault;
    private boolean mIsGridEnabled;
    private Handler mHandler = new Handler();
    private SettingsObserver mSettingsObserver;

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_RECENTS),
                    false, this);
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            mIsGridEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.NAVIGATION_BAR_RECENTS, isGridEnabledDefault ? 1 : 0,
                    UserHandle.USER_CURRENT) == 1;
        }
    }

    public RecentsConfiguration(Context context) {
        // Load only resources that can not change after the first load either through developer
        // settings or via multi window
        SystemServicesProxy ssp = Recents.getSystemServices();
        Context appContext = context.getApplicationContext();
        mContext = appContext;
        Resources res = appContext.getResources();
        fakeShadows = res.getBoolean(R.bool.config_recents_fake_shadows);
        svelteLevel = res.getInteger(R.integer.recents_svelte_level);
        isGridEnabledDefault = SystemProperties.getBoolean("ro.recents.grid", false);

        float screenDensity = context.getResources().getDisplayMetrics().density;
        smallestWidth = ssp.getDeviceSmallestWidth();
        isLargeScreen = smallestWidth >= (int) (screenDensity * LARGE_SCREEN_MIN_DP);
        isXLargeScreen = smallestWidth >= (int) (screenDensity * XLARGE_SCREEN_MIN_DP);

        fabEnterAnimDuration =
                res.getInteger(R.integer.recents_animate_fab_enter_duration);
        fabEnterAnimDelay =
                res.getInteger(R.integer.recents_animate_fab_enter_delay);
        fabExitAnimDuration =
                res.getInteger(R.integer.recents_animate_fab_exit_duration);

        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
    }

    /**
     * Returns the activity launch state.
     * TODO: This will be refactored out of RecentsConfiguration.
     */
    public RecentsActivityLaunchState getLaunchState() {
        return mLaunchState;
    }

    public boolean isGridEnabled() {
        return mIsGridEnabled;
    }
}
