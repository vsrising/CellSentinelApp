package com.asun.cellsentinelapp.network;

import com.asun.cellsentinelapp.util.SettingUtils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HermesClient {

    public interface ChatCallback {
        void onSuccess(String response);
        void onError(String message);
    }

    public interface ModelsCallback {
        void onSuccess(List<String> models);
        void onError(String message);
    }

    private static final int CONNECT_TIMEOUT = 8_000;
    private static final int READ_TIMEOUT    = 120_000;
    private static final Handler sMain = new Handler(Looper.getMainLooper());

    public static void chat(Context ctx,
                            List<Map<String, String>> messages,
                            ChatCallback cb) {
        new Thread(() -> {
            String baseUrl = SettingUtils.getHermesUrl(ctx);
            String token   = SettingUtils.getHermesToken(ctx);
            String model   = SettingUtils.getHermesModel(ctx);
            try {
                JSONArray msgArr = new JSONArray();
                for (Map<String, String> m : messages) {
                    JSONObject jo = new JSONObject();
                    jo.put("role",    m.containsKey("role")    ? m.get("role")    : "user");
                    jo.put("content", m.containsKey("content") ? m.get("content") : "");
                    msgArr.put(jo);
                }
                JSONObject body = new JSONObject();
                if (model != null && !model.isEmpty()) body.put("model", model);
                body.put("messages", msgArr);
                body.put("stream", false);

                HttpURLConnection conn = open(baseUrl + "/v1/chat/completions", "POST", token);
                conn.setDoOutput(true);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                String raw = read(code < 400 ? conn.getInputStream() : conn.getErrorStream());
                conn.disconnect();

                if (code >= 400) {
                    sMain.post(() -> cb.onError("HTTP " + code + ": " + raw));
                    return;
                }
                JSONObject resp = new JSONObject(raw);
                String content = resp.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                sMain.post(() -> cb.onSuccess(content));
            } catch (Exception e) {
                String msg = e.getMessage();
                sMain.post(() -> cb.onError(msg != null ? msg : "连接失败"));
            }
        }).start();
    }

    public static void fetchModels(Context ctx, ModelsCallback cb) {
        new Thread(() -> {
            String baseUrl = SettingUtils.getHermesUrl(ctx);
            String token   = SettingUtils.getHermesToken(ctx);
            try {
                HttpURLConnection conn = open(baseUrl + "/v1/models", "GET", token);
                conn.setReadTimeout(10_000);
                int code = conn.getResponseCode();
                String raw = read(code < 400 ? conn.getInputStream() : conn.getErrorStream());
                conn.disconnect();

                if (code >= 400) {
                    sMain.post(() -> cb.onError("HTTP " + code));
                    return;
                }
                List<String> models = new ArrayList<>();
                JSONObject resp = new JSONObject(raw);
                JSONArray data = resp.getJSONArray("data");
                for (int i = 0; i < data.length(); i++)
                    models.add(data.getJSONObject(i).getString("id"));
                sMain.post(() -> cb.onSuccess(models));
            } catch (Exception e) {
                String msg = e.getMessage();
                sMain.post(() -> cb.onError(msg != null ? msg : "连接失败"));
            }
        }).start();
    }

    private static HttpURLConnection open(String urlStr, String method, String token)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (token != null && !token.isEmpty())
            conn.setRequestProperty("Authorization", "Bearer " + token);
        return conn;
    }

    private static String read(java.io.InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }
}
