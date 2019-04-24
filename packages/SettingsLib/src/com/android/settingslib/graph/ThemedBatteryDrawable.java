package com.android.settingslib.graph;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Path.Op;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.PathParser;
import android.util.TypedValue;

import com.android.settingslib.R;
import com.android.settingslib.Utils;

public class ThemedBatteryDrawable extends BatteryMeterDrawableBase {
    private int backgroundColor = 0xFFFF00FF;
    private final Path boltPath = new Path();
    private boolean charging;
    private int[] colorLevels;
    private final Context context;
    private int criticalLevel;
    private boolean dualTone;
    private int fillColor = 0xFFFF00FF;
    private final Path fillMask = new Path();
    private final RectF fillRect = new RectF();
    private int intrinsicHeight;
    private int intrinsicWidth;
    private boolean invertFillIcon;
    private int levelColor = 0xFFFF00FF;
    private final Path levelPath = new Path();
    private final RectF levelRect = new RectF();
    private final Rect padding = new Rect();
    private final Path perimeterPath = new Path();
    private final Path plusPath = new Path();
    private boolean powerSaveEnabled;
    private final Matrix scaleMatrix = new Matrix();
    private final Path scaledBolt = new Path();
    private final Path scaledFill = new Path();
    private final Path scaledPerimeter = new Path();
    private final Path scaledPlus = new Path();
    private final Path unifiedPath = new Path();

    private final Paint mDualToneBackgroundFill;
    private final Paint mFillColorStrokePaint;
    private final Paint mFillColorStrokeProtection;
    private final Paint mFillPaint;

    private final float mWidthDp = 12.6f;
    private final float mHeightDp = 21f;

    private int mMeterStyle;

    private final Paint getDualToneBackgroundFill() {
        return mDualToneBackgroundFill;
    }

    private final Paint getFillColorStrokePaint() {
        return mFillColorStrokePaint;
    }

    private final Paint getFillColorStrokeProtection() {
        return mFillColorStrokeProtection;
    }

    private final Paint getFillPaint() {
        return mFillPaint;
    }

    public int getOpacity() {
        return -1;
    }

    public void setAlpha(int i) {
    }

    public ThemedBatteryDrawable(Context context, int frameColor) {
        super(context, frameColor);

        this.context = context;
        float f = this.context.getResources().getDisplayMetrics().density;
        this.intrinsicHeight = (int) (mHeightDp * f);
        this.intrinsicWidth = (int) (mWidthDp * f);
        Resources res = this.context.getResources();

        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        final int N = levels.length();
        colorLevels = new int[2 * N];
        for (int i = 0; i < N; i++) {
            colorLevels[2 * i] = levels.getInt(i, 0);
            if (colors.getType(i) == TypedValue.TYPE_ATTRIBUTE) {
                colorLevels[2 * i + 1] = Utils.getColorAttr(context, colors.getThemeAttributeId(i, 0));
            } else {
                colorLevels[2 * i + 1] = colors.getColor(i, 0);
            }
        }
        levels.recycle();
        colors.recycle();
        
        setCriticalLevel(res.getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel));

        mDualToneBackgroundFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDualToneBackgroundFill.setColor(frameColor);
        mDualToneBackgroundFill.setAlpha(255);
        mDualToneBackgroundFill.setDither(true);
        mDualToneBackgroundFill.setStrokeWidth(0f);
        mDualToneBackgroundFill.setStyle(Style.FILL_AND_STROKE);

        mFillColorStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillColorStrokePaint.setColor(frameColor);
        mFillColorStrokePaint.setDither(true);
        mFillColorStrokePaint.setStrokeWidth(5f);
        mFillColorStrokePaint.setStyle(Style.STROKE);
        mFillColorStrokePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        mFillColorStrokePaint.setStrokeMiter(5f);

        mFillColorStrokeProtection = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillColorStrokeProtection.setDither(true);
        mFillColorStrokeProtection.setStrokeWidth(5f);
        mFillColorStrokeProtection.setStyle(Style.STROKE);
        mFillColorStrokeProtection.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mFillColorStrokeProtection.setStrokeMiter(5f);

        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setColor(frameColor);
        mFillPaint.setAlpha(255);
        mFillPaint.setDither(true);
        mFillPaint.setStrokeWidth(0f);
        mFillPaint.setStyle(Style.FILL_AND_STROKE);

        loadPaths();
    }

    public void setCriticalLevel(int i) {
        this.criticalLevel = i;
    }

    public final void setCharging(boolean charging) {
        this.charging = charging;
        super.setCharging(charging);
    }

    public boolean getCharging() {
        return this.charging;
    }

    public final boolean getPowerSaveEnabled() {
        return this.powerSaveEnabled;
    }

    public final void setPowerSaveEnabled(boolean enabled) {
        this.powerSaveEnabled = enabled;
        super.setPowerSave(enabled);
    }

    public void setShowPercent(boolean show) {
        
    }

    public void draw(Canvas canvas) {
        if (getMeterStyle() != BATTERY_STYLE_PORTRAIT) {
            super.draw(canvas);
            return;
        }
        float f;
        String str = "c";
        this.unifiedPath.reset();
        this.levelPath.reset();
        this.levelRect.set(this.fillRect);
        float level = ((float) getLevel()) / 100.0f;
        if (getLevel() >= 95) {
            f = this.fillRect.top;
        } else {
            RectF rectF = this.fillRect;
            f = (rectF.height() * (((float) 1) - level)) + rectF.top;
        }
        this.levelRect.top = (float) Math.floor((double) f);
        this.levelPath.addRect(this.levelRect, Direction.CCW);
        this.unifiedPath.addPath(this.scaledPerimeter);
        if (!this.dualTone) {
            this.unifiedPath.op(this.levelPath, Op.UNION);
        }
        getFillPaint().setColor(this.levelColor);
        if (this.charging) {
            this.unifiedPath.op(this.scaledBolt, Op.DIFFERENCE);
            if (!this.invertFillIcon) {
                canvas.drawPath(this.scaledBolt, getFillPaint());
            }
        } else if (this.powerSaveEnabled) {
            this.unifiedPath.op(this.scaledPlus, Op.DIFFERENCE);
            if (!this.invertFillIcon) {
                canvas.drawPath(this.scaledPlus, getFillPaint());
            }
        }
        if (this.dualTone) {
            canvas.drawPath(this.unifiedPath, getDualToneBackgroundFill());
            canvas.save();
            canvas.clipRect(0.0f, ((float) getBounds().bottom) - (((float) getBounds().height()) * level), (float) getBounds().right, (float) getBounds().bottom);
            canvas.drawPath(this.unifiedPath, getFillPaint());
            canvas.restore();
        } else {
            getFillPaint().setColor(this.fillColor);
            canvas.drawPath(this.unifiedPath, getFillPaint());
            getFillPaint().setColor(this.levelColor);
            if (getLevel() <= 15 && !this.charging) {
                canvas.save();
                canvas.clipPath(this.scaledFill);
                canvas.drawPath(this.levelPath, getFillPaint());
                canvas.restore();
            }
        }
        if (this.charging) {
            canvas.clipOutPath(this.scaledBolt);
            if (this.invertFillIcon) {
                canvas.drawPath(this.scaledBolt, getFillColorStrokePaint());
            } else {
                canvas.drawPath(this.scaledBolt, getFillColorStrokeProtection());
            }
        } else if (this.powerSaveEnabled) {
            canvas.clipOutPath(this.scaledPlus);
            if (this.invertFillIcon) {
                canvas.drawPath(this.scaledPlus, getFillColorStrokePaint());
            } else {
                canvas.drawPath(this.scaledPlus, getFillColorStrokeProtection());
            }
        }
    }

    public int getBatteryLevel() {
        return getLevel();
    }

    protected final int batteryColorForLevel(int i) {
        if (this.charging || this.powerSaveEnabled) {
            return getFillPaint().getColor();
        }
        return getColorForLevel(i);
    }

    private final int getColorForLevel(int i) {
        int i2 = 0;
        int i3 = 0;
        while (true) {
            int[] iArr = this.colorLevels;
            if (i2 >= iArr.length) {
                return i3;
            }
            i3 = iArr[i2];
            int i4 = iArr[i2 + 1];
            if (i <= i3) {
                if (i2 == iArr.length - 2) {
                    i4 = this.fillColor;
                }
                return i4;
            }
            i2 += 2;
            i3 = i4;
        }
    }

    public void setColorFilter(ColorFilter colorFilter) {
        getFillPaint().setColorFilter(colorFilter);
        getFillColorStrokePaint().setColorFilter(colorFilter);
        getDualToneBackgroundFill().setColorFilter(colorFilter);
    }

    public int getIntrinsicHeight() {
        if (getMeterStyle() == BATTERY_STYLE_PORTRAIT) {
            return this.intrinsicHeight;
        } else {
            return super.getIntrinsicHeight();
        }
    }

    public int getIntrinsicWidth() {
        if (getMeterStyle() == BATTERY_STYLE_PORTRAIT) {
            return this.intrinsicWidth;
        } else {
            return super.getIntrinsicWidth();
        }
    }

    public void setBatteryLevel(int val) {
        this.invertFillIcon = val >= 67 ? true : val <= 33 ? false : this.invertFillIcon;
        setLevel(val);
        this.levelColor = batteryColorForLevel(getLevel());
        super.setBatteryLevel(val);
    }

    protected void onBoundsChange(Rect rect) {
        super.onBoundsChange(rect);
        updateSize();
    }

    public void setColors(int fillColor, int backgroundColor, int singleToneColor) {
        this.fillColor = this.dualTone ? fillColor : singleToneColor;
        getFillPaint().setColor(this.fillColor);
        getFillColorStrokePaint().setColor(this.fillColor);
        this.backgroundColor = backgroundColor;
        getDualToneBackgroundFill().setColor(backgroundColor);
        super.setColors(fillColor, backgroundColor);
    }

    private final void updateSize() {
        Rect bounds = getBounds();
        String str = "b";
        if (bounds.isEmpty()) {
            this.scaleMatrix.setScale(1.0f, 1.0f);
        } else {
            this.scaleMatrix.setScale(((float) bounds.right) / mWidthDp, ((float) bounds.bottom) / mHeightDp);
        }
        this.perimeterPath.transform(this.scaleMatrix, this.scaledPerimeter);
        this.fillMask.transform(this.scaleMatrix, this.scaledFill);
        this.scaledFill.computeBounds(this.fillRect, true);
        this.boltPath.transform(this.scaleMatrix, this.scaledBolt);
        this.plusPath.transform(this.scaleMatrix, this.scaledPlus);
    }

    private final void loadPaths() {
        this.perimeterPath.set(PathParser.createPathFromPathData("M3.5,2 v0 H1.33 C0.6,2 0,2.6 0,3.33 V13v5.67 C0,19.4 0.6,20 1.33,20 h9.33 C11.4,20 12,19.4 12,18.67 V13V3.33 C12,2.6 11.4,2 10.67,2 H8.5 V0 H3.5 z M2,18v-7V4h8v9v5H2L2,18z"));
        this.perimeterPath.computeBounds(new RectF(), true);
        this.fillMask.set(PathParser.createPathFromPathData("M2,18 v-14 h8 v14 z"));
        this.fillMask.computeBounds(this.fillRect, true);
        this.boltPath.set(PathParser.createPathFromPathData("M5,16.8 V12 H3 L7,5.2 V10 h2 L5,16.8 z"));
        this.plusPath.set(PathParser.createPathFromPathData("M9,10l-2,0l0,-2l-2,0l0,2l-2,0l0,2l2,0l0,2l2,0l0,-2l2,0z"));
        this.dualTone = false;
    }
}