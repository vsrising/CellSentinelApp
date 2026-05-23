package com.asun.cellsentinelapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfReportBuilder {

    private static final int   PAGE_W        = 595;
    private static final int   PAGE_H        = 842;
    private static final int   MARGIN        = 40;
    private static final int   CONTENT_W     = PAGE_W - 2 * MARGIN;
    private static final float CONTENT_BOT   = PAGE_H - 32f;  // lower content boundary
    private static final float FOOTER_Y      = PAGE_H - 16f;  // page-number baseline

    // ── Page state ────────────────────────────────────────────────────────────

    private static class PageCtx {
        final PdfDocument doc;
        PdfDocument.Page  page;
        Canvas            canvas;
        int               num;
        float             y;

        PageCtx(PdfDocument d) { doc = d; num = 0; newPage(false); }

        /** Start a new page. continuationHeader = true draws a thin accent bar. */
        void newPage(boolean continuationHeader) {
            if (page != null) { drawFooter(); doc.finishPage(page); }
            num++;
            PdfDocument.PageInfo info =
                    new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, num).create();
            page   = doc.startPage(info);
            canvas = page.getCanvas();
            if (continuationHeader) {
                Paint accent = new Paint();
                accent.setColor(0xFF1565C0);
                canvas.drawRect(0, 0, PAGE_W, 3, accent);
                y = MARGIN + 4;
            } else {
                y = MARGIN;
            }
        }

        void finish() { if (page != null) { drawFooter(); doc.finishPage(page); page = null; } }

        private void drawFooter() {
            if (canvas == null) return;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFBDBDBD);
            p.setTextSize(9f);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("— " + num + " —", PAGE_W / 2f, FOOTER_Y, p);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static File build(Context ctx,
                             String aiAnalysis,
                             String contextSummary,
                             String dataType,
                             List<CellSignalManager.SimSignalData> simData) {
        PdfDocument doc = new PdfDocument();
        PageCtx pc = new PageCtx(doc);

        // ── Page 1: report header + main sections ─────────────────────────────
        pc.y = drawReportHeader(pc.canvas, dataType);

        drawSection(pc, "AI 分析结论", stripMd(aiAnalysis));
        drawSection(pc, "网络信息",    buildNetworkInfo(simData));

        // ── Appendix: full raw context data ───────────────────────────────────
        if (contextSummary != null && !contextSummary.isEmpty()) {
            pc.newPage(false);
            pc.y = drawAppendixHeader(pc.canvas, dataType);
            drawBodyText(pc, contextSummary, 10f, 0xFF212121);
        }

        pc.finish();

        // ── Write file ────────────────────────────────────────────────────────
        File dir = ctx.getExternalFilesDir("reports");
        if (dir == null) { doc.close(); return null; }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String fname = "report_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date())
                + ".pdf";
        File f = new File(dir, fname);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            doc.writeTo(fos);
        } catch (Exception e) {
            doc.close();
            return null;
        }
        doc.close();
        return f;
    }

    // ── Section drawing ───────────────────────────────────────────────────────

    private static void drawSection(PageCtx pc, String title, String body) {
        if (body == null || body.isEmpty()) return;

        // Need at least title bar (26pt) + ~34pt for a couple lines before starting new page
        if (CONTENT_BOT - pc.y < 60) {
            pc.newPage(true);
        }

        float y = pc.y + 10;

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFFE3F2FD);
        pc.canvas.drawRoundRect(new RectF(MARGIN, y, PAGE_W - MARGIN, y + 22), 4, 4, bg);

        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFF1565C0);
        tp.setTextSize(13f);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        pc.canvas.drawText(title, MARGIN + 8, y + 15, tp);

        pc.y = y + 28;
        drawBodyText(pc, body, 11f, 0xFF212121);
    }

    // ── Multi-page body text ──────────────────────────────────────────────────

    private static void drawBodyText(PageCtx pc, String body, float textSize, int color) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(color);
        tp.setTextSize(textSize);

        StaticLayout sl = buildSl(body, tp, CONTENT_W);
        int total = sl.getLineCount();
        int line  = 0;

        while (line < total) {
            float available = CONTENT_BOT - pc.y;

            // Count lines that fit in remaining page space
            int end = line;
            while (end < total
                    && sl.getLineBottom(end) - sl.getLineTop(line) <= available) {
                end++;
            }

            if (end == line) {
                // Nothing fits — overflow to next page
                pc.newPage(true);
                continue;
            }

            // Draw lines [line, end) clipped to the available rect
            float segH = sl.getLineBottom(end - 1) - sl.getLineTop(line);
            pc.canvas.save();
            pc.canvas.clipRect(MARGIN, pc.y, PAGE_W - MARGIN, pc.y + segH);
            pc.canvas.translate(MARGIN, pc.y - sl.getLineTop(line));
            sl.draw(pc.canvas);
            pc.canvas.restore();

            pc.y += segH;
            line  = end;

            if (line < total) pc.newPage(true);
        }
        pc.y += 10;
    }

    // ── Headers ───────────────────────────────────────────────────────────────

    private static float drawReportHeader(Canvas c, String dataType) {
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(0xFF1565C0);
        c.drawRect(0, 0, PAGE_W, 72, bar);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("CellSentinel 网络分析报告", MARGIN, 38, title);

        Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
        sub.setColor(0xCCFFFFFF);
        sub.setTextSize(11f);
        String subtitle = ("drive_test".equals(dataType) ? "路测数据分析" : "信令事件分析")
                + "  |  "
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())
                + "  |  " + Build.MANUFACTURER + " " + Build.MODEL;
        c.drawText(subtitle, MARGIN, 58, sub);

        return 90f;
    }

    private static float drawAppendixHeader(Canvas c, String dataType) {
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(0xFF37474F);
        c.drawRect(0, 0, PAGE_W, 60, bar);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.WHITE);
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("附页 — 原始数据", MARGIN, 30, title);

        Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
        sub.setColor(0xCCFFFFFF);
        sub.setTextSize(10f);
        String subtitle = ("drive_test".equals(dataType) ? "路测分析数据" : "信令事件数据")
                + "  |  "
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        c.drawText(subtitle, MARGIN, 48, sub);

        return 72f;
    }

    // ── Markdown stripper (PDF uses plain canvas text) ────────────────────────

    static String stripMd(String md) {
        if (md == null) return null;
        md = md.replaceAll("(?m)^#{1,6}\\s+", "");          // headings
        md = md.replaceAll("\\*\\*\\*(.*?)\\*\\*\\*", "$1"); // bold+italic
        md = md.replaceAll("\\*\\*(.*?)\\*\\*",       "$1"); // bold
        md = md.replaceAll("__(.*?)__",                "$1");
        md = md.replaceAll("\\*(.*?)\\*",              "$1"); // italic
        md = md.replaceAll("_(.*?)_",                  "$1");
        md = md.replaceAll("(?s)```.*?```",            "");   // code blocks
        md = md.replaceAll("`(.*?)`",                  "$1"); // inline code
        md = md.replaceAll("(?m)^[-*+]\\s+",           "• "); // list → bullet
        return md.trim();
    }

    // ── StaticLayout builder ──────────────────────────────────────────────────

    private static StaticLayout buildSl(String text, TextPaint tp, int width) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), tp, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.0f)
                    .build();
        } else {
            //noinspection deprecation
            return new StaticLayout(text, tp, width,
                    Layout.Alignment.ALIGN_NORMAL, 1.0f, 2f, false);
        }
    }

    // ── Network info block ────────────────────────────────────────────────────

    private static String getSimRat(CellSignalManager.SimSignalData d) {
        if (d.nr_SSRSRP != Integer.MAX_VALUE) return "5G NR";
        if (d.lte_RSRP  != Integer.MAX_VALUE || d.lte_CI  != Integer.MAX_VALUE) return "LTE";
        if (d.wcdma_CID != Integer.MAX_VALUE) return "WCDMA";
        if (d.gsm_CID   != Integer.MAX_VALUE) return "GSM";
        return "未知";
    }

    private static String buildNetworkInfo(List<CellSignalManager.SimSignalData> simData) {
        if (simData == null || simData.isEmpty()) return "暂无网络信息";
        StringBuilder sb = new StringBuilder();
        sb.append("设备: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n");
        for (CellSignalManager.SimSignalData d : simData) {
            sb.append("【").append(d.simLabel).append("】\n");
            sb.append("  运营商: ").append(d.operatorName).append("\n");
            sb.append("  制式: ").append(getSimRat(d)).append("\n");
            if (d.lte_RSRP != Integer.MAX_VALUE)
                sb.append("  RSRP: ").append(d.lte_RSRP).append(" dBm\n");
            if (d.lte_SINR != Integer.MAX_VALUE)
                sb.append("  SINR: ").append(d.lte_SINR).append(" dB\n");
            if (d.lte_RSRQ != Integer.MAX_VALUE)
                sb.append("  RSRQ: ").append(d.lte_RSRQ).append(" dB\n");
            if (d.lte_PCI != Integer.MAX_VALUE)
                sb.append("  PCI: ").append(d.lte_PCI).append("\n");
            if (d.lte_CI != Integer.MAX_VALUE) {
                int enb = (d.lte_CI >> 8) & 0xFFFFF;
                int ci  = d.lte_CI & 0xFF;
                sb.append("  CI: ").append(d.lte_CI)
                  .append(" (eNB=").append(enb).append(", Cell=").append(ci).append(")\n");
            }
            if (d.lte_EARFCN != Integer.MAX_VALUE) {
                String band = EarfcnDecoder.decodeLte(d.lte_EARFCN);
                sb.append("  EARFCN: ").append(d.lte_EARFCN);
                if (band != null) sb.append(" (").append(band).append(")");
                sb.append("\n");
            }
            if (d.lte_bandwidth != Integer.MAX_VALUE)
                sb.append("  带宽: ").append(d.lte_bandwidth / 1000).append(" MHz\n");
            if (d.lte_TA != Integer.MAX_VALUE && d.lte_TA >= 0) {
                sb.append("  TA: ").append(d.lte_TA)
                  .append("  ≈ ").append((int)(d.lte_TA * 78.12)).append(" m\n");
            }
            if (d.lte_CQI != Integer.MAX_VALUE)
                sb.append("  CQI: ").append(d.lte_CQI).append("\n");
            if (d.nr_SSRSRP != Integer.MAX_VALUE) {
                sb.append("  NR SS-RSRP: ").append(d.nr_SSRSRP).append(" dBm");
                if (d.nr_SSSINR != Integer.MAX_VALUE)
                    sb.append("  SS-SINR: ").append(d.nr_SSSINR).append(" dB");
                sb.append("\n");
            }
            if (d.nr_CSIRSRP != Integer.MAX_VALUE)
                sb.append("  NR CSI-RSRP: ").append(d.nr_CSIRSRP).append(" dBm\n");
            if (d.nr_CSISINR != Integer.MAX_VALUE)
                sb.append("  NR CSI-SINR: ").append(d.nr_CSISINR).append(" dB\n");
            if (!d.neighborCells.isEmpty()) {
                sb.append("  邻区(").append(d.neighborCells.size()).append("): ");
                for (int i = 0; i < Math.min(3, d.neighborCells.size()); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(d.neighborCells.get(i));
                }
                if (d.neighborCells.size() > 3) sb.append("…");
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
