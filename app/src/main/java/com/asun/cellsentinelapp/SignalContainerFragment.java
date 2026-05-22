package com.asun.cellsentinelapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.List;
import java.util.Locale;

public class SignalContainerFragment extends Fragment {

    private SectionsPagerAdapter mAdapter;
    private ViewPager mViewPager;
    private TabLayout mTabLayout;

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

        private static String fmt(int val) {
            return CellSignalManager.fmt(val);
        }

        private String buildText() {
            if (MainActivity.signalManager == null) {
                return "\n  请授予位置和电话状态权限...";
            }
            List<CellSignalManager.SimSignalData> simList = MainActivity.signalManager.getSimDataList();
            int idx = simIndex();
            if (idx >= simList.size()) return "\n  暂无 SIM 数据";

            CellSignalManager.SimSignalData d = simList.get(idx);
            StringBuilder sb = new StringBuilder();

            String op = (d.operatorName == null || d.operatorName.isEmpty()) ? "N/A" : d.operatorName;
            sb.append("\n运营商: ").append(op).append("\n");

            boolean hasLte = d.lte_CI != Integer.MAX_VALUE || d.lte_PCI != Integer.MAX_VALUE
                    || d.lte_RSRP != Integer.MAX_VALUE;
            if (hasLte) {
                sb.append("\n─── LTE 服务小区 ───\n");
                sb.append(String.format(Locale.US,
                        "  MCC    : %s\n  MNC    : %s\n  CI     : %s\n  PCI    : %s\n" +
                        "  TAC    : %s\n  EARFCN : %s\n  RSRP   : %s dBm\n" +
                        "  RSRQ   : %s dB\n  SINR   : %s dB\n",
                        fmt(d.lte_MCC), fmt(d.lte_MNC), fmt(d.lte_CI), fmt(d.lte_PCI),
                        fmt(d.lte_TAC), fmt(d.lte_EARFCN),
                        fmt(d.lte_RSRP), fmt(d.lte_RSRQ), fmt(d.lte_SINR)));
            }

            boolean hasNr = d.nr_PCI != Integer.MAX_VALUE || d.nr_NRARFCN != Integer.MAX_VALUE
                    || d.nr_SSRSRP != Integer.MAX_VALUE;
            if (hasNr) {
                sb.append("\n─── NR (5G) 服务小区 ───\n");
                sb.append(String.format(Locale.US,
                        "  PCI      : %s\n  NRARFCN  : %s\n  SS-RSRP  : %s dBm\n" +
                        "  SS-RSRQ  : %s dB\n  SS-SINR  : %s dB\n",
                        fmt(d.nr_PCI), fmt(d.nr_NRARFCN),
                        fmt(d.nr_SSRSRP), fmt(d.nr_SSRSRQ), fmt(d.nr_SSSINR)));
            }

            boolean hasWcdma = d.wcdma_CID != Integer.MAX_VALUE;
            if (hasWcdma) {
                sb.append("\n─── WCDMA 服务小区 ───\n");
                sb.append(String.format(Locale.US,
                        "  MCC:%s  MNC:%s  LAC:%s  CID:%s  PSC:%s  RSSI:%s dBm\n",
                        fmt(d.wcdma_MCC), fmt(d.wcdma_MNC), fmt(d.wcdma_LAC),
                        fmt(d.wcdma_CID), fmt(d.wcdma_PSC), fmt(d.wcdma_RSSI)));
            }

            boolean hasGsm = d.gsm_CID != Integer.MAX_VALUE;
            if (hasGsm) {
                sb.append("\n─── GSM 服务小区 ───\n");
                sb.append(String.format(Locale.US,
                        "  MCC:%s  MNC:%s  LAC:%s  CID:%s  RSSI:%s dBm\n",
                        fmt(d.gsm_MCC), fmt(d.gsm_MNC), fmt(d.gsm_LAC),
                        fmt(d.gsm_CID), fmt(d.gsm_RSSI)));
            }

            if (!hasLte && !hasNr && !hasWcdma && !hasGsm) {
                sb.append("\n  暂无服务小区数据\n");
            }

            if (!d.neighborCells.isEmpty()) {
                sb.append("\n─── 邻区 (").append(d.neighborCells.size()).append(") ───\n");
                for (String cell : d.neighborCells) {
                    sb.append("  ").append(cell).append("\n");
                }
            }
            return sb.toString();
        }

        public void updateView() {
            View root = getView();
            if (root == null) return;
            TextView tv = root.findViewById(R.id.tv_signal);
            if (tv != null) tv.setText(buildText());
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            View root = inflater.inflate(R.layout.fragment_sim, container, false);
            ((TextView) root.findViewById(R.id.tv_signal)).setText(buildText());
            return root;
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
            List<CellSignalManager.SimSignalData> list = MainActivity.signalManager.getSimDataList();
            return position < list.size() ? list.get(position).simLabel : "SIM " + (position + 1);
        }
    }
}
