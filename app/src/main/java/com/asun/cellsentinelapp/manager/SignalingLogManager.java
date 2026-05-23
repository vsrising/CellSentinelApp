package com.asun.cellsentinelapp.manager;

import com.asun.cellsentinelapp.activity.MainActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SignalingLogManager {

    public enum EventType { HANDOVER, RAT_CHANGE, SERVICE_STATE, SIGNAL_CHANGE, CELL_INFO }

    /** One captured event with full context at the moment it fired. */
    public static class SignalingEvent {
        public final long      timestamp;
        public final EventType type;
        public final String    simLabel;
        public final String    summary;
        public final String    detail;

        // RAT
        public final String rat;

        // LTE serving cell identity
        public final int ci, enodebId, cellIndex;
        public final int pci, tac, earfcn;

        // LTE signal
        public final int rsrp, sinr, rsrq;
        public final int ta;           // Timing Advance
        public final int cqi;          // Channel Quality Indicator
        public final int bandwidth;    // kHz

        // NR signal (populated when NR is serving or in NSA anchor)
        public final int nr_rsrp, nr_rsrq, nr_sinr;
        public final int nr_csirsrp, nr_csirsrq, nr_csisinr;

        // GPS context
        public final double latitude, longitude;
        public final float  altitude, speed, bearing, accuracy;

        // Derived
        public final int estimatedDistM;   // TA * 78, or -1

        // Neighbor snapshot at capture time
        public final List<CellSignalManager.NeighborCell> neighbors;

        SignalingEvent(EventType type, String summary, String detail,
                       CellSignalManager.SimSignalData d, Location loc) {
            this.timestamp = System.currentTimeMillis();
            this.type      = type;
            this.summary   = summary;
            this.detail    = detail;

            if (d != null) {
                this.simLabel = d.simLabel != null ? d.simLabel : "";

                // RAT
                if (d.nr_SSRSRP != Integer.MAX_VALUE)
                    rat = "NR";
                else if (d.lte_RSRP != Integer.MAX_VALUE || d.lte_CI != Integer.MAX_VALUE)
                    rat = "LTE";
                else if (d.wcdma_CID != Integer.MAX_VALUE)
                    rat = "WCDMA";
                else if (d.gsm_CID != Integer.MAX_VALUE)
                    rat = "GSM";
                else
                    rat = "Unknown";

                // LTE serving cell
                ci        = d.lte_CI;
                enodebId  = (ci != Integer.MAX_VALUE && ci > 0) ? (ci >> 8) & 0xFFFFF : -1;
                cellIndex = (ci != Integer.MAX_VALUE && ci > 0) ? ci & 0xFF : -1;
                pci       = d.lte_PCI;
                tac       = d.lte_TAC;
                earfcn    = d.lte_EARFCN;
                rsrp      = d.lte_RSRP;
                sinr      = d.lte_SINR;
                rsrq      = d.lte_RSRQ;
                ta        = d.lte_TA;
                cqi       = d.lte_CQI;
                bandwidth = d.lte_bandwidth;

                // NR
                nr_rsrp    = d.nr_SSRSRP;
                nr_rsrq    = d.nr_SSRSRQ;
                nr_sinr    = d.nr_SSSINR;
                nr_csirsrp = d.nr_CSIRSRP;
                nr_csirsrq = d.nr_CSIRSRQ;
                nr_csisinr = d.nr_CSISINR;

                neighbors = new ArrayList<>(d.neighborCellData);
            } else {
                simLabel  = "";  rat = "Unknown";
                ci = pci = tac = earfcn = Integer.MAX_VALUE;
                enodebId = cellIndex = -1;
                rsrp = sinr = rsrq = ta = cqi = bandwidth = Integer.MAX_VALUE;
                nr_rsrp = nr_rsrq = nr_sinr = Integer.MAX_VALUE;
                nr_csirsrp = nr_csirsrq = nr_csisinr = Integer.MAX_VALUE;
                neighbors = new ArrayList<>();
            }

            estimatedDistM = (ta != Integer.MAX_VALUE && ta >= 0) ? (int)(ta * 78.12) : -1;

            latitude  = loc != null ? loc.getLatitude()  : Double.NaN;
            longitude = loc != null ? loc.getLongitude() : Double.NaN;
            altitude  = loc != null ? (float) loc.getAltitude()  : -1;
            speed     = loc != null && loc.hasSpeed()   ? loc.getSpeed()   : -1;
            bearing   = loc != null && loc.hasBearing() ? loc.getBearing() : -1;
            accuracy  = loc != null ? loc.getAccuracy() : -1;
        }

        public String formattedTime() {
            return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date(timestamp));
        }

        public String toCsvRow() {
            String nbJson = neighborsToJson();
            return String.format(Locale.US,
                    "%d,%s,%s,%s,%s,\"%s\",\"%s\"," +
                    "%s,%s,%s,%s,%s,%s," +
                    "%s,%s,%s,%s,%s,%s,%s," +
                    "%s,%s,%s," +
                    "%s,%s,%s,%s,%s,%s," +
                    "%d,\"%s\"",
                    timestamp,
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date(timestamp)),
                    csvStr(simLabel), type.name(), csvStr(rat),
                    summary.replace("\"", "\"\""),
                    detail.replace("\"", "\"\""),
                    // LTE cell identity
                    fmtInt(ci), fmtInt(enodebId), fmtInt(cellIndex), fmtInt(pci), fmtInt(tac), fmtInt(earfcn),
                    // LTE signal + extras
                    fmtInt(rsrp), fmtInt(sinr), fmtInt(rsrq), fmtInt(ta),
                    estimatedDistM < 0 ? "N/A" : String.valueOf(estimatedDistM),
                    fmtInt(cqi), fmtInt(bandwidth),
                    // NR signal
                    fmtInt(nr_rsrp), fmtInt(nr_rsrq), fmtInt(nr_sinr),
                    // GPS
                    Double.isNaN(latitude)  ? "N/A" : String.format(Locale.US, "%.6f", latitude),
                    Double.isNaN(longitude) ? "N/A" : String.format(Locale.US, "%.6f", longitude),
                    altitude  < 0 ? "N/A" : String.format(Locale.US, "%.1f", altitude),
                    speed     < 0 ? "N/A" : String.format(Locale.US, "%.2f", speed),
                    bearing   < 0 ? "N/A" : String.format(Locale.US, "%.1f", bearing),
                    accuracy  < 0 ? "N/A" : String.format(Locale.US, "%.1f", accuracy),
                    // neighbors
                    neighbors.size(), nbJson);
        }

        private String neighborsToJson() {
            if (neighbors.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < neighbors.size(); i++) {
                CellSignalManager.NeighborCell nc = neighbors.get(i);
                if (i > 0) sb.append(";");
                sb.append(nc.rat).append(":PCI").append(fmtInt(nc.pci))
                  .append(":EARFCN").append(fmtInt(nc.earfcn))
                  .append(":RSRP").append(fmtInt(nc.rsrp));
                if (nc.rsrq != Integer.MAX_VALUE) sb.append(":RSRQ").append(nc.rsrq);
            }
            return sb.append("]").toString();
        }

        private static String csvStr(String s) { return s == null || s.isEmpty() ? "N/A" : s; }
        private static String fmtInt(int v) { return v == Integer.MAX_VALUE || v < 0 ? "N/A" : String.valueOf(v); }

        public static String csvHeader() {
            return "timestamp,datetime,sim,type,rat,summary,detail," +
                   "ci,enodeb_id,cell_index,pci,tac,earfcn," +
                   "rsrp_dbm,sinr_db,rsrq_db,ta,est_dist_m,cqi,bw_khz," +
                   "nr_rsrp,nr_rsrq,nr_sinr," +
                   "latitude,longitude,altitude_m,speed_ms,bearing_deg,accuracy_m," +
                   "neighbor_count,neighbors";
        }
    }

    public interface Listener { void onNewEvent(SignalingEvent event); }

    private static final int MAX_EVENTS = 1000;

    private final Context         mCtx;
    private final Handler         mMainHandler = new Handler(Looper.getMainLooper());
    private final List<SignalingEvent> mEvents  = new ArrayList<>();
    private final List<Listener>  mListeners   = new ArrayList<>();

    private int mTargetSimIndex = -1;   // -1 = all SIMs, 0 = SIM1, 1 = SIM2

    private TelephonyManager    mTelMgr;
    private PhoneStateListener  mPhoneListener;
    private boolean             mRunning = false;

    // GPS tracking
    private LocationManager  mLocMgr;
    private Location         mLastLocation;
    private final LocationListener mLocListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location l) { mLastLocation = l; }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(@NonNull String p) {}
        @Override public void onProviderDisabled(@NonNull String p) {}
    };

    // State tracking for delta detection
    private int mLastNetworkType  = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private int mLastServiceState = -1;
    private int mLastRsrp         = Integer.MAX_VALUE;
    private int mLastPci          = Integer.MAX_VALUE;

    public SignalingLogManager(Context ctx) {
        mCtx    = ctx.getApplicationContext();
        mTelMgr = (TelephonyManager) mCtx.getSystemService(Context.TELEPHONY_SERVICE);
        mLocMgr = (LocationManager) mCtx.getSystemService(Context.LOCATION_SERVICE);
    }

    public void setTargetSimIndex(int index) {
        if (mTargetSimIndex == index) return;
        mTargetSimIndex = index;
        if (mRunning) { stop(); start(); }
    }

    public int getTargetSimIndex() { return mTargetSimIndex; }

    public void start() {
        if (mRunning) return;
        mRunning = true;
        mTelMgr  = buildTelephonyManager();

        mPhoneListener = new PhoneStateListener() {
            @Override
            public void onServiceStateChanged(ServiceState ss) {
                int state = ss.getState();
                if (state == mLastServiceState) return;
                mLastServiceState = state;
                String op = ss.getOperatorAlphaLong();
                addEvent(EventType.SERVICE_STATE,
                        "服务状态 → " + serviceStateDesc(state),
                        "State=" + state + (op != null ? "  运营商=" + op : ""));
            }

            @Override
            public void onSignalStrengthsChanged(SignalStrength ss) {
                CellSignalManager.SimSignalData d = getTargetSim();
                if (d == null) return;

                // Unified primary signal values
                int rsrp = d.nr_SSRSRP != Integer.MAX_VALUE ? d.nr_SSRSRP
                         : d.lte_RSRP  != Integer.MAX_VALUE ? d.lte_RSRP : Integer.MAX_VALUE;
                int pci  = d.nr_PCI    != Integer.MAX_VALUE ? d.nr_PCI
                         : d.lte_PCI   != Integer.MAX_VALUE ? d.lte_PCI  : Integer.MAX_VALUE;

                // Handover: serving PCI changed
                if (pci != Integer.MAX_VALUE && mLastPci != Integer.MAX_VALUE && pci != mLastPci) {
                    addEvent(EventType.HANDOVER,
                            "切换  PCI " + mLastPci + " → " + pci,
                            buildSignalDetail(d), d);
                }
                mLastPci = pci;

                // Significant RSRP change (≥5 dB)
                if (rsrp != Integer.MAX_VALUE && mLastRsrp != Integer.MAX_VALUE
                        && Math.abs(rsrp - mLastRsrp) >= 5) {
                    String dir = rsrp > mLastRsrp ? "↑" : "↓";
                    addEvent(EventType.SIGNAL_CHANGE,
                            "信号 " + dir + "  " + mLastRsrp + " → " + rsrp + " dBm",
                            buildSignalDetail(d), d);
                }
                if (rsrp != Integer.MAX_VALUE) mLastRsrp = rsrp;
            }

            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                if (networkType == mLastNetworkType) return;
                String from = networkTypeName(mLastNetworkType);
                String to   = networkTypeName(networkType);
                mLastNetworkType = networkType;
                addEvent(EventType.RAT_CHANGE,
                        "RAT变更  " + from + " → " + to,
                        "networkType=" + networkType);
            }
        };

        mTelMgr.listen(mPhoneListener,
                PhoneStateListener.LISTEN_SERVICE_STATE
                | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

        if (ContextCompat.checkSelfPermission(mCtx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                mLocMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, mLocListener);
            } catch (Exception ignored) {}
        }

        addEvent(EventType.SERVICE_STATE, "监听已开始 (SIM: " + simLabel() + ")", "");
    }

    public void stop() {
        if (!mRunning) return;
        mRunning = false;
        if (mPhoneListener != null) {
            mTelMgr.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
            mPhoneListener = null;
        }
        try { mLocMgr.removeUpdates(mLocListener); } catch (Exception ignored) {}
        addEvent(EventType.SERVICE_STATE, "监听已停止", "");
    }

    public boolean isRunning()          { return mRunning; }
    public void addListener(Listener l)    { mListeners.add(l); }
    public void removeListener(Listener l) { mListeners.remove(l); }
    public List<SignalingEvent> getEvents() { return Collections.unmodifiableList(mEvents); }
    public void clearEvents()              { mEvents.clear(); }

    public File exportCsv() {
        File dir = mCtx.getExternalFilesDir("signaling");
        if (dir == null) return null;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String fname = "sig_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date()) + ".csv";
        File f = new File(dir, fname);
        try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
            pw.println(SignalingEvent.csvHeader());
            for (SignalingEvent e : mEvents) pw.println(e.toCsvRow());
            return f;
        } catch (Exception e) { return null; }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void addEvent(EventType type, String summary, String detail) {
        addEvent(type, summary, detail, getTargetSim());
    }

    private void addEvent(EventType type, String summary, String detail,
                          CellSignalManager.SimSignalData d) {
        SignalingEvent e = new SignalingEvent(type, summary, detail, d, mLastLocation);
        mMainHandler.post(() -> {
            if (mEvents.size() >= MAX_EVENTS) mEvents.remove(0);
            mEvents.add(e);
            for (Listener l : mListeners) l.onNewEvent(e);
        });
    }

    private String buildSignalDetail(CellSignalManager.SimSignalData d) {
        StringBuilder sb = new StringBuilder();
        if (d.lte_RSRP != Integer.MAX_VALUE) {
            sb.append("RSRP=").append(d.lte_RSRP).append("dBm");
            if (d.lte_SINR != Integer.MAX_VALUE) sb.append(" SINR=").append(d.lte_SINR).append("dB");
            if (d.lte_RSRQ != Integer.MAX_VALUE) sb.append(" RSRQ=").append(d.lte_RSRQ).append("dB");
        }
        if (d.nr_SSRSRP != Integer.MAX_VALUE) {
            if (sb.length() > 0) sb.append(" | NR:");
            sb.append("SS-RSRP=").append(d.nr_SSRSRP).append("dBm");
            if (d.nr_SSSINR != Integer.MAX_VALUE) sb.append(" SS-SINR=").append(d.nr_SSSINR).append("dB");
        }
        if (d.lte_CI != Integer.MAX_VALUE) sb.append(" CI=").append(d.lte_CI);
        if (d.lte_PCI != Integer.MAX_VALUE) sb.append(" PCI=").append(d.lte_PCI);
        if (d.lte_TA != Integer.MAX_VALUE && d.lte_TA >= 0) {
            sb.append(" TA=").append(d.lte_TA).append("(≈").append((int)(d.lte_TA*78.12)).append("m)");
        }
        return sb.toString();
    }

    private CellSignalManager.SimSignalData getTargetSim() {
        if (MainActivity.signalManager == null) return null;
        List<CellSignalManager.SimSignalData> sims = MainActivity.signalManager.getSimDataList();
        if (sims.isEmpty()) return null;
        if (mTargetSimIndex < 0 || mTargetSimIndex >= sims.size()) return sims.get(0);
        return sims.get(mTargetSimIndex);
    }

    private TelephonyManager buildTelephonyManager() {
        TelephonyManager base = (TelephonyManager) mCtx.getSystemService(Context.TELEPHONY_SERVICE);
        if (mTargetSimIndex < 0) return base;
        try {
            SubscriptionManager sm = (SubscriptionManager)
                    mCtx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm == null) return base;
            List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
            if (subs != null && mTargetSimIndex < subs.size()) {
                int subId = subs.get(mTargetSimIndex).getSubscriptionId();
                return base.createForSubscriptionId(subId);
            }
        } catch (Exception ignored) {}
        return base;
    }

    private String simLabel() {
        if (mTargetSimIndex < 0) return "全部";
        if (MainActivity.signalManager == null) return "SIM " + (mTargetSimIndex + 1);
        List<CellSignalManager.SimSignalData> sims = MainActivity.signalManager.getSimDataList();
        if (mTargetSimIndex < sims.size()) return sims.get(mTargetSimIndex).simLabel;
        return "SIM " + (mTargetSimIndex + 1);
    }

    private static String serviceStateDesc(int state) {
        switch (state) {
            case ServiceState.STATE_IN_SERVICE:      return "在网";
            case ServiceState.STATE_OUT_OF_SERVICE:  return "无服务";
            case ServiceState.STATE_EMERGENCY_ONLY:  return "仅限紧急呼叫";
            case ServiceState.STATE_POWER_OFF:       return "飞行模式";
            default: return "未知(" + state + ")";
        }
    }

    private static String networkTypeName(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_LTE:     return "LTE";
            case TelephonyManager.NETWORK_TYPE_NR:      return "NR(5G)";
            case TelephonyManager.NETWORK_TYPE_UMTS:    return "UMTS";
            case TelephonyManager.NETWORK_TYPE_HSDPA:   return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSPA:    return "HSPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA:   return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPAP:   return "HSPA+";
            case TelephonyManager.NETWORK_TYPE_GSM:     return "GSM";
            case TelephonyManager.NETWORK_TYPE_GPRS:    return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE:    return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN: return "未知";
            default: return "type=" + type;
        }
    }
}
