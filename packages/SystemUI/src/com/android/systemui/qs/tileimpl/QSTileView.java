/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.tileimpl;

import android.content.Context;
import android.content.res.Configuration;
import android.service.quicksettings.Tile;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile;

import java.util.Objects;

/** View that represents a standard quick settings tile. **/
public class QSTileView extends QSTileBaseView {
    private static final int MAX_LABEL_LINES = 2;
    private static final boolean DUAL_TARGET_ALLOWED = false;
    private View mDivider;
    protected TextView mLabel;
    protected TextView mSecondLine;
    private ImageView mPadLock;
    private int mState;
    private ViewGroup mLabelContainer;
    private View mExpandIndicator;
    private View mExpandSpace;
    private boolean mHideEpxand;
    private boolean mDualTarget;

    public QSTileView(Context context, QSIconView icon) {
        this(context, icon, false);
    }

    public QSTileView(Context context, QSIconView icon, boolean collapsedView) {
        super(context, icon, collapsedView);

        setClipChildren(false);
        setClipToPadding(false);

        setClickable(true);
        setId(View.generateViewId());
        createLabel();
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
    }

    TextView getLabel() {
        return mLabel;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(mLabel, R.dimen.qs_tile_text_size);
        FontSizeUtils.updateFontSize(mSecondLine, R.dimen.qs_tile_text_size);
    }

    @Override
    public int getDetailY() {
        return getTop() + mLabelContainer.getTop() + mLabelContainer.getHeight() / 2;
    }

    protected void createLabel() {
        mLabelContainer = (ViewGroup) LayoutInflater.from(getContext())
                .inflate(R.layout.qs_tile_label, this, false);
        mLabelContainer.setClipChildren(false);
        mLabelContainer.setClipToPadding(false);
        mLabel = mLabelContainer.findViewById(R.id.tile_label);
        mPadLock = mLabelContainer.findViewById(R.id.restricted_padlock);
        mDivider = mLabelContainer.findViewById(R.id.underline);
        mExpandIndicator = mLabelContainer.findViewById(R.id.expand_indicator);
        mExpandSpace = mLabelContainer.findViewById(R.id.expand_space);
        mSecondLine = mLabelContainer.findViewById(R.id.app_label);
        addView(mLabelContainer);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Remeasure view if the primary label requires more then 2 lines or the secondary label
        // text will be cut off.
        if (mLabel.getLineCount() > MAX_LABEL_LINES || !TextUtils.isEmpty(mSecondLine.getText())
                        && mSecondLine.getLineHeight() > mSecondLine.getHeight()) {
            mLabel.setSingleLine();
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void handleStateChanged(QSTile.State state) {
        super.handleStateChanged(state);
        if (!Objects.equals(mLabel.getText(), state.label) || mState != state.state) {
            if (state.state == Tile.STATE_UNAVAILABLE) {
                int color = QSTileImpl.getColorForState(getContext(), state.state);
                state.label = new SpannableStringBuilder().append(state.label,
                        new ForegroundColorSpan(color),
                        SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
            }
            mState = state.state;
            mLabel.setText(state.label);
        }
        if (!Objects.equals(mSecondLine.getText(), state.secondaryLabel)) {
            mSecondLine.setText(state.secondaryLabel);
            mSecondLine.setVisibility(TextUtils.isEmpty(state.secondaryLabel) ? View.GONE
                    : View.VISIBLE);
        }
        boolean dualTarget = DUAL_TARGET_ALLOWED && state.dualTarget;
        mDualTarget = dualTarget;
        mExpandIndicator.setVisibility((mDualTarget && !mHideEpxand) ? View.VISIBLE : View.GONE);
        mExpandSpace.setVisibility((mDualTarget && !mHideEpxand) ? View.VISIBLE : View.GONE);
        mLabelContainer.setContentDescription(mDualTarget ? state.dualLabelContentDescription
                : null);
        if (dualTarget != mLabelContainer.isClickable()) {
            mLabelContainer.setClickable(dualTarget);
            mLabelContainer.setLongClickable(dualTarget);
            mLabelContainer.setBackground(dualTarget ? newTileBackground() : null);
        }
        mLabel.setEnabled(!state.disabledByPolicy);
        mPadLock.setVisibility(state.disabledByPolicy ? View.VISIBLE : View.GONE);
    }

    @Override
    public void init(OnClickListener click, OnClickListener secondaryClick,
            OnLongClickListener longClick) {
        super.init(click, secondaryClick, longClick);
        mLabelContainer.setOnClickListener(secondaryClick);
        mLabelContainer.setOnLongClickListener(longClick);
        mLabelContainer.setClickable(false);
        mLabelContainer.setLongClickable(false);
    }

    public void setHideExpand(boolean value) {
        mHideEpxand = value;
        mExpandIndicator.setVisibility((mDualTarget && !mHideEpxand) ? View.VISIBLE : View.GONE);
        mExpandSpace.setVisibility((mDualTarget && !mHideEpxand) ? View.VISIBLE : View.GONE);
    }

    public void setHideLabel(boolean value) {
        mLabelContainer.setVisibility(value ? View.GONE : View.VISIBLE);
    }
}
