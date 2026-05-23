package com.asun.cellsentinelapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WifiInfoFragment extends Fragment {

    private WifiManager mWifiMgr;
    private TextView    mTvConnected;
    private TextView    mTvScanResults;
    private BroadcastReceiver mScanReceiver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_wifi_info, container, false);

        mTvConnected   = root.findViewById(R.id.tv_wifi_connected);
        mTvScanResults = root.findViewById(R.id.tv_wifi_scan_results);

        mWifiMgr = (WifiManager) requireContext()
                .getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        root.findViewById(R.id.btn_wifi_refresh)
                .setOnClickListener(v -> refreshConnected());
        root.findViewById(R.id.btn_wifi_scan)
                .setOnClickListener(v -> startScan());

        mScanReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                showScanResults();
            }
        };

        refreshConnected();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireContext().registerReceiver(mScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        refreshConnected();
    }

    @Override
    public void onPause() {
        super.onPause();
        try { requireContext().unregisterReceiver(mScanReceiver); } catch (Exception ignored) {}
    }

    private void refreshConnected() {
        if (mWifiMgr == null) { mTvConnected.setText("WiFi 服务不可用"); return; }
        if (!mWifiMgr.isWifiEnabled()) { mTvConnected.setText("WiFi 已关闭"); return; }

        WifiInfo info = mWifiMgr.getConnectionInfo();
        if (info == null || info.getNetworkId() == -1) {
            mTvConnected.setText("未连接 WiFi");
            return;
        }

        String ssid = info.getSSID().replaceAll("\"", "");
        int freq     = info.getFrequency(); // MHz
        int rssi     = info.getRssi();
        int linkSpeed= info.getLinkSpeed(); // Mbps
        int channel  = freqToChannel(freq);
        String band  = freq < 3000 ? "2.4GHz" : (freq < 6000 ? "5GHz" : "6GHz");
        String level = rssiToLevel(rssi);

        mTvConnected.setText(String.format(Locale.US,
                "SSID       : %s\n" +
                "BSSID      : %s\n" +
                "RSSI       : %d dBm  (%s)\n" +
                "频率       : %d MHz  (%s Ch.%d)\n" +
                "链路速率   : %d Mbps\n" +
                "IP 地址    : %s",
                ssid, info.getBSSID(), rssi, level,
                freq, band, channel,
                linkSpeed,
                intToIp(info.getIpAddress())));
    }

    private void startScan() {
        if (mWifiMgr == null) return;
        if (!mWifiMgr.isWifiEnabled()) {
            Toast.makeText(getContext(), "请先开启 WiFi", Toast.LENGTH_SHORT).show();
            return;
        }
        mTvScanResults.setText("扫描中...");
        mWifiMgr.startScan();
    }

    private void showScanResults() {
        if (mWifiMgr == null || mTvScanResults == null) return;
        List<ScanResult> results = mWifiMgr.getScanResults();
        if (results == null || results.isEmpty()) {
            mTvScanResults.setText("未发现 WiFi");
            return;
        }
        // Sort by RSSI descending
        List<ScanResult> sorted = new ArrayList<>(results);
        Collections.sort(sorted, (a, b) -> Integer.compare(b.level, a.level));

        StringBuilder sb = new StringBuilder();
        for (ScanResult r : sorted) {
            int ch    = freqToChannel(r.frequency);
            String band = r.frequency < 3000 ? "2.4G" : (r.frequency < 6000 ? " 5G" : " 6G");
            String sec = r.capabilities.contains("WPA") ? "WPA"
                       : r.capabilities.contains("WEP") ? "WEP" : "开放";
            String ssid = r.SSID.isEmpty() ? "<隐藏>" : r.SSID;
            sb.append(String.format(Locale.US, "%-22s %4ddBm  %3d  %s  %s\n",
                    truncate(ssid, 22), r.level, ch, band, sec));
        }
        mTvScanResults.setText(sb.toString().trim());
    }

    private static int freqToChannel(int freqMhz) {
        if (freqMhz == 2484) return 14;
        if (freqMhz < 2484)  return (freqMhz - 2407) / 5;
        if (freqMhz >= 5180) return (freqMhz - 5000) / 5;
        return 0;
    }

    private static String rssiToLevel(int rssi) {
        if (rssi >= -50) return "极强";
        if (rssi >= -60) return "强";
        if (rssi >= -70) return "中";
        if (rssi >= -80) return "弱";
        return "极弱";
    }

    private static String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
