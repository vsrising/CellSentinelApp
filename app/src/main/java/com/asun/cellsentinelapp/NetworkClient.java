package com.asun.cellsentinelapp;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP helper that retries the configured backup server when the primary fails.
 * All callers that currently hard-code server URL should route through here.
 */
public class NetworkClient {

    public interface ResponseCallback {
        void onSuccess(int code, String body);
        void onError(String message);
    }

    private static final int CONNECT_TIMEOUT = 6_000;
    private static final int READ_TIMEOUT    = 15_000;

    /** POST JSON; tries primary then backup on connection errors. */
    public static void postJson(Context ctx, String path, String json, ResponseCallback cb) {
        new Thread(() -> {
            String primary = SettingUtils.getServerUrl(ctx);
            String backup  = SettingUtils.getBackupServerUrl(ctx);
            String err1 = null;

            try {
                String result = doPost(primary + path, json, SettingUtils.getToken(ctx));
                cb.onSuccess(200, result);
                return;
            } catch (IOException e) {
                err1 = e.getMessage();
            }

            if (!backup.isEmpty()) {
                try {
                    String result = doPost(backup + path, json, SettingUtils.getToken(ctx));
                    cb.onSuccess(200, result);
                    return;
                } catch (IOException e2) {
                    cb.onError("主: " + err1 + "  备: " + e2.getMessage());
                    return;
                }
            }
            cb.onError(err1 != null ? err1 : "连接失败");
        }).start();
    }

    /** GET; tries primary then backup. */
    public static void get(Context ctx, String path, ResponseCallback cb) {
        new Thread(() -> {
            String primary = SettingUtils.getServerUrl(ctx);
            String backup  = SettingUtils.getBackupServerUrl(ctx);
            String err1 = null;

            try {
                String result = doGet(primary + path, SettingUtils.getToken(ctx));
                cb.onSuccess(200, result);
                return;
            } catch (IOException e) {
                err1 = e.getMessage();
            }

            if (!backup.isEmpty()) {
                try {
                    String result = doGet(backup + path, SettingUtils.getToken(ctx));
                    cb.onSuccess(200, result);
                    return;
                } catch (IOException e2) {
                    cb.onError("主: " + err1 + "  备: " + e2.getMessage());
                    return;
                }
            }
            cb.onError(err1 != null ? err1 : "连接失败");
        }).start();
    }

    private static String doPost(String url, String json, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (token != null && !token.isEmpty())
            conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        String body = readStream(code < 400 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (code >= 400) throw new IOException("HTTP " + code + ": " + body);
        return body;
    }

    private static String doGet(String url, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        if (token != null && !token.isEmpty())
            conn.setRequestProperty("Authorization", "Bearer " + token);
        int code = conn.getResponseCode();
        String body = readStream(code < 400 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (code >= 400) throw new IOException("HTTP " + code + ": " + body);
        return body;
    }

    private static String readStream(InputStream in) throws IOException {
        if (in == null) return "";
        byte[] buf = new byte[4096];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = in.read(buf)) != -1) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        in.close();
        return sb.toString();
    }
}
