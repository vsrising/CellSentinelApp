package com.asun.cellsentinelapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
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

    /** One captured event, with full context at the moment it fired. */
    public static class SignalingEvent {
        public final long      timestamp;
        public final EventType type;
        public final String    simLabel;
        public final String    summary;
        public final String    detail;
        // Cell identity at capture time
        public final int       ci;
        public final int       enodebId;
        public final int       cellIndex;
        public final int       pci;
        // Signal values at capture time
        public final int       rsrp;
        public final int       sinr;
        public final int       rsrq;
        // GPS at capture time
        public final double    latitude;
        public final double    longitude;
        public final float     accuracy;

        SignalingEvent(EventType type, String simLabel, String summary, String detail,
                       int ci, int pci, int rsrp, int sinr, int rsrq,
                       Location location) {
            this.timestamp  = System.currentTimeMillis();
            this.type       = type;
            this.simLabel   = simLabel != null ? simLabel : "";
            this.summary    = summary;
            this.detail     = detail;
            this.ci         = ci;
            this.enodebId   = ci != Integer.MAX_VALUE && ci > 0 ? (ci >> 8) & 0xFFFFF : -1;
            this.cellIndex  = ci != Integer.MAX_VALUE && ci > 0 ? ci & 0xFF : -1;
            this.pci        = pci;
            this.rsrp       = rsrp;
            this.sinr       = sinr;
            this.rsrq       = rsrq;
            this.latitude   = location != null ? location.getLatitude()  : Double.NaN;
            this.longitude  = location != null ? location.getLongitude() : Double.NaN;
            this.accuracy   = location != null ? location.getAccuracy()  : -1;
        }

        public String formattedTime() {
            return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date(timestamp));
        }

        public String toCsvRow() {
            return String.format(Locale.US,
                    "%d,%s,%s,%s,\"%s\",\"%s\",%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    timestamp,
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date(timestamp)),
                    csvStr(simLabel),
                    type.name(),
                    summary.replace("\"", "\"\""),
                    detail.replace("\"", "\"\""),
                    fmtInt(ci), fmtInt(enodebId), fmtInt(cellIndex), fmtInt(pci),
                    Double.isNaN(latitude)  ? "N/A" : String.format(Locale.US, "%.6f", latitude),
                    Double.isNaN(longitude) ? "N/A" : String.format(Locale.US, "%.6f", longitude),
                    fmtInt(rsrp), fmtInt(sinr), fmtInt(rsrq));
        }

        private static String csvStr(String s) { return s == null || s.isEmpty() ? "N/A" : s; }
        private static String fmtInt(int v) { return v == Integer.MAX_VALUE || v < 0 ? "N/A" : String.valueOf(v); }

        public static String csvHeader() {
            return "timestamp,datetime,sim,type,summary,detail,ci,enodeb_id,cell_index,pci," +
                   "latitude,longitude,rsrp,sinr,rsrq";
        }
    }

    public interface Listener { void onNewEvent(SignalingEvent event); }

    private static final int MAX_EVENTS = 1000;

    private final Context         mCtx;
    private final Handler         mMainHandler = new Handler(Looper.getMainLooper());
    private final List<SignalingEvent> mEvents  = new ArrayList<>();
    private final List<Listener>  mListeners   = new ArrayList<>();

    // -1 = all SIMs, 0 = SIM1, 1 = SIM2
    private int mTargetSimIndex = -1;

    private TelephonyManager mTelMgr;
    private PhoneStateListener mPhoneListener;
    private boolean            mRunning = false;

    // GPS tracking
    private LocationManager mLocMgr;
    private Location        mLastLocation;
    private final LocationListener mLocListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location l) { mLastLocation = l; }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(@NonNull String p) {}
        @Override public void onProviderDisabled(@NonNull String p) {}
    };

    // State tracking for delta detection
    private int    mLastNetworkType  = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private int    mLastServiceState = -1;
    private int    mLastRsrp         = Integer.MAX_VALUE;
    private int    mLastPci          = Integer.MAX_VALUE;

    public SignalingLogManager(Context ctx) {
        mCtx    = ctx.getApplicationContext();
        mTelMgr = (TelephonyManager) mCtx.getSystemService(Context.TELEPHONY_SERVICE);
        mLocMgr = (LocationManager) mCtx.getSystemService(Context.LOCATION_SERVICE);
    }

    /** -1 = all SIMs, 0 = SIM 1, 1 = SIM 2. Restarts listener if already running. */
    public void setTargetSimIndex(int index) {
        if (mTargetSimIndex == index) return;
        mTargetSimIndex = index;
        if (mRunning) { stop(); start(); }
    }

    public int getTargetSimIndex() { return mTargetSimIndex; }

    public void start() {
        if (mRunning) return;
        mRunning = true;

        // Pick TelephonyManager for target SIM
        mTelMgr = buildTelephonyManager();

        mPhoneListener = new PhoneStateListener() {
            @Override
            public void onServiceStateChanged(ServiceState ss) {
                int state = ss.getState();
                if (state == mLastServiceState) return;
                mLastServiceState = state;
                addEvent(EventType.SERVICE_STATE,
                        "服务状态 → " + serviceStateDesc(state),
                        "State=" + state + "  Operator=" + ss.getOperatorAlphaLong());
            }

            @Override
            public void onSignalStrengthsChanged(SignalStrength ss) {
                CellSignalManager.SimSignalData d = getTargetSim();
                if (d == null) return;

                int rsrp = d.lte_RSRP != Integer.MAX_VALUE ? d.lte_RSRP : d.nr_SSRSRP;
                int sinr = d.lte_SINR != Integer.MAX_VALUE ? d.lte_SINR : d.nr_SSSINR;
                int rsrq = d.lte_RSRQ != Integer.MAX_VALUE ? d.lte_RSRQ : d.nr_SSRSRQ;
                int pci  = d.lte_PCI  != Integer.MAX_VALUE ? d.lte_PCI  : d.nr_PCI;
                int ci   = d.lte_CI   != Integer.MAX_VALUE ? d.lte_CI   : Integer.MAX_VALUE;

                // Handover: PCI changed
                if (pci != Integer.MAX_VALUE && mLastPci != Integer.MAX_VALUE && pci != mLastPci) {
                    addEvent(EventType.HANDOVER,
                            "切换  PCI " + mLastPci + " → " + pci,
                            "RSRP=" + fmt(rsrp) + "dBm  CI=" + fmt(ci),
                            ci, pci, rsrp, sinr, rsrq, d.simLabel);
                }
                mLastPci = pci;

                // Significant RSRP change (≥5 dB)
                if (rsrp != Integer.MAX_VALUE && mLastRsrp != Integer.MAX_VALUE
                        && Math.abs(rsrp - mLastRsrp) >= 5) {
                    String dir = rsrp > mLastRsrp ? "↑" : "↓";
                    addEvent(EventType.SIGNAL_CHANGE,
                            "信号 " + dir + "  " + mLastRsrp + " → " + rsrp + " dBm",
                            "PCI=" + fmt(pci) + "  CI=" + fmt(ci),
                            ci, pci, rsrp, sinr, rsrq, d.simLabel);
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
                        "RAT 变更  " + from + " → " + to,
                        "networkType=" + networkType);
            }
        };

        mTelMgr.listen(mPhoneListener,
                PhoneStateListener.LISTEN_SERVICE_STATE
                | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

        // Start GPS tracking for event context
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

    public boolean isRunning() { return mRunning; }
    public void addListener(Listener l)    { mListeners.add(l); }
    public void removeListener(Listener l) { mListeners.remove(l); }
    public List<SignalingEvent> getEvents()  { return Collections.unmodifiableList(mEvents); }
    public void clearEvents()              { mEvents.clear(); }

    /** Export to CSV in the app's external drivetest folder. Returns the file or null on failure. */
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

    // ── Internals ────────────────────────────────────────────────────────────

    private void addEvent(EventType type, String summary, String detail) {
        CellSignalManager.SimSignalData d = getTargetSim();
        int ci   = d != null && d.lte_CI  != Integer.MAX_VALUE ? d.lte_CI  : Integer.MAX_VALUE;
        int pci  = d != null && d.lte_PCI != Integer.MAX_VALUE ? d.lte_PCI : Integer.MAX_VALUE;
        int rsrp = d != null && d.lte_RSRP!= Integer.MAX_VALUE ? d.lte_RSRP: Integer.MAX_VALUE;
        int sinr = d != null && d.lte_SINR!= Integer.MAX_VALUE ? d.lte_SINR: Integer.MAX_VALUE;
        int rsrq = d != null && d.lte_RSRQ!= Integer.MAX_VALUE ? d.lte_RSRQ: Integer.MAX_VALUE;
        String sl = d != null ? d.simLabel : "";
        addEvent(type, summary, detail, ci, pci, rsrp, sinr, rsrq, sl);
    }

    private void addEvent(EventType type, String summary, String detail,
                          int ci, int pci, int rsrp, int sinr, int rsrq, String simLabel) {
        SignalingEvent e = new SignalingEvent(type, simLabel, summary, detail,
                ci, pci, rsrp, sinr, rsrq, mLastLocation);
        mMainHandler.post(() -> {
            if (mEvents.size() >= MAX_EVENTS) mEvents.remove(0);
            mEvents.add(e);
            for (Listener l : mListeners) l.onNewEvent(e);
        });
    }

    /** Get the SimSignalData for the targeted SIM (or first available if -1). */
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

    private static String fmt(int v) { return v == Integer.MAX_VALUE ? "N/A" : String.valueOf(v); }

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
            case TelephonyManager.NETWORK_TYPE_NR:      return "NR";
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
