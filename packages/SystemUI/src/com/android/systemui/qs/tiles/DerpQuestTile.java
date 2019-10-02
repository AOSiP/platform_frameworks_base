/*
 * Copyright (C) 2019 DerpFest
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

import javax.inject.Inject;

public class DerpQuestTile extends QSTileImpl<BooleanState> {
    private boolean mListening;
    private final ActivityStarter mActivityStarter;

    private static final String TAG = "DerpQuestTile";

    private static final String DERP_SETTINGS_PKG = "com.android.settings";
    private static final Intent DERP_SETTINGS = new Intent()
        .setComponent(new ComponentName(DERP_SETTINGS_PKG,
        "com.android.settings.Settings$DerpQuestSettingsActivity"));

    @Inject
    public DerpQuestTile(QSHost host) {
        super(host);
        mActivityStarter = Dependency.get(ActivityStarter.class);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.OWLSNEST;
    }

    @Override
    protected void handleClick() {
        mHost.collapsePanels();
        startDERPSettings();
        refreshState();
    }

    @Override
    protected void handleSecondaryClick() {
        handleClick();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick() {
        mHost.collapsePanels();
        startDERPSettings();
        refreshState();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_derp_label);
    }

    protected void startDERPSettings() {
        mActivityStarter.postStartActivityDismissingKeyguard(DERP_SETTINGS, 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_derp);
        state.label = mContext.getString(R.string.quick_settings_derp_label);
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
