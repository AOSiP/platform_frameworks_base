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
 * limitations under the License.
 */
package com.android.systemui.tuner;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.internal.hardware.AmbientDisplayConfiguration;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.plugins.PluginPrefs;

public class StatusbarIconsFragment extends TunerFragment {

    private static final String TAG = "StatusbarIconsFragment";
    private static final String NFC_KEY = "nfc";

    private StatusBarSwitch mNfcSwitch;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.statusbar_icon_settings);
        final PreferenceScreen prefScreen = getPreferenceScreen();
        final PackageManager pm = getActivity().getApplicationContext().getPackageManager();
        mNfcSwitch = (StatusBarSwitch) findPreference(NFC_KEY);
        final boolean isNfcAvailable = pm.hasSystemFeature(PackageManager.FEATURE_NFC);

        if (!isNfcAvailable) {
            prefScreen.removePreference(mNfcSwitch);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(R.string.statusbar_icons_blacklist);

        MetricsLogger.visibility(getContext(), MetricsEvent.TUNER, true);
    }
}
