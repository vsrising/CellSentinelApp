package com.asun.cellsentinelapp.manager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class CellSignalManager {

    private static final int REFRESH_INTERVAL_MS = 1000;

    public interface UpdateListener {
        void onSignalUpdated();
    }

    /** Structured neighbor cell measurement. */
    public static class NeighborCell {
        public final String rat;
        public final int    pci;
        public final int    earfcn;
        public final int    rsrp;
        public final int    rsrq;
        public final int    sinr;

        NeighborCell(String rat, int pci, int earfcn, int rsrp, int rsrq, int sinr) {
            this.rat = rat; this.pci = pci; this.earfcn = earfcn;
            this.rsrp = rsrp; this.rsrq = rsrq; this.sinr = sinr;
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "%-5s PCI:%-5s EARFCN:%-7s RSRP:%-7s RSRQ:%-6s%s",
                    rat, nc(pci),  nc(earfcn),
                    rsrp == Integer.MAX_VALUE ? "N/A" : rsrp + "dBm",
                    rsrq == Integer.MAX_VALUE ? "N/A" : rsrq + "dB",
                    sinr != Integer.MAX_VALUE ? "  SINR:" + sinr + "dB" : "");
        }

        private static String nc(int v) { return v == Integer.MAX_VALUE ? "N/A" : String.valueOf(v); }
    }

    public static class SimSignalData {
        public final int subscriptionId;
        public final String simLabel;
        public String operatorName = "";

        // LTE
        public int lte_MCC    = Integer.MAX_VALUE;
        public int lte_MNC    = Integer.MAX_VALUE;
        public int lte_CI     = Integer.MAX_VALUE;
        public int lte_PCI    = Integer.MAX_VALUE;
        public int lte_TAC    = Integer.MAX_VALUE;
        public int lte_EARFCN = Integer.MAX_VALUE;
        public int lte_RSRP   = Integer.MAX_VALUE;
        public int lte_RSRQ   = Integer.MAX_VALUE;
        public int lte_SINR   = Integer.MAX_VALUE;

        // LTE additional metrics
        public int lte_TA        = Integer.MAX_VALUE;  // Timing Advance (each unit ≈78m one-way)
        public int lte_CQI       = Integer.MAX_VALUE;  // Channel Quality Indicator (0-15)
        public int lte_bandwidth = Integer.MAX_VALUE;  // Channel bandwidth in kHz (API 28+)

        // NR (5G)
        public int nr_PCI     = Integer.MAX_VALUE;
        public int nr_NRARFCN = Integer.MAX_VALUE;
        public int nr_SSRSRP  = Integer.MAX_VALUE;
        public int nr_SSRSRQ  = Integer.MAX_VALUE;
        public int nr_SSSINR  = Integer.MAX_VALUE;
        public int nr_TAC     = Integer.MAX_VALUE;
        public int nr_CSIRSRP = Integer.MAX_VALUE;
        public int nr_CSIRSRQ = Integer.MAX_VALUE;
        public int nr_CSISINR = Integer.MAX_VALUE;

        // WCDMA
        public int wcdma_MCC  = Integer.MAX_VALUE;
        public int wcdma_MNC  = Integer.MAX_VALUE;
        public int wcdma_LAC  = Integer.MAX_VALUE;
        public int wcdma_CID  = Integer.MAX_VALUE;
        public int wcdma_PSC  = Integer.MAX_VALUE;
        public int wcdma_RSSI = Integer.MAX_VALUE;

        // GSM
        public int gsm_MCC  = Integer.MAX_VALUE;
        public int gsm_MNC  = Integer.MAX_VALUE;
        public int gsm_LAC  = Integer.MAX_VALUE;
        public int gsm_CID  = Integer.MAX_VALUE;
        public int gsm_RSSI = Integer.MAX_VALUE;

        // CDMA
        public int cdma_SID   = Integer.MAX_VALUE;
        public int cdma_NID   = Integer.MAX_VALUE;
        public int cdma_BSID  = Integer.MAX_VALUE;
        public int cdma_RxPwr = Integer.MAX_VALUE;
        public int cdma_EcIo  = Integer.MAX_VALUE;

        public final List<String>       neighborCells    = new ArrayList<>();
        public final List<NeighborCell> neighborCellData = new ArrayList<>();

        SimSignalData(int subId, String label) {
            this.subscriptionId = subId;
            this.simLabel = label;
        }
    }

    private final Context mContext;
    private final UpdateListener mListener;
    private final List<SimSignalData> mSimDataList = new ArrayList<>();
    private final List<TelephonyManager> mTMList = new ArrayList<>();
    private final List<PhoneStateMonitor> mMonitorList = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            for (PhoneStateMonitor m : mMonitorList) {
                m.triggerRefresh();
            }
            mHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @SuppressLint("MissingPermission")
    public CellSignalManager(Context context, UpdateListener listener) {
        mContext = context;
        mListener = listener;

        TelephonyManager baseTM = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        boolean hasPhonePermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;

        List<SubscriptionInfo> subs = null;
        if (hasPhonePermission) {
            SubscriptionManager sm = (SubscriptionManager) context.getSystemService(
                    Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            subs = sm.getActiveSubscriptionInfoList();
        }

        if (subs != null && !subs.isEmpty()) {
            for (SubscriptionInfo sub : subs) {
                int subId = sub.getSubscriptionId();
                String label = "SIM " + (sub.getSimSlotIndex() + 1);
                CharSequence name = sub.getDisplayName();
                if (name != null && name.length() > 0) label += " (" + name + ")";

                SimSignalData data = new SimSignalData(subId, label);
                mSimDataList.add(data);

                TelephonyManager tm = baseTM.createForSubscriptionId(subId);
                data.operatorName = tm.getNetworkOperatorName();
                mTMList.add(tm);

                PhoneStateMonitor monitor = new PhoneStateMonitor(data, tm);
                mMonitorList.add(monitor);
                tm.listen(monitor,
                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
                        PhoneStateListener.LISTEN_CELL_INFO);
            }
        } else {
            SimSignalData data = new SimSignalData(SubscriptionManager.INVALID_SUBSCRIPTION_ID, "SIM 1");
            data.operatorName = baseTM.getNetworkOperatorName();
            mSimDataList.add(data);
            mTMList.add(baseTM);
            PhoneStateMonitor monitor = new PhoneStateMonitor(data, baseTM);
            mMonitorList.add(monitor);
            baseTM.listen(monitor,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
                    PhoneStateListener.LISTEN_CELL_INFO);
        }

        mHandler.postDelayed(mRefreshRunnable, REFRESH_INTERVAL_MS);
    }

    public void release() {
        mHandler.removeCallbacks(mRefreshRunnable);
        for (int i = 0; i < mTMList.size(); i++) {
            mTMList.get(i).listen(mMonitorList.get(i), PhoneStateListener.LISTEN_NONE);
        }
        mSimDataList.clear();
        mTMList.clear();
        mMonitorList.clear();
    }

    public List<SimSignalData> getSimDataList() {
        return Collections.unmodifiableList(mSimDataList);
    }

    public int getSimCount() {
        return mSimDataList.size();
    }

    static String fmt(int val) {
        return val == Integer.MAX_VALUE ? "N/A" : String.valueOf(val);
    }

    private void notifyUpdate() {
        if (mListener != null) {
            mHandler.post(mListener::onSignalUpdated);
        }
    }

    // -----------------------------------------------------------------------
    // Inner class: per-SIM phone state monitor
    // -----------------------------------------------------------------------
    private class PhoneStateMonitor extends PhoneStateListener {
        private final SimSignalData mData;
        private final TelephonyManager mTM;

        PhoneStateMonitor(SimSignalData data, TelephonyManager tm) {
            mData = data;
            mTM = tm;
        }

        void triggerRefresh() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                triggerRefreshQ();
            } else {
                refreshFromAllCellInfo();
                notifyUpdate();
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        @SuppressLint("MissingPermission")
        private void triggerRefreshQ() {
            mTM.requestCellInfoUpdate(
                    mContext.getMainExecutor(),
                    new TelephonyManager.CellInfoCallback() {
                        @Override
                        public void onCellInfo(List<CellInfo> cellInfoList) {
                            parseCellInfoList(cellInfoList);
                            mData.operatorName = mTM.getNetworkOperatorName();
                            notifyUpdate();
                        }
                    });
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            refreshFromAllCellInfo();
            mData.operatorName = mTM.getNetworkOperatorName();
            notifyUpdate();
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCellInfoChanged(List<CellInfo> cellInfoList) {
            super.onCellInfoChanged(cellInfoList);
            if (cellInfoList != null) {
                parseCellInfoList(cellInfoList);
                mData.operatorName = mTM.getNetworkOperatorName();
            }
            notifyUpdate();
        }

        @SuppressLint("MissingPermission")
        private void refreshFromAllCellInfo() {
            List<CellInfo> list = mTM.getAllCellInfo();
            if (list != null) parseCellInfoList(list);
        }

        private void parseCellInfoList(List<CellInfo> list) {
            // Reset all serving-cell fields so stale data from the previous parse never bleeds
            // into the UI when the cell list no longer contains that RAT.
            clearServingData();
            mData.neighborCells.clear();
            mData.neighborCellData.clear();

            for (CellInfo cellInfo : list) {
                // Determine whether this cell belongs to this SIM's subscription.
                // Android R+ exposes CellInfo.getSubscriptionId() via public API.
                boolean subIdMatch = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && mData.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    try {
                        int subId = (int) CellInfo.class
                                .getMethod("getSubscriptionId")
                                .invoke(cellInfo);
                        subIdMatch = (subId == mData.subscriptionId);
                    } catch (Exception ignored) {}
                }

                if (cellInfo.isRegistered()) {
                    if (!subIdMatch) continue;
                    // Pre-R fallback: filter by this SIM's reported RAT to prevent
                    // the other SIM's registered cell from being parsed into our data.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                            && mData.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                            && !ratMatchesThisSim(cellInfo)) {
                        continue;
                    }
                    parseServingCell(cellInfo);
                } else {
                    if (!subIdMatch) continue; // R+: keep neighbor list per-SIM
                    NeighborCell nc = buildNeighborCell(cellInfo);
                    if (nc != null) {
                        mData.neighborCellData.add(nc);
                        mData.neighborCells.add(nc.toString());
                    }
                }
            }
        }

        private void clearServingData() {
            mData.lte_MCC = mData.lte_MNC = mData.lte_CI = mData.lte_PCI = Integer.MAX_VALUE;
            mData.lte_TAC = mData.lte_EARFCN = mData.lte_RSRP = mData.lte_RSRQ = mData.lte_SINR = Integer.MAX_VALUE;
            mData.lte_TA  = mData.lte_CQI = mData.lte_bandwidth = Integer.MAX_VALUE;
            mData.nr_PCI  = mData.nr_NRARFCN = mData.nr_SSRSRP = mData.nr_SSRSRQ = mData.nr_SSSINR = Integer.MAX_VALUE;
            mData.nr_TAC  = mData.nr_CSIRSRP = mData.nr_CSIRSRQ = mData.nr_CSISINR = Integer.MAX_VALUE;
            mData.wcdma_MCC = mData.wcdma_MNC = mData.wcdma_LAC = mData.wcdma_CID
                            = mData.wcdma_PSC = mData.wcdma_RSSI = Integer.MAX_VALUE;
            mData.gsm_MCC = mData.gsm_MNC = mData.gsm_LAC = mData.gsm_CID = mData.gsm_RSSI = Integer.MAX_VALUE;
            mData.cdma_SID = mData.cdma_NID = mData.cdma_BSID = mData.cdma_RxPwr = mData.cdma_EcIo = Integer.MAX_VALUE;
        }

        @SuppressLint("MissingPermission")
        private boolean ratMatchesThisSim(CellInfo cellInfo) {
            int nt = mTM.getNetworkType();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && cellInfo instanceof android.telephony.CellInfoNr) {
                return nt == TelephonyManager.NETWORK_TYPE_NR;
            }
            if (cellInfo instanceof CellInfoLte) {
                return nt == TelephonyManager.NETWORK_TYPE_LTE
                        || nt == TelephonyManager.NETWORK_TYPE_NR; // NSA 5G anchor
            }
            if (cellInfo instanceof CellInfoWcdma) {
                return nt == TelephonyManager.NETWORK_TYPE_UMTS
                        || nt == TelephonyManager.NETWORK_TYPE_HSPA
                        || nt == TelephonyManager.NETWORK_TYPE_HSPAP
                        || nt == TelephonyManager.NETWORK_TYPE_HSDPA
                        || nt == TelephonyManager.NETWORK_TYPE_HSUPA;
            }
            if (cellInfo instanceof CellInfoGsm) {
                return nt == TelephonyManager.NETWORK_TYPE_GSM
                        || nt == TelephonyManager.NETWORK_TYPE_GPRS
                        || nt == TelephonyManager.NETWORK_TYPE_EDGE;
            }
            if (cellInfo instanceof CellInfoCdma) {
                return nt == TelephonyManager.NETWORK_TYPE_CDMA
                        || nt == TelephonyManager.NETWORK_TYPE_EVDO_0
                        || nt == TelephonyManager.NETWORK_TYPE_EVDO_A
                        || nt == TelephonyManager.NETWORK_TYPE_EVDO_B
                        || nt == TelephonyManager.NETWORK_TYPE_1xRTT;
            }
            return true;
        }

        private void parseServingCell(CellInfo cellInfo) {
            if (cellInfo instanceof CellInfoLte) {
                parseLte((CellInfoLte) cellInfo);
            } else if (cellInfo instanceof CellInfoCdma) {
                parseCdma((CellInfoCdma) cellInfo);
            } else if (cellInfo instanceof CellInfoGsm) {
                parseGsm((CellInfoGsm) cellInfo);
            } else if (cellInfo instanceof CellInfoWcdma) {
                parseWcdma((CellInfoWcdma) cellInfo);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && cellInfo instanceof android.telephony.CellInfoNr) {
                parseNr((android.telephony.CellInfoNr) cellInfo);
            }
        }

        private NeighborCell buildNeighborCell(CellInfo cellInfo) {
            if (cellInfo instanceof CellInfoLte) {
                CellInfoLte lte = (CellInfoLte) cellInfo;
                int pci    = lte.getCellIdentity().getPci();
                int earfcn = lte.getCellIdentity().getEarfcn();
                int rsrp   = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? lte.getCellSignalStrength().getRsrp()
                        : lte.getCellSignalStrength().getDbm();
                int rsrq   = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? lte.getCellSignalStrength().getRsrq() : Integer.MAX_VALUE;
                return new NeighborCell("LTE", pci, earfcn, rsrp, rsrq, Integer.MAX_VALUE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && cellInfo instanceof android.telephony.CellInfoNr) {
                android.telephony.CellInfoNr nr = (android.telephony.CellInfoNr) cellInfo;
                android.telephony.CellIdentityNr id =
                        (android.telephony.CellIdentityNr) nr.getCellIdentity();
                android.telephony.CellSignalStrengthNr ss =
                        (android.telephony.CellSignalStrengthNr) nr.getCellSignalStrength();
                return new NeighborCell("NR", id.getPci(), id.getNrarfcn(),
                        ss.getSsRsrp(), ss.getSsRsrq(), ss.getSsSinr());
            } else if (cellInfo instanceof CellInfoWcdma) {
                CellInfoWcdma w = (CellInfoWcdma) cellInfo;
                return new NeighborCell("WCDMA", w.getCellIdentity().getPsc(),
                        w.getCellIdentity().getUarfcn(),
                        w.getCellSignalStrength().getDbm(), Integer.MAX_VALUE, Integer.MAX_VALUE);
            } else if (cellInfo instanceof CellInfoGsm) {
                CellInfoGsm g = (CellInfoGsm) cellInfo;
                return new NeighborCell("GSM", Integer.MAX_VALUE,
                        g.getCellIdentity().getArfcn(),
                        g.getCellSignalStrength().getDbm(), Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
            return null;
        }

        private void parseLte(CellInfoLte info) {
            mData.lte_MCC    = info.getCellIdentity().getMcc();
            mData.lte_MNC    = info.getCellIdentity().getMnc();
            mData.lte_CI     = info.getCellIdentity().getCi();
            mData.lte_PCI    = info.getCellIdentity().getPci();
            mData.lte_TAC    = info.getCellIdentity().getTac();
            mData.lte_EARFCN = info.getCellIdentity().getEarfcn();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try { mData.lte_bandwidth = info.getCellIdentity().getBandwidth(); } catch (Exception ignored) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mData.lte_RSRP = info.getCellSignalStrength().getRsrp();
                mData.lte_RSRQ = info.getCellSignalStrength().getRsrq();
                mData.lte_SINR = info.getCellSignalStrength().getRssnr();
            } else {
                mData.lte_RSRP = info.getCellSignalStrength().getDbm();
            }
            try { mData.lte_TA  = info.getCellSignalStrength().getTimingAdvance(); } catch (Exception ignored) {}
            try { mData.lte_CQI = info.getCellSignalStrength().getCqi(); } catch (Exception ignored) {}
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        private void parseNr(android.telephony.CellInfoNr info) {
            android.telephony.CellIdentityNr id =
                    (android.telephony.CellIdentityNr) info.getCellIdentity();
            android.telephony.CellSignalStrengthNr ss =
                    (android.telephony.CellSignalStrengthNr) info.getCellSignalStrength();
            mData.nr_PCI     = id.getPci();
            mData.nr_NRARFCN = id.getNrarfcn();
            mData.nr_SSRSRP  = ss.getSsRsrp();
            mData.nr_SSRSRQ  = ss.getSsRsrq();
            mData.nr_SSSINR  = ss.getSsSinr();
            try { mData.nr_TAC = id.getTac(); } catch (Exception ignored) {}
            try {
                mData.nr_CSIRSRP = ss.getCsiRsrp();
                mData.nr_CSIRSRQ = ss.getCsiRsrq();
                mData.nr_CSISINR = ss.getCsiSinr();
            } catch (Exception ignored) {}
        }

        private void parseCdma(CellInfoCdma info) {
            mData.cdma_SID   = info.getCellIdentity().getSystemId();
            mData.cdma_NID   = info.getCellIdentity().getNetworkId();
            mData.cdma_BSID  = info.getCellIdentity().getBasestationId();
            mData.cdma_RxPwr = info.getCellSignalStrength().getCdmaDbm();
            mData.cdma_EcIo  = info.getCellSignalStrength().getCdmaEcio();
        }

        private void parseGsm(CellInfoGsm info) {
            mData.gsm_MCC  = info.getCellIdentity().getMcc();
            mData.gsm_MNC  = info.getCellIdentity().getMnc();
            mData.gsm_CID  = info.getCellIdentity().getCid();
            mData.gsm_LAC  = info.getCellIdentity().getLac();
            mData.gsm_RSSI = info.getCellSignalStrength().getDbm();
        }

        private void parseWcdma(CellInfoWcdma info) {
            mData.wcdma_MCC  = info.getCellIdentity().getMcc();
            mData.wcdma_MNC  = info.getCellIdentity().getMnc();
            mData.wcdma_CID  = info.getCellIdentity().getCid();
            mData.wcdma_LAC  = info.getCellIdentity().getLac();
            mData.wcdma_PSC  = info.getCellIdentity().getPsc();
            mData.wcdma_RSSI = info.getCellSignalStrength().getDbm();
        }
    }
}
