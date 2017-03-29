/**
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settingslib.drawer;

import java.util.HashMap;
import java.util.Map;

public final class CategoryKey {

    // Activities in this category shows up in Settings homepage.
    public static final String CATEGORY_HOMEPAGE = "com.android.settings.category.ia.homepage";

    // Top level category.
    public static final String CATEGORY_NETWORK = "com.android.settings.category.ia.wireless";
    public static final String CATEGORY_DEVICE = "com.android.settings.category.ia.device";
    public static final String CATEGORY_APPS = "com.android.settings.category.ia.apps";
    public static final String CATEGORY_APPS_DEFAULT =
            "com.android.settings.category.ia.apps.default";
    public static final String CATEGORY_BATTERY = "com.android.settings.category.ia.battery";
    public static final String CATEGORY_DISPLAY = "com.android.settings.category.ia.display";
    public static final String CATEGORY_SOUND = "com.android.settings.category.ia.sound";
    public static final String CATEGORY_STORAGE = "com.android.settings.category.ia.storage";
    public static final String CATEGORY_SECURITY = "com.android.settings.category.ia.security";
    public static final String CATEGORY_ACCOUNT = "com.android.settings.category.ia.accounts";
    public static final String CATEGORY_SYSTEM = "com.android.settings.category.ia.system";
    public static final String CATEGORY_SYSTEM_LANGUAGE =
            "com.android.settings.category.ia.language";
    public static final String CATEGORY_SYSTEM_DEVELOPMENT =
            "com.android.settings.category.ia.development";
    public static final String CATEGORY_NOTIFICATIONS =
            "com.android.settings.category.ia.notifications";

    public static final String CATEGORY_NEST = "com.android.settings.category.ia.nest";
    public static final String CATEGORY_GESTURES = "com.android.settings.category.ia.nest";

    public static final Map<String, String> KEY_COMPAT_MAP;

    static {
        KEY_COMPAT_MAP = new HashMap<>();
        KEY_COMPAT_MAP.put("com.android.settings.category.wireless", CATEGORY_NETWORK);
        KEY_COMPAT_MAP.put("com.android.settings.category.device", CATEGORY_SYSTEM);
        KEY_COMPAT_MAP.put("com.android.settings.category.personal", CATEGORY_SYSTEM);
        KEY_COMPAT_MAP.put("com.android.settings.category.system", CATEGORY_SYSTEM);
    }
}
