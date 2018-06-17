/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2012-2015 The CyanogenMod Project
 * Copyright (C) 2014-2015 The Euphoria-OS Project
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

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.screenshot.TakeScreenshotService;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

/** Quick settings tile: Screenshot **/
public class ScreenshotTile extends QSTileImpl<BooleanState> {

    private boolean mRegion;

    private boolean mListening;
    private final Object mScreenshotLock = new Object();
    private ServiceConnection mScreenshotConnection = null;

    public ScreenshotTile(QSHost host) {
        super(host);
        mRegion = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREENSHOT_DEFAULT_MODE, 0, UserHandle.USER_CURRENT) == 1;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

    @Override
    protected void handleClick() {
        mRegion = !mRegion;
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.SCREENSHOT_DEFAULT_MODE, mRegion ? 1 : 0,
                UserHandle.USER_CURRENT);
        refreshState();
    }

    @Override
    public void handleLongClick() {
        mHost.collapsePanels();
        /* wait for the panel to close */
        try {
             Thread.sleep(1000);
        } catch (InterruptedException ie) {
             // Do nothing
        }
        Intent intent = new Intent(Intent.ACTION_SCREENSHOT);
        mContext.sendBroadcast(intent);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mRegion) {
            state.label = mContext.getString(R.string.quick_settings_region_screenshot_label);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_region_screenshot);
            state.contentDescription =  mContext.getString(
                    R.string.quick_settings_region_screenshot_label);
        } else {
            state.label = mContext.getString(R.string.quick_settings_screenshot_label);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_screenshot);
            state.contentDescription =  mContext.getString(
                    R.string.quick_settings_screenshot_label);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_screenshot_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.OWL_TILE;
    }
}
