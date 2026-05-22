package com.asun.cellsentinelapp;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingUtils {

    private static final String PREF_NAME = "cell_sentinel_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_TOKEN      = "auth_token";
    private static final String KEY_USERNAME   = "username";
    private static final String KEY_NICK_NAME  = "nick_name";

    public static final String DEFAULT_SERVER_URL = "http://192.168.202.19:8080";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getServerUrl(Context ctx) {
        return prefs(ctx).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public static void setServerUrl(Context ctx, String url) {
        prefs(ctx).edit().putString(KEY_SERVER_URL, url.trim()).apply();
    }

    public static String getToken(Context ctx) {
        return prefs(ctx).getString(KEY_TOKEN, "");
    }

    public static void setToken(Context ctx, String token) {
        prefs(ctx).edit().putString(KEY_TOKEN, token).apply();
    }

    public static void clearToken(Context ctx) {
        prefs(ctx).edit().remove(KEY_TOKEN).remove(KEY_USERNAME).remove(KEY_NICK_NAME).apply();
    }

    public static boolean isLoggedIn(Context ctx) {
        return !getToken(ctx).isEmpty();
    }

    public static String getUsername(Context ctx) {
        return prefs(ctx).getString(KEY_USERNAME, "");
    }

    public static void setUsername(Context ctx, String username) {
        prefs(ctx).edit().putString(KEY_USERNAME, username).apply();
    }

    public static String getNickName(Context ctx) {
        return prefs(ctx).getString(KEY_NICK_NAME, "");
    }

    public static void setNickName(Context ctx, String nickName) {
        prefs(ctx).edit().putString(KEY_NICK_NAME, nickName).apply();
    }
}
