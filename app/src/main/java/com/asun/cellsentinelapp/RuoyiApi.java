package com.asun.cellsentinelapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * RuoYi 3.8.6 API 接口封装。
 * 信号上报接口 (/iot/signal/report, /iot/drivetest/report) 为自定义扩展接口，
 * 需在 EnLearning 后端添加对应 Controller。
 */
public class RuoyiApi {

    // ── Callback interfaces ──────────────────────────────────────────────────

    public interface LoginCallback {
        void onSuccess(String token);
        void onError(String msg);
    }

    public interface CaptchaCallback {
        void onSuccess(Bitmap image, String uuid);
        void onError(String msg);
    }

    public interface UserInfoCallback {
        void onSuccess(String userName, String nickName);
        void onError(String msg);
    }

    public interface UploadCallback {
        void onSuccess(String msg);
        void onError(String msg);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /** GET /captchaImage → captcha bitmap + uuid */
    public static void getCaptcha(Context ctx, CaptchaCallback cb) {
        ApiClient.get(ctx, "/captchaImage", new ApiClient.Callback() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONObject obj = new JSONObject(body);
                    String img  = obj.getString("img");
                    String uuid = obj.getString("uuid");
                    byte[] bytes = Base64.decode(img, Base64.DEFAULT);
                    Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    cb.onSuccess(bm, uuid);
                } catch (Exception e) {
                    cb.onError("验证码解析失败: " + e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    /** POST /login → token */
    public static void login(Context ctx, String username, String password,
                             String captchaCode, String uuid, LoginCallback cb) {
        String json = String.format(Locale.US,
                "{\"username\":\"%s\",\"password\":\"%s\",\"code\":\"%s\",\"uuid\":\"%s\"}",
                username, password, captchaCode, uuid);

        ApiClient.post(ctx, "/login", json, new ApiClient.Callback() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONObject obj = new JSONObject(body);
                    int code = obj.optInt("code", -1);
                    if (code == 200) {
                        String token = obj.getString("token");
                        SettingUtils.setToken(ctx, token);
                        SettingUtils.setUsername(ctx, username);
                        cb.onSuccess(token);
                    } else {
                        cb.onError(obj.optString("msg", "登录失败"));
                    }
                } catch (Exception e) {
                    cb.onError("响应解析失败: " + e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    /** GET /getInfo → current user info */
    public static void getInfo(Context ctx, UserInfoCallback cb) {
        ApiClient.get(ctx, "/getInfo", new ApiClient.Callback() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONObject obj  = new JSONObject(body);
                    JSONObject user = obj.getJSONObject("user");
                    String userName = user.optString("userName", "");
                    String nickName = user.optString("nickName", userName);
                    SettingUtils.setUsername(ctx, userName);
                    SettingUtils.setNickName(ctx, nickName);
                    cb.onSuccess(userName, nickName);
                } catch (Exception e) {
                    cb.onError("用户信息解析失败: " + e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    // ── Signal data upload ────────────────────────────────────────────────────

    /**
     * POST /iot/signal/report
     * 上报当前所有 SIM 的信号快照（需在 EnLearning 后端实现此接口）。
     */
    public static void uploadSignalSnapshot(Context ctx,
                                            List<CellSignalManager.SimSignalData> sims,
                                            UploadCallback cb) {
        try {
            JSONArray arr = new JSONArray();
            long ts = System.currentTimeMillis();
            for (CellSignalManager.SimSignalData d : sims) {
                JSONObject o = new JSONObject();
                o.put("timestamp",    ts);
                o.put("simLabel",     d.simLabel);
                o.put("operatorName", d.operatorName);
                if (d.lte_RSRP != Integer.MAX_VALUE) {
                    o.put("rat",    "LTE");
                    o.put("rsrp",   d.lte_RSRP);
                    o.put("rsrq",   d.lte_RSRQ != Integer.MAX_VALUE ? d.lte_RSRQ : JSONObject.NULL);
                    o.put("sinr",   d.lte_SINR != Integer.MAX_VALUE ? d.lte_SINR : JSONObject.NULL);
                    o.put("pci",    d.lte_PCI  != Integer.MAX_VALUE ? d.lte_PCI  : JSONObject.NULL);
                    o.put("ci",     d.lte_CI   != Integer.MAX_VALUE ? d.lte_CI   : JSONObject.NULL);
                    o.put("tac",    d.lte_TAC  != Integer.MAX_VALUE ? d.lte_TAC  : JSONObject.NULL);
                    o.put("earfcn", d.lte_EARFCN != Integer.MAX_VALUE ? d.lte_EARFCN : JSONObject.NULL);
                } else if (d.nr_SSRSRP != Integer.MAX_VALUE) {
                    o.put("rat",    "NR");
                    o.put("rsrp",   d.nr_SSRSRP);
                    o.put("rsrq",   d.nr_SSRSRQ != Integer.MAX_VALUE ? d.nr_SSRSRQ : JSONObject.NULL);
                    o.put("sinr",   d.nr_SSSINR != Integer.MAX_VALUE ? d.nr_SSSINR : JSONObject.NULL);
                    o.put("pci",    d.nr_PCI    != Integer.MAX_VALUE ? d.nr_PCI    : JSONObject.NULL);
                    o.put("earfcn", d.nr_NRARFCN != Integer.MAX_VALUE ? d.nr_NRARFCN : JSONObject.NULL);
                } else {
                    o.put("rat", "Unknown");
                }
                arr.put(o);
            }
            JSONObject body = new JSONObject();
            body.put("records", arr);
            ApiClient.post(ctx, "/iot/signal/report", body.toString(), new ApiClient.Callback() {
                @Override public void onSuccess(String b) { cb.onSuccess("上报成功"); }
                @Override public void onError(String m)   { cb.onError(m); }
            });
        } catch (Exception e) {
            cb.onError("数据构建失败: " + e.getMessage());
        }
    }

    /**
     * POST /iot/drivetest/report
     * 上报路测数据（需在 EnLearning 后端实现此接口）。
     */
    public static void uploadDriveTest(Context ctx, List<DriveTestRecord> records,
                                       String sessionName, UploadCallback cb) {
        if (records.isEmpty()) {
            cb.onError("无路测数据");
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("sessionName", sessionName);
            body.put("records", new org.json.JSONArray(DriveTestRecord.toJsonArray(records)));
            ApiClient.post(ctx, "/iot/drivetest/report", body.toString(), new ApiClient.Callback() {
                @Override public void onSuccess(String b) {
                    cb.onSuccess("上报成功，共 " + records.size() + " 条记录");
                }
                @Override public void onError(String m) { cb.onError(m); }
            });
        } catch (Exception e) {
            cb.onError("数据构建失败: " + e.getMessage());
        }
    }
}
