package com.asun.cellsentinelapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SpeedTestFragment extends Fragment {

    // Public Cloudflare speed test download URL (~10MB)
    private static final String PUBLIC_DOWNLOAD_URL =
            "https://speed.cloudflare.com/__down?bytes=10000000";
    // Cloudflare upload
    private static final String PUBLIC_UPLOAD_URL =
            "https://speed.cloudflare.com/__up";
    private static final int UPLOAD_SIZE = 2_000_000; // 2MB

    private TextView  mTvSpeedValue;
    private TextView  mTvSpeedUnit;
    private TextView  mTvDownload;
    private TextView  mTvUpload;
    private TextView  mTvLatency;
    private TextView  mTvStatus;
    private TextView  mTvHistory;
    private Button    mBtnStart;
    private RadioGroup mRgTarget;

    private final Handler   mHandler  = new Handler(Looper.getMainLooper());
    private final List<String> mHistory = new ArrayList<>();
    private boolean mTesting = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_speed_test, container, false);

        mTvSpeedValue = root.findViewById(R.id.tv_speed_value);
        mTvSpeedUnit  = root.findViewById(R.id.tv_speed_unit);
        mTvDownload   = root.findViewById(R.id.tv_download_speed);
        mTvUpload     = root.findViewById(R.id.tv_upload_speed);
        mTvLatency    = root.findViewById(R.id.tv_latency);
        mTvStatus     = root.findViewById(R.id.tv_speed_status);
        mTvHistory    = root.findViewById(R.id.tv_speed_history);
        mBtnStart     = root.findViewById(R.id.btn_start_speed_test);
        mRgTarget     = root.findViewById(R.id.rg_speed_target);

        mBtnStart.setOnClickListener(v -> {
            if (!mTesting) startTest();
        });

        updateHistory();
        return root;
    }

    private void startTest() {
        mTesting = true;
        mBtnStart.setEnabled(false);
        mTvSpeedValue.setText("…");
        mTvDownload.setText("— Mbps");
        mTvUpload.setText("— Mbps");
        mTvLatency.setText("— ms");

        boolean usePublic = mRgTarget.getCheckedRadioButtonId() == R.id.rb_speed_public;
        String baseUrl = usePublic ? PUBLIC_DOWNLOAD_URL
                : SettingUtils.getServerUrl(requireContext()) + "/api/speedtest/download?bytes=10000000";
        String uploadUrl = usePublic ? PUBLIC_UPLOAD_URL
                : SettingUtils.getServerUrl(requireContext()) + "/api/speedtest/upload";

        new Thread(() -> {
            // 1. Ping / Latency
            long latency = measureLatency(usePublic
                    ? "https://speed.cloudflare.com"
                    : SettingUtils.getServerUrl(requireContext()), 3);
            postStatus("延迟测试完成，测下载速度...");
            postLatency(latency);

            // 2. Download
            double dlMbps = measureDownload(baseUrl, !usePublic
                    ? SettingUtils.getBackupServerUrl(requireContext()) : null);
            postStatus("下载完成，测上传速度...");
            postDownload(dlMbps);
            postBig(dlMbps);

            // 3. Upload
            double ulMbps = measureUpload(uploadUrl);
            postStatus("测速完成");
            postUpload(ulMbps);

            // Save to history
            String entry = String.format(Locale.US,
                    "[%s] %s  下行 %.1fMbps  上行 %.1fMbps  延迟 %dms",
                    new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()),
                    usePublic ? "公网" : "自建",
                    dlMbps, ulMbps, latency);
            mHistory.add(0, entry);
            if (mHistory.size() > 10) mHistory.remove(mHistory.size() - 1);
            mHandler.post(() -> {
                updateHistory();
                mTesting = false;
                mBtnStart.setEnabled(true);
            });
        }).start();
    }

    private long measureLatency(String url, int count) {
        long sum = 0; int ok = 0;
        for (int i = 0; i < count; i++) {
            long t = System.currentTimeMillis();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("HEAD");
                conn.connect();
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code > 0) { sum += System.currentTimeMillis() - t; ok++; }
            } catch (Exception ignored) {}
        }
        return ok > 0 ? sum / ok : -1;
    }

    private double measureDownload(String primaryUrl, @Nullable String backupBase) {
        double result = doDownload(primaryUrl);
        if (result < 0 && backupBase != null && !backupBase.isEmpty()) {
            result = doDownload(backupBase + "/api/speedtest/download?bytes=10000000");
        }
        return Math.max(result, 0);
    }

    private double doDownload(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            conn.connect();
            if (conn.getResponseCode() != 200) { conn.disconnect(); return -1; }
            InputStream in = conn.getInputStream();
            byte[] buf = new byte[65536];
            long start = System.currentTimeMillis();
            long bytes = 0;
            int read;
            while ((read = in.read(buf)) != -1) {
                bytes += read;
                // Live update every 500ms
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed > 0) {
                    final double live = bytes * 8.0 / elapsed / 1000.0;
                    postBig(live);
                }
            }
            in.close(); conn.disconnect();
            long elapsed = System.currentTimeMillis() - start;
            return elapsed > 0 ? bytes * 8.0 / elapsed / 1000.0 : -1; // Mbps
        } catch (Exception e) { return -1; }
    }

    private double measureUpload(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(UPLOAD_SIZE);
            conn.connect();
            OutputStream out = conn.getOutputStream();
            byte[] chunk = new byte[65536];
            long start = System.currentTimeMillis();
            long written = 0;
            while (written < UPLOAD_SIZE) {
                int toWrite = (int) Math.min(chunk.length, UPLOAD_SIZE - written);
                out.write(chunk, 0, toWrite);
                written += toWrite;
            }
            out.flush(); out.close();
            conn.getResponseCode();
            conn.disconnect();
            long elapsed = System.currentTimeMillis() - start;
            return elapsed > 0 ? written * 8.0 / elapsed / 1000.0 : -1;
        } catch (Exception e) { return -1; }
    }

    private void postStatus(String s) { mHandler.post(() -> { if (mTvStatus != null) mTvStatus.setText(s); }); }
    private void postBig(double mbps) {
        mHandler.post(() -> { if (mTvSpeedValue != null)
            mTvSpeedValue.setText(String.format(Locale.US, "%.1f", mbps)); });
    }
    private void postDownload(double mbps) {
        mHandler.post(() -> { if (mTvDownload != null)
            mTvDownload.setText(String.format(Locale.US, "%.2f Mbps", mbps)); });
    }
    private void postUpload(double mbps) {
        mHandler.post(() -> { if (mTvUpload != null)
            mTvUpload.setText(mbps > 0
                    ? String.format(Locale.US, "%.2f Mbps", mbps) : "上传失败"); });
    }
    private void postLatency(long ms) {
        mHandler.post(() -> { if (mTvLatency != null)
            mTvLatency.setText(ms >= 0 ? ms + " ms" : "—"); });
    }

    private void updateHistory() {
        if (mTvHistory == null) return;
        if (mHistory.isEmpty()) { mTvHistory.setText("暂无记录"); return; }
        StringBuilder sb = new StringBuilder();
        for (String s : mHistory) sb.append(s).append("\n");
        mTvHistory.setText(sb.toString().trim());
    }
}
