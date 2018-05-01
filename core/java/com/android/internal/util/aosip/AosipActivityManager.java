package com.android.internal.util.aosip;

import android.content.Context;

public class AosipActivityManager {
    private Context mContext;

    // Long screen related activity settings
    private LongScreen mLongScreen;

    public AosipActivityManager(Context context) {
        mContext = context;

        mLongScreen = new LongScreen(context);
    }

    public boolean shouldForceLongScreen(String packageName) {
        return mLongScreen.shouldForceLongScreen(packageName);
    }
}