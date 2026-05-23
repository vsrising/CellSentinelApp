package com.asun.cellsentinelapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Scrolling signal history chart – stores 120 seconds of RSRP, SINR, RSRQ.
 * Each series uses its own fixed Y-axis scale; all three are normalized 0-1
 * within typical ranges and drawn on the same plot area.
 */
public class SignalChartView extends View {

    private static final int MAX_SAMPLES = 120;
    private static final int NA = Integer.MAX_VALUE;

    // Fixed scale ranges (typical values)
    private static final int RSRP_MIN = -140, RSRP_MAX = -44;
    private static final int SINR_MIN =  -20, SINR_MAX =  30;
    private static final int RSRQ_MIN =  -20, RSRQ_MAX =   0;

    private static final int COLOR_RSRP = 0xFF1565C0; // blue
    private static final int COLOR_SINR = 0xFF2E7D32; // green
    private static final int COLOR_RSRQ = 0xFFBF360C; // deep orange

    private final int[] mRsrp = new int[MAX_SAMPLES];
    private final int[] mSinr = new int[MAX_SAMPLES];
    private final int[] mRsrq = new int[MAX_SAMPLES];
    private int mHead  = 0;
    private int mCount = 0;

    private final Paint mGridPaint  = new Paint();
    private final Paint mLinePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SignalChartView(Context ctx) {
        super(ctx);
        init();
    }

    public SignalChartView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }

    private void init() {
        // Pre-fill with NA
        java.util.Arrays.fill(mRsrp, NA);
        java.util.Arrays.fill(mSinr, NA);
        java.util.Arrays.fill(mRsrq, NA);
        setWillNotDraw(false);
    }

    /**
     * Push a new sample. Safe to call from any thread.
     * Pass Integer.MAX_VALUE for unavailable metrics.
     */
    public void addSample(int rsrp, int sinr, int rsrq) {
        mRsrp[mHead] = rsrp;
        mSinr[mHead] = sinr;
        mRsrq[mHead] = rsrq;
        mHead = (mHead + 1) % MAX_SAMPLES;
        if (mCount < MAX_SAMPLES) mCount++;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Layout: top strip for legend, rest for chart
        float density = getResources().getDisplayMetrics().density;
        float legendH = 20 * density;
        float padL = 4, padR = 4;
        float chartTop = legendH;
        float chartH   = h - legendH;
        float chartW   = w - padL - padR;

        // Background
        canvas.drawColor(0xFFF5F5F5);

        // Grid (3 horizontal lines)
        mGridPaint.setColor(0x22000000);
        mGridPaint.setStrokeWidth(1f);
        for (int i = 1; i <= 3; i++) {
            float y = chartTop + chartH * i / 4f;
            canvas.drawLine(padL, y, padL + chartW, y, mGridPaint);
        }

        if (mCount < 2) {
            drawLegend(canvas, w, legendH, NA, NA, NA);
            return;
        }

        // Draw the three series
        drawSeries(canvas, mRsrp, RSRP_MIN, RSRP_MAX, COLOR_RSRP, padL, chartTop, chartW, chartH);
        drawSeries(canvas, mSinr, SINR_MIN, SINR_MAX, COLOR_SINR, padL, chartTop, chartW, chartH);
        drawSeries(canvas, mRsrq, RSRQ_MIN, RSRQ_MAX, COLOR_RSRQ, padL, chartTop, chartW, chartH);

        int last = (mHead - 1 + MAX_SAMPLES) % MAX_SAMPLES;
        drawLegend(canvas, w, legendH, mRsrp[last], mSinr[last], mRsrq[last]);
    }

    private void drawSeries(Canvas canvas, int[] data,
                             int minV, int maxV, int color,
                             float padL, float top, float chartW, float chartH) {
        mLinePaint.setColor(color);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(2.5f);
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);

        Path path = new Path();
        boolean first = true;
        for (int j = 0; j < mCount; j++) {
            // Map j (0=oldest … mCount-1=newest) to ring buffer index
            int idx = (mHead - mCount + j + MAX_SAMPLES) % MAX_SAMPLES;
            int v = data[idx];
            if (v == NA) { first = true; continue; }

            // x: newest data flush-right
            float x = padL + chartW * (MAX_SAMPLES - mCount + j) / (float)(MAX_SAMPLES - 1);
            float norm = (float)(v - minV) / (maxV - minV);
            norm = Math.max(0f, Math.min(1f, norm));
            float y = top + chartH * (1f - norm);

            if (first) { path.moveTo(x, y); first = false; }
            else        path.lineTo(x, y);
        }
        canvas.drawPath(path, mLinePaint);
    }

    private void drawLegend(Canvas canvas, float w, float legendH,
                             int rsrp, int sinr, int rsrq) {
        float density = getResources().getDisplayMetrics().density;
        float textSz  = 9.5f * density;
        float y = legendH * 0.78f;
        mLabelPaint.setTextSize(textSz);
        mLabelPaint.setTextAlign(Paint.Align.LEFT);

        String rsrpLbl = "RSRP:" + (rsrp == NA ? "N/A" : rsrp + "dBm");
        String sinrLbl = "SINR:" + (sinr == NA ? "N/A" : sinr + "dB");
        String rsrqLbl = "RSRQ:" + (rsrq == NA ? "N/A" : rsrq + "dB");
        float gap = 6 * density;
        float x = 4;

        mLabelPaint.setColor(COLOR_RSRP);
        canvas.drawText(rsrpLbl, x, y, mLabelPaint);
        x += mLabelPaint.measureText(rsrpLbl) + gap;

        mLabelPaint.setColor(COLOR_SINR);
        canvas.drawText(sinrLbl, x, y, mLabelPaint);
        x += mLabelPaint.measureText(sinrLbl) + gap;

        mLabelPaint.setColor(COLOR_RSRQ);
        canvas.drawText(rsrqLbl, x, y, mLabelPaint);
    }
}
