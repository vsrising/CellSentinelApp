package com.asun.cellsentinelapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Circular arc gauge displaying a signal metric value.
 * Arc sweeps 270° from lower-left (bad) to lower-right (good).
 * Fill color reflects signal quality level.
 */
public class SignalGaugeView extends View {

    private static final float START_ANGLE = 135f; // 7:30 position (lower-left)
    private static final float SWEEP_ANGLE = 270f; // ends at 1:30 (lower-right) via top

    private final Paint mBgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mValPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mUnitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLblPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String mLabel  = "RSRP";
    private String mUnit   = "dBm";
    private int    mMinVal = -140;
    private int    mMaxVal = -44;
    private int    mValue  = Integer.MAX_VALUE;
    private int    mArcColor = 0xFF888888;

    public SignalGaugeView(Context ctx) {
        super(ctx);
    }

    public SignalGaugeView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    /** Configure the gauge before first use. */
    public void configure(String label, String unit, int minVal, int maxVal) {
        mLabel = label;
        mUnit  = unit;
        mMinVal = minVal;
        mMaxVal = maxVal;
    }

    /** Update displayed value and fill color. Pass Integer.MAX_VALUE for N/A. */
    public void setValue(int value, int color) {
        mValue    = value;
        mArcColor = color;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Force square: height equals the resolved width
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w  = getWidth();
        float stroke = w * 0.10f;
        float margin = stroke / 2 + w * 0.04f;
        RectF oval = new RectF(margin, margin, w - margin, w - margin);

        // Background arc
        mBgPaint.setStyle(Paint.Style.STROKE);
        mBgPaint.setStrokeWidth(stroke);
        mBgPaint.setColor(0xFFDDDDDD);
        mBgPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE, false, mBgPaint);

        // Value arc (colored fill)
        if (mValue != Integer.MAX_VALUE) {
            float frac = (float)(mValue - mMinVal) / (mMaxVal - mMinVal);
            frac = Math.max(0.03f, Math.min(1f, frac));
            mArcPaint.setStyle(Paint.Style.STROKE);
            mArcPaint.setStrokeWidth(stroke);
            mArcPaint.setColor(mArcColor);
            mArcPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE * frac, false, mArcPaint);
        }

        float cx = oval.centerX();
        float cy = oval.centerY();

        // Numeric value
        String valStr = mValue == Integer.MAX_VALUE ? "N/A" : String.valueOf(mValue);
        mValPaint.setTextAlign(Paint.Align.CENTER);
        mValPaint.setTextSize(w * 0.22f);
        mValPaint.setFakeBoldText(true);
        mValPaint.setColor(mValue == Integer.MAX_VALUE ? 0xFFAAAAAA : 0xFF212121);
        canvas.drawText(valStr, cx, cy + w * 0.08f, mValPaint);

        // Unit text below value
        mUnitPaint.setTextAlign(Paint.Align.CENTER);
        mUnitPaint.setTextSize(w * 0.10f);
        mUnitPaint.setColor(0xFFFFFFFF);
        canvas.drawText(mUnit, cx, cy + w * 0.22f, mUnitPaint);

        // Label at bottom (colored by quality, or grey if N/A)
        mLblPaint.setTextAlign(Paint.Align.CENTER);
        mLblPaint.setTextSize(w * 0.13f);
        mLblPaint.setFakeBoldText(true);
        mLblPaint.setColor(mValue == Integer.MAX_VALUE ? 0xFF999999 : mArcColor);
        canvas.drawText(mLabel, cx, w - w * 0.03f, mLblPaint);
    }
}
