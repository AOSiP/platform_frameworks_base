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

package android.content;

import static org.junit.Assert.assertEquals;

import android.app.ActivityThread;
import android.os.UserHandle;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextTest {
    @Test
    public void testDisplayIdForSystemContext() {
        final Context systemContext =
                ActivityThread.currentActivityThread().getSystemContext();

        assertEquals(systemContext.getDisplay().getDisplayId(), systemContext.getDisplayId());
    }

    @Test
    public void testDisplayIdForTestContext() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals(testContext.getDisplay().getDisplayId(), testContext.getDisplayId());
    }

    @Test
    public void testDisplayIdForDefaultDisplayContext() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        final WindowManager wm = testContext.getSystemService(WindowManager.class);
        final Context defaultDisplayContext =
                testContext.createDisplayContext(wm.getDefaultDisplay());

        assertEquals(defaultDisplayContext.getDisplay().getDisplayId(),
                defaultDisplayContext.getDisplayId());
    }

    @Test(expected = NullPointerException.class)
    public void testStartActivityAsUserNullIntentNullUser() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext.startActivityAsUser(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testStartActivityAsUserNullIntentNonNullUser() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext.startActivityAsUser(null, new UserHandle(UserHandle.USER_ALL));
    }

    @Test(expected = NullPointerException.class)
    public void testStartActivityAsUserNonNullIntentNullUser() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext.startActivityAsUser(new Intent(), null);
    }

    @Test(expected = RuntimeException.class)
    public void testStartActivityAsUserNonNullIntentNonNullUser() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext.startActivityAsUser(new Intent(), new UserHandle(UserHandle.USER_ALL));
    }
}
