package com.asun.cellsentinelapp;

import android.location.Location;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DriveTestRecord {

    public final long   timestamp;
    public final double latitude;
    public final double longitude;
    public final float  accuracy;
    public final float  altitude;   // GPS altitude (m)
    public final float  speed;      // GPS speed (m/s)
    public final float  bearing;    // GPS bearing (degrees)

    public final String simLabel;
    public final String operatorName;
    public final String rat;          // "LTE" | "NR" | "WCDMA" | "GSM" | "Unknown"

    // Primary signal metrics
    public final int rsrp;
    public final int rsrq;
    public final int sinr;

    // Cell identification
    public final int pci;
    public final int ci;
    public final int tac;
    public final int earfcn;

    // LTE extra metrics
    public final int ta;          // Timing Advance (each unit ≈78m one-way)
    public final int cqi;         // Channel Quality Indicator (0-15)
    public final int bandwidth;   // Channel bandwidth in kHz

    // Neighbor cell snapshot at this measurement point
    public final List<CellSignalManager.NeighborCell> neighbors;

    public DriveTestRecord(Location location, CellSignalManager.SimSignalData sim) {
        this.timestamp    = System.currentTimeMillis();
        this.latitude     = location.getLatitude();
        this.longitude    = location.getLongitude();
        this.accuracy     = location.getAccuracy();
        this.altitude     = (float) location.getAltitude();
        this.speed        = location.hasSpeed()   ? location.getSpeed()   : 0f;
        this.bearing      = location.hasBearing() ? location.getBearing() : 0f;
        this.simLabel     = sim.simLabel;
        this.operatorName = sim.operatorName;
        this.neighbors    = new ArrayList<>(sim.neighborCellData);

        if (sim.nr_SSRSRP != Integer.MAX_VALUE) {
            rat       = "NR";
            rsrp      = sim.nr_SSRSRP;
            rsrq      = sim.nr_SSRSRQ;
            sinr      = sim.nr_SSSINR;
            pci       = sim.nr_PCI;
            ci        = Integer.MAX_VALUE;
            tac       = sim.nr_TAC != Integer.MAX_VALUE ? sim.nr_TAC : sim.lte_TAC;
            earfcn    = sim.nr_NRARFCN;
            ta        = Integer.MAX_VALUE;
            cqi       = Integer.MAX_VALUE;
            bandwidth = Integer.MAX_VALUE;
        } else if (sim.lte_RSRP != Integer.MAX_VALUE) {
            rat       = "LTE";
            rsrp      = sim.lte_RSRP;
            rsrq      = sim.lte_RSRQ;
            sinr      = sim.lte_SINR;
            pci       = sim.lte_PCI;
            ci        = sim.lte_CI;
            tac       = sim.lte_TAC;
            earfcn    = sim.lte_EARFCN;
            ta        = sim.lte_TA;
            cqi       = sim.lte_CQI;
            bandwidth = sim.lte_bandwidth;
        } else if (sim.wcdma_RSSI != Integer.MAX_VALUE) {
            rat       = "WCDMA";
            rsrp      = sim.wcdma_RSSI;
            rsrq      = Integer.MAX_VALUE;
            sinr      = Integer.MAX_VALUE;
            pci       = sim.wcdma_PSC;
            ci        = sim.wcdma_CID;
            tac       = sim.wcdma_LAC;
            earfcn    = Integer.MAX_VALUE;
            ta        = Integer.MAX_VALUE;
            cqi       = Integer.MAX_VALUE;
            bandwidth = Integer.MAX_VALUE;
        } else if (sim.gsm_RSSI != Integer.MAX_VALUE) {
            rat       = "GSM";
            rsrp      = sim.gsm_RSSI;
            rsrq      = Integer.MAX_VALUE;
            sinr      = Integer.MAX_VALUE;
            pci       = Integer.MAX_VALUE;
            ci        = sim.gsm_CID;
            tac       = sim.gsm_LAC;
            earfcn    = Integer.MAX_VALUE;
            ta        = Integer.MAX_VALUE;
            cqi       = Integer.MAX_VALUE;
            bandwidth = Integer.MAX_VALUE;
        } else {
            rat       = "Unknown";
            rsrp      = Integer.MAX_VALUE;
            rsrq      = Integer.MAX_VALUE;
            sinr      = Integer.MAX_VALUE;
            pci       = Integer.MAX_VALUE;
            ci        = Integer.MAX_VALUE;
            tac       = Integer.MAX_VALUE;
            earfcn    = Integer.MAX_VALUE;
            ta        = Integer.MAX_VALUE;
            cqi       = Integer.MAX_VALUE;
            bandwidth = Integer.MAX_VALUE;
        }
    }

    /** Estimated distance from base station in meters, or -1 if TA unavailable. */
    public int estimatedDistM() {
        return (ta != Integer.MAX_VALUE && ta >= 0) ? (int)(ta * 78.12) : -1;
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
                "\"altitude\":%.1f,\"speed\":%.2f,\"bearing\":%.1f," +
                "\"simLabel\":\"%s\",\"operatorName\":\"%s\",\"rat\":\"%s\"," +
                "\"rsrp\":%s,\"rsrq\":%s,\"sinr\":%s," +
                "\"pci\":%s,\"ci\":%s,\"tac\":%s,\"earfcn\":%s," +
                "\"ta\":%s,\"cqi\":%s,\"bandwidth\":%s}",
                timestamp, latitude, longitude, accuracy,
                altitude, speed, bearing,
                simLabel, operatorName, rat,
                rsrp == Integer.MAX_VALUE ? "null" : String.valueOf(rsrp),
                rsrq == Integer.MAX_VALUE ? "null" : String.valueOf(rsrq),
                sinr == Integer.MAX_VALUE ? "null" : String.valueOf(sinr),
                pci  == Integer.MAX_VALUE ? "null" : String.valueOf(pci),
                ci   == Integer.MAX_VALUE ? "null" : String.valueOf(ci),
                tac  == Integer.MAX_VALUE ? "null" : String.valueOf(tac),
                earfcn == Integer.MAX_VALUE ? "null" : String.valueOf(earfcn),
                ta == Integer.MAX_VALUE ? "null" : String.valueOf(ta),
                cqi == Integer.MAX_VALUE ? "null" : String.valueOf(cqi),
                bandwidth == Integer.MAX_VALUE ? "null" : String.valueOf(bandwidth));
    }

    public String toCsvRow() {
        return String.format(Locale.US,
                "%d,%.6f,%.6f,%.1f,%.1f,%.2f,%.1f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                timestamp, latitude, longitude, accuracy, altitude, speed, bearing,
                simLabel, operatorName, rat,
                fmt(rsrp), fmt(rsrq), fmt(sinr),
                fmt(pci), fmt(ci), fmt(tac), fmt(earfcn),
                fmt(ta), fmt(cqi), fmt(bandwidth));
    }

    public static String csvHeader() {
        return "timestamp,latitude,longitude,accuracy,altitude,speed_ms,bearing," +
               "simLabel,operatorName,rat," +
               "rsrp,rsrq,sinr,pci,ci,tac,earfcn,ta,cqi,bandwidth_khz";
    }

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
