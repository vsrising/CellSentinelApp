package com.asun.cellsentinelapp;

import org.json.JSONObject;

public class LteCellInfo {
    public int    id;
    public String cellName    = "";
    public String enodebName  = "";
    public double cellLon;
    public double cellLat;
    public double enodebLon;
    public double enodebLat;
    public long   enodebId;
    public int    cellId;
    public int    tac;
    public int    pci;
    public int    azimuth;
    public int    bandwidthMhz;
    public int    antennaHeight;
    public int    downlinkCenterFrequency;
    public String frequencyBand = "";
    public String operator      = "";
    public String cityName      = "";
    public String status        = "";

    public static LteCellInfo from(JSONObject o) {
        LteCellInfo c = new LteCellInfo();
        c.id                      = o.optInt("id");
        c.cellName                = o.optString("cellName", "");
        c.enodebName              = o.optString("enodebName", "");
        c.cellLon                 = parseDouble(o.optString("cellLongitude", "0"));
        c.cellLat                 = parseDouble(o.optString("cellLatitude",  "0"));
        c.enodebLon               = parseDouble(o.optString("enodebLongitude", "0"));
        c.enodebLat               = parseDouble(o.optString("enodebLatitude",  "0"));
        c.enodebId                = o.optLong("enodebId");
        c.cellId                  = o.optInt("cellId");
        c.tac                     = o.optInt("tac");
        c.pci                     = o.optInt("pci");
        c.azimuth                 = o.optInt("azimuthAngle");
        c.bandwidthMhz            = o.optInt("bandwidthMhz");
        c.antennaHeight           = o.optInt("antennaHeight");
        c.downlinkCenterFrequency = o.optInt("downlinkCenterFrequency");
        c.frequencyBand           = o.optString("frequencyBand", "");
        c.operator                = o.optString("operator", "");
        c.cityName                = o.optString("cityName", "");
        c.status                  = o.optString("status", "");
        return c;
    }

    public boolean hasValidCoords() {
        return cellLat != 0 && cellLon != 0;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id",                       id);
            o.put("cellName",                 cellName);
            o.put("enodebName",               enodebName);
            o.put("cellLongitude",            String.valueOf(cellLon));
            o.put("cellLatitude",             String.valueOf(cellLat));
            o.put("enodebLongitude",          String.valueOf(enodebLon));
            o.put("enodebLatitude",           String.valueOf(enodebLat));
            o.put("enodebId",                 enodebId);
            o.put("cellId",                   cellId);
            o.put("tac",                      tac);
            o.put("pci",                      pci);
            o.put("azimuthAngle",             azimuth);
            o.put("bandwidthMhz",             bandwidthMhz);
            o.put("antennaHeight",            antennaHeight);
            o.put("downlinkCenterFrequency",  downlinkCenterFrequency);
            o.put("frequencyBand",            frequencyBand);
            o.put("operator",                 operator);
            o.put("cityName",                 cityName);
            o.put("status",                   status);
        } catch (Exception ignored) {}
        return o;
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }
}
