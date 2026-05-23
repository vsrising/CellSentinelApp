package com.asun.cellsentinelapp.network;

import com.asun.cellsentinelapp.util.SettingUtils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile OkHttpClient sClient;

    public interface Callback {
        void onSuccess(String body);
        void onError(String message);
    }

    private static OkHttpClient client() {
        if (sClient == null) {
            synchronized (ApiClient.class) {
                if (sClient == null) {
                    sClient = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return sClient;
    }

    private static Request.Builder baseBuilder(Context ctx, String path) {
        String url = SettingUtils.getServerUrl(ctx) + path;
        Request.Builder builder = new Request.Builder().url(url);
        String token = SettingUtils.getToken(ctx);
        if (!token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        return builder;
    }

    public static void get(Context ctx, String path, Callback cb) {
        Request req = baseBuilder(ctx, path).get().build();
        dispatch(req, cb);
    }

    public static void post(Context ctx, String path, String json, Callback cb) {
        RequestBody body = RequestBody.create(json, JSON);
        Request req = baseBuilder(ctx, path).post(body).build();
        dispatch(req, cb);
    }

    private static void dispatch(Request req, Callback cb) {
        client().newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                MAIN.post(() -> cb.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    MAIN.post(() -> cb.onSuccess(body));
                } else {
                    MAIN.post(() -> cb.onError("HTTP " + response.code() + ": " + body));
                }
            }
        });
    }
}
