package com.asun.cellsentinelapp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DriveTestRecord {

    public final long   timestamp;
    public final double latitude;
    public final double longitude;
    public final float  accuracy;

    public final String simLabel;
    public final String operatorName;
    public final String rat;          // "LTE" | "NR" | "WCDMA" | "GSM" | "Unknown"

    // Primary signal metrics (unified across RATs)
    public final int rsrp;   // LTE/NR RSRP or WCDMA/GSM RSSI
    public final int rsrq;   // LTE/NR RSRQ, Integer.MAX_VALUE if not applicable
    public final int sinr;   // LTE/NR SINR, Integer.MAX_VALUE if not applicable

    // Cell identification
    public final int pci;    // LTE PCI / NR PCI
    public final int ci;     // LTE CI
    public final int tac;    // LTE TAC
    public final int earfcn; // LTE EARFCN / NR NRARFCN

    public DriveTestRecord(double lat, double lon, float acc,
                           CellSignalManager.SimSignalData sim) {
        this.timestamp   = System.currentTimeMillis();
        this.latitude    = lat;
        this.longitude   = lon;
        this.accuracy    = acc;
        this.simLabel    = sim.simLabel;
        this.operatorName = sim.operatorName;

        // Determine dominant RAT and fill primary metrics
        if (sim.nr_SSRSRP != Integer.MAX_VALUE) {
            rat    = "NR";
            rsrp   = sim.nr_SSRSRP;
            rsrq   = sim.nr_SSRSRQ;
            sinr   = sim.nr_SSSINR;
            pci    = sim.nr_PCI;
            ci     = Integer.MAX_VALUE;
            tac    = sim.lte_TAC;
            earfcn = sim.nr_NRARFCN;
        } else if (sim.lte_RSRP != Integer.MAX_VALUE) {
            rat    = "LTE";
            rsrp   = sim.lte_RSRP;
            rsrq   = sim.lte_RSRQ;
            sinr   = sim.lte_SINR;
            pci    = sim.lte_PCI;
            ci     = sim.lte_CI;
            tac    = sim.lte_TAC;
            earfcn = sim.lte_EARFCN;
        } else if (sim.wcdma_RSSI != Integer.MAX_VALUE) {
            rat    = "WCDMA";
            rsrp   = sim.wcdma_RSSI;
            rsrq   = Integer.MAX_VALUE;
            sinr   = Integer.MAX_VALUE;
            pci    = sim.wcdma_PSC;
            ci     = sim.wcdma_CID;
            tac    = sim.wcdma_LAC;
            earfcn = Integer.MAX_VALUE;
        } else if (sim.gsm_RSSI != Integer.MAX_VALUE) {
            rat    = "GSM";
            rsrp   = sim.gsm_RSSI;
            rsrq   = Integer.MAX_VALUE;
            sinr   = Integer.MAX_VALUE;
            pci    = Integer.MAX_VALUE;
            ci     = sim.gsm_CID;
            tac    = sim.gsm_LAC;
            earfcn = Integer.MAX_VALUE;
        } else {
            rat    = "Unknown";
            rsrp   = Integer.MAX_VALUE;
            rsrq   = Integer.MAX_VALUE;
            sinr   = Integer.MAX_VALUE;
            pci    = Integer.MAX_VALUE;
            ci     = Integer.MAX_VALUE;
            tac    = Integer.MAX_VALUE;
            earfcn = Integer.MAX_VALUE;
        }
    }

    private static String fmt(int v) {
        return v == Integer.MAX_VALUE ? "N/A" : String.valueOf(v);
    }

    public String getTimeString() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    public String getSignalSummary() {
        if (rsrp == Integer.MAX_VALUE) return rat + "  无信号";
        String metric = rat.equals("LTE") || rat.equals("NR") ? "RSRP" : "RSSI";
        return String.format(Locale.US, "%s  %s:%s dBm", rat, metric, fmt(rsrp));
    }

    public String toJsonObject() {
        return String.format(Locale.US,
                "{\"timestamp\":%d,\"latitude\":%.6f,\"longitude\":%.6f,\"accuracy\":%.1f," +
                "\"simLabel\":\"%s\",\"operatorName\":\"%s\",\"rat\":\"%s\"," +
                "\"rsrp\":%s,\"rsrq\":%s,\"sinr\":%s," +
                "\"pci\":%s,\"ci\":%s,\"tac\":%s,\"earfcn\":%s}",
                timestamp, latitude, longitude, accuracy,
                simLabel, operatorName, rat,
                rsrp == Integer.MAX_VALUE ? "null" : String.valueOf(rsrp),
                rsrq == Integer.MAX_VALUE ? "null" : String.valueOf(rsrq),
                sinr == Integer.MAX_VALUE ? "null" : String.valueOf(sinr),
                pci  == Integer.MAX_VALUE ? "null" : String.valueOf(pci),
                ci   == Integer.MAX_VALUE ? "null" : String.valueOf(ci),
                tac  == Integer.MAX_VALUE ? "null" : String.valueOf(tac),
                earfcn == Integer.MAX_VALUE ? "null" : String.valueOf(earfcn));
    }

    /** CSV row (matches header in DriveTestManager.toCsv) */
    public String toCsvRow() {
        return String.format(Locale.US,
                "%d,%.6f,%.6f,%.1f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                timestamp, latitude, longitude, accuracy,
                simLabel, operatorName, rat,
                fmt(rsrp), fmt(rsrq), fmt(sinr),
                fmt(pci), fmt(ci), fmt(tac), fmt(earfcn));
    }

    public static String csvHeader() {
        return "timestamp,latitude,longitude,accuracy,simLabel,operatorName,rat," +
               "rsrp,rsrq,sinr,pci,ci,tac,earfcn";
    }

    /** Build JSON array from list */
    public static String toJsonArray(List<DriveTestRecord> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i).toJsonObject());
        }
        sb.append("]");
        return sb.toString();
    }
}
