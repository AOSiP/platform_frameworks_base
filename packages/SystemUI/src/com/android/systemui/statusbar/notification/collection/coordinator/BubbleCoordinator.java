/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.collection.coordinator;

import static android.service.notification.NotificationStats.DISMISSAL_OTHER;
import static android.service.notification.NotificationStats.DISMISS_SENTIMENT_NEUTRAL;

import com.android.internal.statusbar.NotificationVisibility;
import com.android.systemui.bubbles.BubbleController;
import com.android.systemui.statusbar.notification.collection.NotifCollection;
import com.android.systemui.statusbar.notification.collection.NotifPipeline;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter;
import com.android.systemui.statusbar.notification.collection.notifcollection.DismissedByUserStats;
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifDismissInterceptor;
import com.android.systemui.statusbar.notification.logging.NotificationLogger;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Coordinates hiding, intercepting (the dismissal), and deletion of bubbled notifications.
 *
 * The typical "start state" for a bubbled notification is when a bubble-able notification is
 * posted. It is visible as a bubble AND as a notification in the shade. From here, we can get
 * into a few hidden-from-shade states described below:
 *
 * Start State -> Hidden from shade
 * User expands the bubble so we hide its notification from the shade.
 * OR
 * User dismisses a group summary with a bubbled child. All bubbled children are now hidden from
 * the shade. And the group summary's dismissal is intercepted + hidden from the shade (see below).
 *
 * Start State -> Dismissal intercepted + hidden from shade
 * User dismisses the notification from the shade. We now hide the notification from the shade
 * and intercept its dismissal (the removal signal is never sent to system server). We
 * keep the notification alive in system server so that {@link BubbleController} can still
 * respond to app-cancellations (ie: remove the bubble if the app cancels the notification).
 *
 */
@Singleton
public class BubbleCoordinator implements Coordinator {
    private static final String TAG = "BubbleCoordinator";

    private final BubbleController mBubbleController;
    private final NotifCollection mNotifCollection;
    private final Set<String> mInterceptedDismissalEntries = new HashSet<>();
    private NotifPipeline mNotifPipeline;
    private NotifDismissInterceptor.OnEndDismissInterception mOnEndDismissInterception;

    @Inject
    public BubbleCoordinator(
            BubbleController bubbleController,
            NotifCollection notifCollection) {
        mBubbleController = bubbleController;
        mNotifCollection = notifCollection;
    }

    @Override
    public void attach(NotifPipeline pipeline) {
        mNotifPipeline = pipeline;
        mNotifPipeline.addNotificationDismissInterceptor(mDismissInterceptor);
        mNotifPipeline.addFinalizeFilter(mNotifFilter);
        mBubbleController.addNotifCallback(mNotifCallback);
    }

    private final NotifFilter mNotifFilter = new NotifFilter(TAG) {
        @Override
        public boolean shouldFilterOut(NotificationEntry entry, long now) {
            return mBubbleController.isBubbleNotificationSuppressedFromShade(entry);
        }
    };

    private final NotifDismissInterceptor mDismissInterceptor = new NotifDismissInterceptor() {
        @Override
        public String getName() {
            return TAG;
        }

        @Override
        public void setCallback(OnEndDismissInterception callback) {
            mOnEndDismissInterception = callback;
        }

        @Override
        public boolean shouldInterceptDismissal(NotificationEntry entry) {
            // TODO: b/149041810 add support for intercepting app-cancelled bubble notifications
            // for experimental bubbles
            if (mBubbleController.handleDismissalInterception(entry)) {
                mInterceptedDismissalEntries.add(entry.getKey());
                return true;
            } else {
                mInterceptedDismissalEntries.remove(entry.getKey());
                return false;
            }
        }

        @Override
        public void cancelDismissInterception(NotificationEntry entry) {
            mInterceptedDismissalEntries.remove(entry.getKey());
        }
    };

    private final BubbleController.NotifCallback mNotifCallback =
            new BubbleController.NotifCallback() {
        @Override
        public void removeNotification(NotificationEntry entry, int reason) {
            if (isInterceptingDismissal(entry)) {
                mInterceptedDismissalEntries.remove(entry.getKey());
                mOnEndDismissInterception.onEndDismissInterception(mDismissInterceptor, entry,
                        createDismissedByUserStats(entry));
            } else if (mNotifPipeline.getAllNotifs().contains(entry)) {
                // Bubbles are hiding the notifications from the shade, but the bubble was
                // deleted; therefore, the notification should be cancelled as if it were a user
                // dismissal (this won't re-enter handleInterceptDimissal because Bubbles
                // will have already marked it as no longer a bubble)
                mNotifCollection.dismissNotification(entry, createDismissedByUserStats(entry));
            }
        }

        @Override
        public void invalidateNotifications(String reason) {
            mNotifFilter.invalidateList();
        }

        @Override
        public void maybeCancelSummary(NotificationEntry entry) {
            // no-op
        }
    };

    private boolean isInterceptingDismissal(NotificationEntry entry) {
        return mInterceptedDismissalEntries.contains(entry.getKey());
    }

    private DismissedByUserStats createDismissedByUserStats(NotificationEntry entry) {
        return new DismissedByUserStats(
                DISMISSAL_OTHER,
                DISMISS_SENTIMENT_NEUTRAL,
                NotificationVisibility.obtain(entry.getKey(),
                        entry.getRanking().getRank(),
                        mNotifPipeline.getShadeListCount(),
                        true, // was visible as a bubble
                        NotificationLogger.getNotificationLocation(entry))
        );
    }
}
