/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.frameworks.perftests.am.tests;

import android.content.Intent;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.frameworks.perftests.am.util.Constants;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BroadcastPerfTest extends BasePerfTest {
    @Test
    public void manifestBroadcastRunning() {
        runPerfFunction(() -> {
            startTargetPackage();

            final Intent intent = createBroadcastIntent(
                    Constants.ACTION_BROADCAST_MANIFEST_RECEIVE);

            final long startTime = System.nanoTime();

            mContext.sendBroadcast(intent);

            final long endTime = getReceivedTimeNs(Constants.TYPE_BROADCAST_RECEIVE);

            return endTime - startTime;
        });
    }

    @Test
    public void manifestBroadcastNotRunning() {
        runPerfFunction(() -> {
            final Intent intent = createBroadcastIntent(
                    Constants.ACTION_BROADCAST_MANIFEST_RECEIVE);

            final long startTime = System.nanoTime();

            mContext.sendBroadcast(intent);

            final long endTime = getReceivedTimeNs(Constants.TYPE_BROADCAST_RECEIVE);

            return endTime - startTime;
        });
    }

    @Test
    public void registeredBroadcast() {
        runPerfFunction(() -> {
            startTargetPackage();

            final Intent intent = createBroadcastIntent(
                    Constants.ACTION_BROADCAST_REGISTERED_RECEIVE);

            final long startTime = System.nanoTime();

            mContext.sendBroadcast(intent);

            final long endTime = getReceivedTimeNs(Constants.TYPE_BROADCAST_RECEIVE);

            return endTime - startTime;
        });
    }
}
