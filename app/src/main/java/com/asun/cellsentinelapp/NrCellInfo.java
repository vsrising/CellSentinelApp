package com.asun.cellsentinelapp;

import org.json.JSONObject;

public class NrCellInfo {
    public int    id;
    public String cellName      = "";
    public String gnodebName    = "";
    public double cellLon;
    public double cellLat;
    public double gnodebLon;
    public double gnodebLat;
    public long   gnodebId;
    public int    cellId;
    public int    physicalCellId;  // PCI
    public int    tac;             // trackingAreaCode
    public int    azimuth;
    public int    bandwidthMhz;
    public int    antennaHeight;
    public int    downlinkFrequency;
    public String frequencyBand = "";
    public String operator      = "";
    public String cityName      = "";
    public String status        = "";

    public static NrCellInfo from(JSONObject o) {
        NrCellInfo c = new NrCellInfo();
        c.id               = o.optInt("id");
        c.cellName         = o.optString("cellName", "");
        c.gnodebName       = o.optString("gnodebName", "");
        c.cellLon          = parseDouble(o.optString("cellLongitude", "0"));
        c.cellLat          = parseDouble(o.optString("cellLatitude",  "0"));
        c.gnodebLon        = parseDouble(o.optString("gnodebLongitude", "0"));
        c.gnodebLat        = parseDouble(o.optString("gnodebLatitude",  "0"));
        c.gnodebId         = o.optLong("gnodebId");
        c.cellId           = o.optInt("cellId");
        c.physicalCellId   = o.optInt("physicalCellId");
        c.tac              = o.optInt("trackingAreaCode");
        c.azimuth          = o.optInt("azimuthAngle");
        c.bandwidthMhz     = o.optInt("bandwidthMhz");
        c.antennaHeight    = o.optInt("antennaHeight");
        c.downlinkFrequency= o.optInt("downlinkFrequency");
        c.frequencyBand    = o.optString("frequencyBand", "");
        c.operator         = o.optString("operator", "");
        c.cityName         = o.optString("cityName", "");
        c.status           = o.optString("status", "");
        return c;
    }

    public boolean hasValidCoords() {
        return cellLat != 0 && cellLon != 0;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id",                id);
            o.put("cellName",          cellName);
            o.put("gnodebName",        gnodebName);
            o.put("cellLongitude",     String.valueOf(cellLon));
            o.put("cellLatitude",      String.valueOf(cellLat));
            o.put("gnodebLongitude",   String.valueOf(gnodebLon));
            o.put("gnodebLatitude",    String.valueOf(gnodebLat));
            o.put("gnodebId",          gnodebId);
            o.put("cellId",            cellId);
            o.put("physicalCellId",    physicalCellId);
            o.put("trackingAreaCode",  tac);
            o.put("azimuthAngle",      azimuth);
            o.put("bandwidthMhz",      bandwidthMhz);
            o.put("antennaHeight",     antennaHeight);
            o.put("downlinkFrequency", downlinkFrequency);
            o.put("frequencyBand",     frequencyBand);
            o.put("operator",          operator);
            o.put("cityName",          cityName);
            o.put("status",            status);
        } catch (Exception ignored) {}
        return o;
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }
}
