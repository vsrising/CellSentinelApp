package com.asun.cellsentinelapp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for CellSentinelPG API endpoints.
 * Each query method: on backend success → store in cache; on backend error → serve from cache.
 */
public class CellSentinelApi {

    public interface CellListCallback<T> {
        void onSuccess(List<T> cells);
        void onError(String message);
    }

    // ── LTE queries ──────────────────────────────────────────────────────────

    public static void getLteCellsByCi(Context ctx, long enodebId, int cellId, int pageSize,
                                        CellListCallback<LteCellInfo> cb) {
        String key  = "lte_ci_" + enodebId + "_" + cellId;
        String path = "/system/infolte/list?enodebId=" + enodebId
                    + "&cellId=" + cellId
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                List<LteCellInfo> cells = parseLteList(body);
                CellInfoCache.storeLteCells(ctx, key, cells);
                cb.onSuccess(cells);
            }
            @Override public void onError(String message) {
                List<LteCellInfo> cached = CellInfoCache.loadLteCells(ctx, key);
                if (cached != null) cb.onSuccess(cached);
                else                cb.onError(message);
            }
        });
    }

    public static void getLteCellsByEnodebId(Context ctx, long enodebId, int pageSize,
                                              CellListCallback<LteCellInfo> cb) {
        String key  = "lte_enb_" + enodebId;
        String path = "/system/infolte/list?enodebId=" + enodebId
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                List<LteCellInfo> cells = parseLteList(body);
                CellInfoCache.storeLteCells(ctx, key, cells);
                cb.onSuccess(cells);
            }
            @Override public void onError(String message) {
                List<LteCellInfo> cached = CellInfoCache.loadLteCells(ctx, key);
                if (cached != null) cb.onSuccess(cached);
                else                cb.onError(message);
            }
        });
    }

    public static void getLteCellsByPci(Context ctx, int pci, int pageSize,
                                        CellListCallback<LteCellInfo> cb) {
        String key  = "lte_pci_" + pci;
        String path = "/system/infolte/list?pci=" + pci
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                List<LteCellInfo> cells = parseLteList(body);
                CellInfoCache.storeLteCells(ctx, key, cells);
                cb.onSuccess(cells);
            }
            @Override public void onError(String message) {
                List<LteCellInfo> cached = CellInfoCache.loadLteCells(ctx, key);
                if (cached != null) cb.onSuccess(cached);
                else                cb.onError(message);
            }
        });
    }

    public static void getLteCellsByTac(Context ctx, int tac, int pageSize,
                                        CellListCallback<LteCellInfo> cb) {
        String key  = "lte_tac_" + tac;
        String path = "/system/infolte/list?tac=" + tac
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                List<LteCellInfo> cells = parseLteList(body);
                CellInfoCache.storeLteCells(ctx, key, cells);
                cb.onSuccess(cells);
            }
            @Override public void onError(String message) {
                List<LteCellInfo> cached = CellInfoCache.loadLteCells(ctx, key);
                if (cached != null) cb.onSuccess(cached);
                else                cb.onError(message);
            }
        });
    }

    // ── NR queries ───────────────────────────────────────────────────────────

    public static void getNrCellsByPci(Context ctx, int pci, int pageSize,
                                       CellListCallback<NrCellInfo> cb) {
        String key  = "nr_pci_" + pci;
        String path = "/system/infonr/list?physicalCellId=" + pci
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                List<NrCellInfo> cells = parseNrList(body);
                CellInfoCache.storeNrCells(ctx, key, cells);
                cb.onSuccess(cells);
            }
            @Override public void onError(String message) {
                List<NrCellInfo> cached = CellInfoCache.loadNrCells(ctx, key);
                if (cached != null) cb.onSuccess(cached);
                else                cb.onError(message);
            }
        });
    }

    public static void getNrCellsByTac(Context ctx, int tac, int pageSize,
                                       CellListCallback<NrCellInfo> cb) {
        String key  = "nr_tac_" + tac;
        String path = "/system/infonr/list?trackingAreaCode=" + tac
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                List<NrCellInfo> cells = parseNrList(body);
                CellInfoCache.storeNrCells(ctx, key, cells);
                cb.onSuccess(cells);
            }
            @Override public void onError(String message) {
                List<NrCellInfo> cached = CellInfoCache.loadNrCells(ctx, key);
                if (cached != null) cb.onSuccess(cached);
                else                cb.onError(message);
            }
        });
    }

    // ── JSON parsers ─────────────────────────────────────────────────────────

    static List<LteCellInfo> parseLteList(String json) {
        List<LteCellInfo> list = new ArrayList<>();
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray rows = obj.optJSONArray("rows");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    LteCellInfo c = LteCellInfo.from(rows.getJSONObject(i));
                    if (c.hasValidCoords()) list.add(c);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    static List<NrCellInfo> parseNrList(String json) {
        List<NrCellInfo> list = new ArrayList<>();
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray rows = obj.optJSONArray("rows");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    NrCellInfo c = NrCellInfo.from(rows.getJSONObject(i));
                    if (c.hasValidCoords()) list.add(c);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}
