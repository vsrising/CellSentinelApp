package com.asun.cellsentinelapp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for CellSentinelPG API endpoints.
 * Server: http://<configured>:8080 (same JWT auth as RuoYi)
 */
public class CellSentinelApi {

    public interface CellListCallback<T> {
        void onSuccess(List<T> cells);
        void onError(String message);
    }

    // ── LTE queries ──────────────────────────────────────────────────────────

    /**
     * Exact CI match: query by eNodeBId + cellId derived from lte_CI.
     * LTE CI decomposition (Chinese carriers): eNodeBId = CI >> 8, cellId = CI & 0xFF.
     */
    public static void getLteCellsByCi(Context ctx, long enodebId, int cellId, int pageSize,
                                        CellListCallback<LteCellInfo> cb) {
        String path = "/system/infolte/list?enodebId=" + enodebId
                    + "&cellId=" + cellId
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) { cb.onSuccess(parseLteList(body)); }
            @Override public void onError(String message) { cb.onError(message); }
        });
    }

    /** Query all cells belonging to an eNodeB (by eNodeBId). */
    public static void getLteCellsByEnodebId(Context ctx, long enodebId, int pageSize,
                                              CellListCallback<LteCellInfo> cb) {
        String path = "/system/infolte/list?enodebId=" + enodebId
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) { cb.onSuccess(parseLteList(body)); }
            @Override public void onError(String message) { cb.onError(message); }
        });
    }

    /** Query LTE cells by PCI. Returns up to pageSize matches. */
    public static void getLteCellsByPci(Context ctx, int pci, int pageSize,
                                        CellListCallback<LteCellInfo> cb) {
        String path = "/system/infolte/list?pci=" + pci
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                cb.onSuccess(parseLteList(body));
            }
            @Override public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    /** Query LTE cells by TAC (fetches nearby cells for map overlay). */
    public static void getLteCellsByTac(Context ctx, int tac, int pageSize,
                                        CellListCallback<LteCellInfo> cb) {
        String path = "/system/infolte/list?tac=" + tac
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                cb.onSuccess(parseLteList(body));
            }
            @Override public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    // ── NR queries ───────────────────────────────────────────────────────────

    /** Query NR cells by physicalCellId (PCI). */
    public static void getNrCellsByPci(Context ctx, int pci, int pageSize,
                                       CellListCallback<NrCellInfo> cb) {
        String path = "/system/infonr/list?physicalCellId=" + pci
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                cb.onSuccess(parseNrList(body));
            }
            @Override public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    /** Query NR cells by trackingAreaCode (TAC). */
    public static void getNrCellsByTac(Context ctx, int tac, int pageSize,
                                       CellListCallback<NrCellInfo> cb) {
        String path = "/system/infonr/list?trackingAreaCode=" + tac
                    + "&pageNum=1&pageSize=" + pageSize;
        ApiClient.get(ctx, path, new ApiClient.Callback() {
            @Override public void onSuccess(String body) {
                cb.onSuccess(parseNrList(body));
            }
            @Override public void onError(String message) {
                cb.onError(message);
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
