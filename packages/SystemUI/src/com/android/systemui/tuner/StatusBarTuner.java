/*
 * Copyright (C) 2017 The LineageOS Project
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

import android.content.Context;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.view.MenuItem;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;

public class StatusBarTuner extends PreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String SHOW_FOURG = "show_fourg";
    private static final String SHOW_VOLTE = "show_volte";
    private static final String DATA_DISABLED_ICON = "data_disabled_icon";
    private static final String USE_OLD_MOBILETYPE = "use_old_mobiletype";
    private static final String VOLTE_ICON = "volte_icon_style";

    private SwitchPreference mShowFourG;
    private SwitchPreference mShowVoLTE;
    private SwitchPreference mShowDataDisabled;
    private SwitchPreference mUseOldMobileType;
    private ListPreference mVoLTEIcon;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        mShowFourG = (SwitchPreference) findPreference(SHOW_FOURG);
        mShowVoLTE = (SwitchPreference) findPreference(SHOW_VOLTE);
        mShowDataDisabled = (SwitchPreference) findPreference(DATA_DISABLED_ICON);
        mUseOldMobileType = (SwitchPreference) findPreference(USE_OLD_MOBILETYPE);
        mVoLTEIcon = (ListPreference) findPreference(VOLTE_ICON);
        if (isWifiOnly()) {
            getPreferenceScreen().removePreference(mShowFourG);
            getPreferenceScreen().removePreference(mShowVoLTE);
            getPreferenceScreen().removePreference(mShowDataDisabled);
            getPreferenceScreen().removePreference(mUseOldMobileType);
            getPreferenceScreen().removePreference(mVoLTEIcon);
        } else {
            mShowFourG.setChecked(Settings.System.getIntForUser(getActivity().getContentResolver(),
                Settings.System.SHOW_FOURG, get4gForLTEDefaultBool() ? 1 : 0,
                UserHandle.USER_CURRENT) == 1);
            mShowVoLTE.setChecked(Settings.System.getIntForUser(getActivity().getContentResolver(),
                Settings.System.SHOW_VOLTE_ICON, 0,
                UserHandle.USER_CURRENT) == 1);
            mShowDataDisabled.setChecked(Settings.System.getIntForUser(getActivity().getContentResolver(),
                Settings.System.DATA_DISABLED_ICON, 1,
                UserHandle.USER_CURRENT) == 1);
            mUseOldMobileType.setChecked(Settings.System.getIntForUser(getActivity().getContentResolver(),
                Settings.System.USE_OLD_MOBILETYPE, 0,
                UserHandle.USER_CURRENT) == 1);
            mVoLTEIcon.setValue(Settings.System.getStringForUser(getActivity().getContentResolver(),
                Settings.System.VOLTE_ICON_STYLE, UserHandle.USER_CURRENT));
            mVoLTEIcon.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.status_bar_prefs);
    }

    @Override
    public void onResume() {
        super.onResume();
        MetricsLogger.visibility(getContext(), MetricsEvent.TUNER, true);
    }

    @Override
    public void onPause() {
        super.onPause();
        MetricsLogger.visibility(getContext(), MetricsEvent.TUNER, false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mShowFourG) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SHOW_FOURG, checked ? 1 : 0);
            return true;
        } else if (preference == mShowVoLTE) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SHOW_VOLTE_ICON, checked ? 1 : 0);
            return true;
        } else if (preference == mShowDataDisabled) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.DATA_DISABLED_ICON, checked ? 1 : 0);
            return true;
        } else if (preference == mUseOldMobileType) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.USE_OLD_MOBILETYPE, checked ? 1 : 0);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mVoLTEIcon) {
          int value = Integer.parseInt((String) objValue);
          Settings.System.putInt(getActivity().getContentResolver(),
                  Settings.System.VOLTE_ICON_STYLE, value);
          return true;
        }
        return false;
    }

    private boolean isWifiOnly() {
        ConnectivityManager cm = (ConnectivityManager)getActivity().getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return (cm != null && cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) == false);
    }

    private boolean get4gForLTEDefaultBool() {
            CarrierConfigManager configMgr = (CarrierConfigManager) getContext().getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
            final int defaultDataSubId = SubscriptionManager.from(getContext())
                .getDefaultDataSubscriptionId();
            PersistableBundle b = configMgr.getConfigForSubId(defaultDataSubId);

            return b.getBoolean(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL);
    }
}
