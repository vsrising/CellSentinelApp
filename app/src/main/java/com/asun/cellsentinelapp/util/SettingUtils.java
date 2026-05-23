package com.asun.cellsentinelapp.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingUtils {

    private static final String PREF_NAME = "cell_sentinel_prefs";
    private static final String KEY_SERVER_URL         = "server_url";
    private static final String KEY_TOKEN              = "auth_token";
    private static final String KEY_USERNAME           = "username";
    private static final String KEY_NICK_NAME          = "nick_name";
    private static final String KEY_NEED_CACHE_REFRESH = "need_cache_refresh";
    private static final String KEY_SAVED_USERNAME     = "saved_username";
    private static final String KEY_SAVED_PASSWORD     = "saved_password";

    public static final String DEFAULT_SERVER_URL        = "http://192.168.8.152:8080";
    private static final String KEY_BACKUP_SERVER_URL   = "backup_server_url";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getServerUrl(Context ctx) {
        return prefs(ctx).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public static void setServerUrl(Context ctx, String url) {
        prefs(ctx).edit().putString(KEY_SERVER_URL, url.trim()).apply();
    }

    public static String getBackupServerUrl(Context ctx) {
        return prefs(ctx).getString(KEY_BACKUP_SERVER_URL, "");
    }

    public static void setBackupServerUrl(Context ctx, String url) {
        prefs(ctx).edit().putString(KEY_BACKUP_SERVER_URL, url.trim()).apply();
    }

    /**
     * Returns the effective server URL to use. If primary is reachable (non-empty) it is
     * returned; otherwise the backup URL is returned. Callers that want automatic failover
     * should use {@link #getEffectiveServerUrl(Context, Runnable)} instead.
     */
    public static String getPrimaryOrBackupUrl(Context ctx) {
        String primary = getServerUrl(ctx);
        return primary.isEmpty() ? getBackupServerUrl(ctx) : primary;
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

    public static boolean getNeedCacheRefresh(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NEED_CACHE_REFRESH, false);
    }

    public static void setNeedCacheRefresh(Context ctx, boolean need) {
        prefs(ctx).edit().putBoolean(KEY_NEED_CACHE_REFRESH, need).apply();
    }

    public static String getSavedUsername(Context ctx) {
        return prefs(ctx).getString(KEY_SAVED_USERNAME, "");
    }

    public static String getSavedPassword(Context ctx) {
        return prefs(ctx).getString(KEY_SAVED_PASSWORD, "");
    }

    public static boolean hasSavedCredentials(Context ctx) {
        return !prefs(ctx).getString(KEY_SAVED_USERNAME, "").isEmpty();
    }

    public static void saveLoginCredentials(Context ctx, String username, String password) {
        prefs(ctx).edit()
                .putString(KEY_SAVED_USERNAME, username)
                .putString(KEY_SAVED_PASSWORD, password)
                .apply();
    }

    public static void clearSavedCredentials(Context ctx) {
        prefs(ctx).edit()
                .remove(KEY_SAVED_USERNAME)
                .remove(KEY_SAVED_PASSWORD)
                .apply();
    }

    // ── Hermes AI agent settings ──────────────────────────────────────────────
    private static final String KEY_HERMES_URL   = "hermes_url";
    private static final String KEY_HERMES_TOKEN = "hermes_token";
    private static final String KEY_HERMES_MODEL = "hermes_model";

    public static final String DEFAULT_HERMES_URL   = "http://100.99.178.106:8642";
    public static final String DEFAULT_HERMES_TOKEN = "admin123";

    public static String getHermesUrl(Context ctx) {
        return prefs(ctx).getString(KEY_HERMES_URL, DEFAULT_HERMES_URL);
    }
    public static void setHermesUrl(Context ctx, String url) {
        prefs(ctx).edit().putString(KEY_HERMES_URL, url.trim()).apply();
    }
    public static String getHermesToken(Context ctx) {
        return prefs(ctx).getString(KEY_HERMES_TOKEN, DEFAULT_HERMES_TOKEN);
    }
    public static void setHermesToken(Context ctx, String token) {
        prefs(ctx).edit().putString(KEY_HERMES_TOKEN, token.trim()).apply();
    }
    public static String getHermesModel(Context ctx) {
        return prefs(ctx).getString(KEY_HERMES_MODEL, "");
    }
    public static void setHermesModel(Context ctx, String model) {
        prefs(ctx).edit().putString(KEY_HERMES_MODEL, model.trim()).apply();
    }
}
