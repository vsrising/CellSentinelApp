package com.asun.cellsentinelapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

public class DeviceInfoFragment extends Fragment {

    private TextView mTvBattery;
    private TextView mTvHardware;
    private TextView mTvSystem;
    private BroadcastReceiver mBatteryReceiver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_device_info, container, false);
        mTvBattery  = root.findViewById(R.id.tv_battery_info);
        mTvHardware = root.findViewById(R.id.tv_hardware_info);
        mTvSystem   = root.findViewById(R.id.tv_system_info);

        mBatteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                updateBattery(intent);
            }
        };

        fillHardware();
        fillSystem();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent battIntent = requireContext().registerReceiver(
                mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battIntent != null) updateBattery(battIntent);
    }

    @Override
    public void onPause() {
        super.onPause();
        try { requireContext().unregisterReceiver(mBatteryReceiver); } catch (Exception ignored) {}
    }

    private void updateBattery(Intent intent) {
        if (mTvBattery == null) return;
        int level   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale   = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status  = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int health  = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int temp    = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        String tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

        int pct = scale > 0 ? (level * 100 / scale) : -1;

        mTvBattery.setText(String.format(Locale.US,
                "电量       : %d%%\n" +
                "状态       : %s\n" +
                "充电方式   : %s\n" +
                "健康状态   : %s\n" +
                "温度       : %.1f °C\n" +
                "电压       : %d mV\n" +
                "技术       : %s",
                pct,
                battStatusStr(status),
                pluggedStr(plugged),
                battHealthStr(health),
                temp / 10.0f,
                voltage,
                tech != null ? tech : "—"));
    }

    private void fillHardware() {
        String cpuAbi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI;
        // Try to read CPU info
        String cpuName = readCpuName();

        mTvHardware.setText(String.format(Locale.US,
                "品牌       : %s\n" +
                "制造商     : %s\n" +
                "型号       : %s\n" +
                "设备代号   : %s\n" +
                "主板       : %s\n" +
                "CPU 架构   : %s\n" +
                "CPU 型号   : %s",
                Build.BRAND, Build.MANUFACTURER, Build.MODEL,
                Build.DEVICE, Build.BOARD, cpuAbi, cpuName));
    }

    private void fillSystem() {
        mTvSystem.setText(String.format(Locale.US,
                "Android 版本: %s (API %d)\n" +
                "安全补丁   : %s\n" +
                "版本号     : %s\n" +
                "内核版本   : %s\n" +
                "Build ID   : %s",
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
                Build.VERSION.SECURITY_PATCH,
                Build.DISPLAY,
                System.getProperty("os.version", "—"),
                Build.ID));
    }

    private static String readCpuName() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.toLowerCase(Locale.US).startsWith("hardware")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) return parts[1].trim();
                }
            }
        } catch (IOException ignored) {}
        return Build.HARDWARE;
    }

    private static String battStatusStr(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:    return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
            case BatteryManager.BATTERY_STATUS_FULL:        return "已充满";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:return "未充电";
            default: return "未知";
        }
    }

    private static String pluggedStr(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:      return "交流充电";
            case BatteryManager.BATTERY_PLUGGED_USB:     return "USB 充电";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:return "无线充电";
            default: return "未插电";
        }
    }

    private static String battHealthStr(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:          return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:      return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD:          return "已损坏";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:  return "过压";
            case BatteryManager.BATTERY_HEALTH_COLD:          return "温度过低";
            default: return "未知";
        }
    }
}
