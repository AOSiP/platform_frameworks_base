/*
 * Copyright (C) 2016 The Nitrogen Project
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

import android.os.Bundle;
import androidx.preference.PreferenceFragment;
import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerActivity;
import com.android.systemui.R;

public class LockscreenActivity extends TunerActivity {

    private static final String TAG_TUNER = "tuner";

    protected void onCreate(Bundle savedInstanceState) {
        Dependency.initDependencies(this);
        super.onCreate(savedInstanceState);

        if (getFragmentManager().findFragmentByTag(TAG_TUNER) == null) {
            final String action = getIntent().getAction();
            final PreferenceFragment fragment = new LockscreenFragment();
            getFragmentManager().beginTransaction().replace(R.id.content_frame,
                    fragment, TAG_TUNER).commit();
        }
    }
}