/**
 * Copyright (C) 2020 AquariOS
 *
 * @author Randall Rushing <randall.rushing@gmail.com>
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
 *
 * Interface for hardware navigation button handling
 *
 */

package com.android.internal.os;

import android.view.WindowManager;

public interface HardwareKeyHandler {
    // messages to PWM to do some actions we can't really do here
    public static final int MSG_FIRE_HOME = 7102;
    public static final int MSG_UPDATE_MENU_KEY = 7106;
    public static final int MSG_DO_HAPTIC_FB = 7107;

    public boolean handleKeyEvent(WindowManager.LayoutParams attrs, int keyCode,
            int repeatCount,
            boolean down,
            boolean canceled,
            boolean longPress, boolean keyguardOn);
}
