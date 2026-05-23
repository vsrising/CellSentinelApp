package com.asun.cellsentinelapp.util;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;

/** Converts a subset of Markdown to Android Spanned text via Html.fromHtml(). */
public class MarkdownUtils {

    public static Spanned toSpanned(String md) {
        if (md == null || md.isEmpty()) return null;
        String html = convertToHtml(md);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            //noinspection deprecation
            return Html.fromHtml(html);
        }
    }

    private static String convertToHtml(String md) {
        String[] lines = md.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean inCode = false;

        for (String raw : lines) {
            String t = raw.trim();

            if (t.startsWith("```")) { inCode = !inCode; out.append("<br>"); continue; }
            if (inCode)              { out.append("<tt>").append(esc(raw)).append("</tt><br>"); continue; }

            if (t.isEmpty()) { out.append("<br>"); continue; }

            if      (t.startsWith("### ")) out.append("<b>").append(inline(t.substring(4))).append("</b><br>");
            else if (t.startsWith("## "))  out.append("<br><b>").append(inline(t.substring(3))).append("</b><br>");
            else if (t.startsWith("# "))   out.append("<br><b><big>").append(inline(t.substring(2))).append("</big></b><br>");
            else if (t.matches("^[-*_]{3,}$")) out.append("<br>");
            else if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ "))
                out.append("&nbsp;&nbsp;• ").append(inline(t.substring(2))).append("<br>");
            else if (t.matches("^\\d+\\.\\s.*")) {
                int dot = t.indexOf(". ");
                out.append("&nbsp;&nbsp;").append(esc(t.substring(0, dot + 2)))
                  .append(inline(t.substring(dot + 2))).append("<br>");
            }
            else out.append(inline(raw)).append("<br>");
        }
        return out.toString();
    }

    /** Escape HTML entities first, then apply inline Markdown formatting. */
    private static String inline(String s) {
        s = esc(s);
        s = s.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "<b><i>$1</i></b>");
        s = s.replaceAll("\\*\\*(.+?)\\*\\*",        "<b>$1</b>");
        s = s.replaceAll("__(.+?)__",                 "<b>$1</b>");
        s = s.replaceAll("\\*(.+?)\\*",               "<i>$1</i>");
        s = s.replaceAll("_(.+?)_",                   "<i>$1</i>");
        s = s.replaceAll("`(.+?)`",                   "<tt>$1</tt>");
        return s;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
