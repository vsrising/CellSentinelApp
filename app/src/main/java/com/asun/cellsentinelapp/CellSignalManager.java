package com.asun.cellsentinelapp;

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

        // NR (5G)
        public int nr_PCI     = Integer.MAX_VALUE;
        public int nr_NRARFCN = Integer.MAX_VALUE;
        public int nr_SSRSRP  = Integer.MAX_VALUE;
        public int nr_SSRSRQ  = Integer.MAX_VALUE;
        public int nr_SSSINR  = Integer.MAX_VALUE;

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

        public final List<String> neighborCells = new ArrayList<>();

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
            mData.neighborCells.clear();

            for (CellInfo cellInfo : list) {
                if (cellInfo.isRegistered()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            && mData.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        try {
                            int subId = (int) CellInfo.class
                                    .getMethod("getSubscriptionId")
                                    .invoke(cellInfo);
                            if (subId != mData.subscriptionId) continue;
                        } catch (Exception ignored) {}
                    }
                    parseServingCell(cellInfo);
                } else {
                    String summary = formatNeighborCell(cellInfo);
                    if (summary != null) mData.neighborCells.add(summary);
                }
            }
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

        private String formatNeighborCell(CellInfo cellInfo) {
            if (cellInfo instanceof CellInfoLte) {
                CellInfoLte lte = (CellInfoLte) cellInfo;
                int pci    = lte.getCellIdentity().getPci();
                int earfcn = lte.getCellIdentity().getEarfcn();
                int rsrp   = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? lte.getCellSignalStrength().getRsrp()
                        : lte.getCellSignalStrength().getDbm();
                return String.format(Locale.US, "LTE   PCI:%-5s EARFCN:%-7s RSRP:%s dBm",
                        fmt(pci), fmt(earfcn), fmt(rsrp));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && cellInfo instanceof android.telephony.CellInfoNr) {
                return formatNeighborNr((android.telephony.CellInfoNr) cellInfo);
            } else if (cellInfo instanceof CellInfoWcdma) {
                CellInfoWcdma w = (CellInfoWcdma) cellInfo;
                int psc    = w.getCellIdentity().getPsc();
                int uarfcn = w.getCellIdentity().getUarfcn();
                int rssi   = w.getCellSignalStrength().getDbm();
                return String.format(Locale.US, "WCDMA PSC:%-5s UARFCN:%-7s RSSI:%s dBm",
                        fmt(psc), fmt(uarfcn), fmt(rssi));
            } else if (cellInfo instanceof CellInfoGsm) {
                CellInfoGsm g = (CellInfoGsm) cellInfo;
                int arfcn = g.getCellIdentity().getArfcn();
                int rssi  = g.getCellSignalStrength().getDbm();
                return String.format(Locale.US, "GSM   ARFCN:%-7s RSSI:%s dBm",
                        fmt(arfcn), fmt(rssi));
            }
            return null;
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        private String formatNeighborNr(android.telephony.CellInfoNr cellInfo) {
            android.telephony.CellIdentityNr id =
                    (android.telephony.CellIdentityNr) cellInfo.getCellIdentity();
            android.telephony.CellSignalStrengthNr ss =
                    (android.telephony.CellSignalStrengthNr) cellInfo.getCellSignalStrength();
            return String.format(Locale.US, "NR    PCI:%-5s NRARFCN:%-7s SS-RSRP:%s dBm",
                    fmt(id.getPci()), fmt(id.getNrarfcn()), fmt(ss.getSsRsrp()));
        }

        private void parseLte(CellInfoLte info) {
            mData.lte_MCC    = info.getCellIdentity().getMcc();
            mData.lte_MNC    = info.getCellIdentity().getMnc();
            mData.lte_CI     = info.getCellIdentity().getCi();
            mData.lte_PCI    = info.getCellIdentity().getPci();
            mData.lte_TAC    = info.getCellIdentity().getTac();
            mData.lte_EARFCN = info.getCellIdentity().getEarfcn();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mData.lte_RSRP = info.getCellSignalStrength().getRsrp();
                mData.lte_RSRQ = info.getCellSignalStrength().getRsrq();
                mData.lte_SINR = info.getCellSignalStrength().getRssnr();
            } else {
                mData.lte_RSRP = info.getCellSignalStrength().getDbm();
            }
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
