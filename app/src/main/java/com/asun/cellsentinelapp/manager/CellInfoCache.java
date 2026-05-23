package com.asun.cellsentinelapp.manager;

import com.asun.cellsentinelapp.model.LteCellInfo;
import com.asun.cellsentinelapp.model.NrCellInfo;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Disk-backed in-memory cache for LTE/NR cell info.
 * Cache keys: "lte_ci_{enodebId}_{cellId}", "lte_pci_{pci}", "lte_tac_{tac}",
 *             "nr_pci_{pci}", "nr_tac_{tac}"
 * TTL: 7 days.  Writes are async (background thread).
 */
public class CellInfoCache {

    private static final String CACHE_FILE   = "cell_cache.json";
    private static final long   CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    private static JSONObject sCache;

    // ── LTE ──────────────────────────────────────────────────────────────────

    public static void storeLteCells(Context ctx, String key, List<LteCellInfo> cells) {
        JSONArray arr = new JSONArray();
        for (LteCellInfo c : cells) arr.put(c.toJson());
        putEntry(ctx, key, arr);
    }

    /** Returns cached LTE cells if present and unexpired; null if cache miss. */
    public static List<LteCellInfo> loadLteCells(Context ctx, String key) {
        JSONArray arr = getEntry(ctx, key);
        if (arr == null) return null;
        List<LteCellInfo> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try { list.add(LteCellInfo.from(arr.getJSONObject(i))); } catch (Exception ignored) {}
        }
        return list;
    }

    // ── NR ───────────────────────────────────────────────────────────────────

    public static void storeNrCells(Context ctx, String key, List<NrCellInfo> cells) {
        JSONArray arr = new JSONArray();
        for (NrCellInfo c : cells) arr.put(c.toJson());
        putEntry(ctx, key, arr);
    }

    /** Returns cached NR cells if present and unexpired; null if cache miss. */
    public static List<NrCellInfo> loadNrCells(Context ctx, String key) {
        JSONArray arr = getEntry(ctx, key);
        if (arr == null) return null;
        List<NrCellInfo> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try { list.add(NrCellInfo.from(arr.getJSONObject(i))); } catch (Exception ignored) {}
        }
        return list;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static synchronized JSONObject cache(Context ctx) {
        if (sCache == null) sCache = readFile(ctx);
        return sCache;
    }

    private static synchronized void putEntry(Context ctx, String key, JSONArray items) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("ts",    System.currentTimeMillis());
            entry.put("items", items);
            cache(ctx).put(key, entry);
        } catch (Exception ignored) {}
        // Snapshot before async flush to avoid data races
        String snapshot = sCache != null ? sCache.toString() : "{}";
        new Thread(() -> writeFile(ctx.getApplicationContext(), snapshot)).start();
    }

    private static synchronized JSONArray getEntry(Context ctx, String key) {
        try {
            JSONObject all = cache(ctx);
            if (!all.has(key)) return null;
            JSONObject entry = all.getJSONObject(key);
            if (System.currentTimeMillis() - entry.optLong("ts") > CACHE_TTL_MS) return null;
            return entry.optJSONArray("items");
        } catch (Exception ignored) { return null; }
    }

    private static JSONObject readFile(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), CACHE_FILE);
            if (!f.exists()) return new JSONObject();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (Exception ignored) { return new JSONObject(); }
    }

    private static void writeFile(Context ctx, String data) {
        try {
            File f = new File(ctx.getFilesDir(), CACHE_FILE);
            try (FileWriter fw = new FileWriter(f)) {
                fw.write(data);
            }
        } catch (Exception ignored) {}
    }
}
