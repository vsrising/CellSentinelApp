package com.asun.cellsentinelapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

public class SignalContainerFragment extends Fragment {

    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private SectionsPagerAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_signal_container, container, false);
        mViewPager = root.findViewById(R.id.container);
        mTabLayout = root.findViewById(R.id.tabs);
        refreshAdapter();
        return root;
    }

    public void refreshAdapter() {
        if (mViewPager == null) return;
        mAdapter = new SectionsPagerAdapter(getChildFragmentManager());
        mViewPager.setAdapter(mAdapter);
        mTabLayout.setupWithViewPager(mViewPager);
    }

    public void notifySignalUpdate() {
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
    }

    // ── SimFragment ──────────────────────────────────────────────────────────

    public static class SimFragment extends Fragment {

        private static final String ARG_SIM_INDEX = "sim_index";

        // Quality color thresholds (mirrors DriveTestFragment)
        private static final int[] RSRP_THRESH = { -80, -95, -105, -115 };
        private static final int[] SINR_THRESH = { 20, 10, 0, -3 };
        private static final int[] RSRQ_THRESH = { -6, -9, -12, -15 };
        private static final int[] Q_COLORS = {
                0xFF00B050, 0xFF92D050, 0xFFFFFF00, 0xFFFF8C00, 0xFFFF0000 };

        public static SimFragment newInstance(int index) {
            SimFragment f = new SimFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SIM_INDEX, index);
            f.setArguments(args);
            return f;
        }

        private int simIndex() {
            return getArguments() != null ? getArguments().getInt(ARG_SIM_INDEX, 0) : 0;
        }

        // View references (null until onCreateView)
        private SignalGaugeView mGaugeRsrp;
        private SignalGaugeView mGaugeSinr;
        private SignalGaugeView mGaugeRsrq;
        private SignalChartView mChart;
        private TextView        mTvOperator;
        private TextView        mTvRatBadge;
        private TextView        mTvCiVal;
        private TextView        mTvPciVal;
        private TextView        mTvTacVal;
        private TextView        mTvMccMncVal;
        private TextView        mTvEarfcnVal;
        private TextView        mTvBandVal;
        private Button          mBtnPing;
        private TextView        mTvPingResult;
        private TextView        mTvNbrHeader;
        private TextView        mTvNeighbors;

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            View root = inflater.inflate(R.layout.fragment_sim, container, false);

            mGaugeRsrp   = root.findViewById(R.id.gauge_rsrp);
            mGaugeSinr   = root.findViewById(R.id.gauge_sinr);
            mGaugeRsrq   = root.findViewById(R.id.gauge_rsrq);
            mChart        = root.findViewById(R.id.chart_signal);
            mTvOperator   = root.findViewById(R.id.tv_operator);
            mTvRatBadge   = root.findViewById(R.id.tv_rat_badge);
            mTvCiVal      = root.findViewById(R.id.tv_ci_val);
            mTvPciVal     = root.findViewById(R.id.tv_pci_val);
            mTvTacVal     = root.findViewById(R.id.tv_tac_val);
            mTvMccMncVal  = root.findViewById(R.id.tv_mcc_mnc_val);
            mTvEarfcnVal  = root.findViewById(R.id.tv_earfcn_val);
            mTvBandVal    = root.findViewById(R.id.tv_band_val);
            mBtnPing      = root.findViewById(R.id.btn_ping);
            mTvPingResult = root.findViewById(R.id.tv_ping_result);
            mTvNbrHeader  = root.findViewById(R.id.tv_nbr_header);
            mTvNeighbors  = root.findViewById(R.id.tv_neighbors);

            mGaugeRsrp.configure("RSRP", "dBm", -140, -44);
            mGaugeSinr.configure("SINR", "dB",  -20,  30);
            mGaugeRsrq.configure("RSRQ", "dB",  -20,   0);

            mBtnPing.setOnClickListener(v -> startPingTest());

            updateView();
            return root;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            mGaugeRsrp = null; mGaugeSinr = null; mGaugeRsrq = null;
            mChart = null; mBtnPing = null;
        }

        public void updateView() {
            if (mGaugeRsrp == null) return; // views not yet inflated
            if (MainActivity.signalManager == null) {
                mTvOperator.setText("等待权限...");
                mTvRatBadge.setText("—");
                return;
            }
            List<CellSignalManager.SimSignalData> simList =
                    MainActivity.signalManager.getSimDataList();
            int idx = simIndex();
            if (idx >= simList.size()) {
                mTvOperator.setText("暂无 SIM 数据");
                return;
            }
            CellSignalManager.SimSignalData d = simList.get(idx);

            String op = (d.operatorName == null || d.operatorName.isEmpty())
                    ? "未知运营商" : d.operatorName;
            mTvOperator.setText(op);

            // Determine active RAT and primary signal metrics
            int rsrp, sinr, rsrq, pci, ci, tac, mcc, mnc, earfcn;
            String rat;
            if (d.nr_SSRSRP != Integer.MAX_VALUE) {
                rat   = "5G NR";
                rsrp  = d.nr_SSRSRP;
                sinr  = d.nr_SSSINR;
                rsrq  = d.nr_SSRSRQ;
                pci   = d.nr_PCI;
                ci    = d.lte_CI;   // NSA: anchor LTE CI
                tac   = d.lte_TAC;
                mcc   = d.lte_MCC;
                mnc   = d.lte_MNC;
                earfcn = d.nr_NRARFCN;
            } else if (d.lte_RSRP != Integer.MAX_VALUE) {
                rat   = "LTE";
                rsrp  = d.lte_RSRP;
                sinr  = d.lte_SINR;
                rsrq  = d.lte_RSRQ;
                pci   = d.lte_PCI;
                ci    = d.lte_CI;
                tac   = d.lte_TAC;
                mcc   = d.lte_MCC;
                mnc   = d.lte_MNC;
                earfcn = d.lte_EARFCN;
            } else if (d.wcdma_CID != Integer.MAX_VALUE) {
                rat   = "WCDMA";
                rsrp  = d.wcdma_RSSI;
                sinr  = Integer.MAX_VALUE;
                rsrq  = Integer.MAX_VALUE;
                pci   = d.wcdma_PSC;
                ci    = d.wcdma_CID;
                tac   = d.wcdma_LAC;
                mcc   = d.wcdma_MCC;
                mnc   = d.wcdma_MNC;
                earfcn = Integer.MAX_VALUE;
            } else if (d.gsm_CID != Integer.MAX_VALUE) {
                rat   = "GSM";
                rsrp  = d.gsm_RSSI;
                sinr  = Integer.MAX_VALUE;
                rsrq  = Integer.MAX_VALUE;
                pci   = Integer.MAX_VALUE;
                ci    = d.gsm_CID;
                tac   = d.gsm_LAC;
                mcc   = d.gsm_MCC;
                mnc   = d.gsm_MNC;
                earfcn = Integer.MAX_VALUE;
            } else {
                rat   = "无信号";
                rsrp  = Integer.MAX_VALUE;
                sinr  = Integer.MAX_VALUE;
                rsrq  = Integer.MAX_VALUE;
                pci   = Integer.MAX_VALUE;
                ci    = Integer.MAX_VALUE;
                tac   = Integer.MAX_VALUE;
                mcc   = Integer.MAX_VALUE;
                mnc   = Integer.MAX_VALUE;
                earfcn = Integer.MAX_VALUE;
            }

            // RAT badge color
            mTvRatBadge.setText(rat);
            int badgeColor;
            switch (rat) {
                case "5G NR":  badgeColor = 0xFF00796B; break;
                case "LTE":    badgeColor = 0xFF1565C0; break;
                case "WCDMA":  badgeColor = 0xFF6A1B9A; break;
                case "GSM":    badgeColor = 0xFF4E342E; break;
                default:       badgeColor = 0xFF888888; break;
            }
            mTvRatBadge.setBackgroundColor(badgeColor);

            // Gauges
            mGaugeRsrp.setValue(rsrp, qualityColor(rsrp, RSRP_THRESH));
            mGaugeSinr.setValue(sinr, qualityColor(sinr, SINR_THRESH));
            mGaugeRsrq.setValue(rsrq, qualityColor(rsrq, RSRQ_THRESH));

            // Cell identity card
            mTvCiVal.setText(ci != Integer.MAX_VALUE
                    ? String.format(Locale.US, "%d (eNB:%d)", ci, (ci >> 8) & 0xFFFFF)
                    : "N/A");
            mTvPciVal.setText(fmt(pci));
            mTvTacVal.setText(fmt(tac));
            mTvMccMncVal.setText(mcc != Integer.MAX_VALUE && mnc != Integer.MAX_VALUE
                    ? mcc + "/" + mnc : "N/A");
            mTvEarfcnVal.setText(fmt(earfcn));

            // Band decode
            String band = "N/A";
            if (rat.equals("5G NR"))
                band = nvl(EarfcnDecoder.decodeNr(earfcn));
            else
                band = nvl(EarfcnDecoder.decodeLte(earfcn));
            mTvBandVal.setText(band);

            // History chart
            mChart.addSample(rsrp, sinr, rsrq);

            // Neighbor cells
            List<String> nbrs = d.neighborCells;
            mTvNbrHeader.setText("邻区 (" + nbrs.size() + ")");
            if (nbrs.isEmpty()) {
                mTvNeighbors.setText("—");
            } else {
                StringBuilder sb = new StringBuilder();
                for (String s : nbrs) sb.append(s).append("\n");
                if (sb.length() > 0) sb.setLength(sb.length() - 1);
                mTvNeighbors.setText(sb.toString());
            }
        }

        // ── Ping test ─────────────────────────────────────────────────────

        private void startPingTest() {
            if (mBtnPing == null) return;
            mBtnPing.setEnabled(false);
            mTvPingResult.setText("测试中...");
            String serverUrl = SettingUtils.getServerUrl(requireContext());
            Handler uiHandler = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                long[] rtts = new long[3];
                int ok = 0;
                for (int i = 0; i < 3; i++) {
                    long t = System.currentTimeMillis();
                    try {
                        HttpURLConnection conn = (HttpURLConnection)
                                new URL(serverUrl).openConnection();
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(2000);
                        conn.setRequestMethod("HEAD");
                        conn.connect();
                        rtts[i] = System.currentTimeMillis() - t;
                        conn.disconnect();
                        ok++;
                    } catch (Exception e) {
                        rtts[i] = -1;
                    }
                    try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                }
                String result;
                if (ok == 0) {
                    result = "连接失败 (检查服务器地址)";
                } else {
                    long sum = 0, min = Long.MAX_VALUE, max = 0;
                    for (long r : rtts) {
                        if (r >= 0) { sum += r; if (r < min) min = r; if (r > max) max = r; }
                    }
                    result = String.format(Locale.US,
                            "最小 %dms  平均 %dms  最大 %dms  (%d/3 成功)\n→ %s",
                            min, sum / ok, max, ok, serverUrl);
                }
                uiHandler.post(() -> {
                    if (mBtnPing != null) {
                        mTvPingResult.setText(result);
                        mBtnPing.setEnabled(true);
                    }
                });
            }).start();
        }

        // ── Helpers ───────────────────────────────────────────────────────

        private static String fmt(int val) {
            return val == Integer.MAX_VALUE ? "N/A" : String.valueOf(val);
        }

        private static String nvl(String s) {
            return s != null ? s : "N/A";
        }

        private static int qualityColor(int value, int[] thresholds) {
            if (value == Integer.MAX_VALUE) return 0xFF888888;
            for (int i = 0; i < thresholds.length; i++) {
                if (value >= thresholds[i]) return Q_COLORS[i];
            }
            return Q_COLORS[Q_COLORS.length - 1];
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public Fragment getItem(int position) {
            return SimFragment.newInstance(position);
        }

        @Override
        public int getCount() {
            if (MainActivity.signalManager == null) return 1;
            int c = MainActivity.signalManager.getSimCount();
            return c > 0 ? c : 1;
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            if (object instanceof SimFragment) ((SimFragment) object).updateView();
            return super.getItemPosition(object);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (MainActivity.signalManager == null) return "SIM " + (position + 1);
            List<CellSignalManager.SimSignalData> list =
                    MainActivity.signalManager.getSimDataList();
            return position < list.size() ? list.get(position).simLabel : "SIM " + (position + 1);
        }
    }
}
