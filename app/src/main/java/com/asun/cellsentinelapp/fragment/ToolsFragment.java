package com.asun.cellsentinelapp.fragment;

import com.asun.cellsentinelapp.R;
import com.asun.cellsentinelapp.util.EarfcnDecoder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

public class ToolsFragment extends Fragment {

    private TextInputEditText mEtCi;
    private TextView          mTvCiResult;

    private TextInputEditText mEtEarfcn;
    private TextView          mTvEarfcnResult;

    private TextInputEditText mEtTa;
    private RadioGroup        mRgTaType;
    private TextView          mTvTaResult;

    private TextInputEditText mEtLat1, mEtLon1, mEtLat2, mEtLon2;
    private TextView          mTvBearingResult;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_tools, container, false);

        mEtCi          = root.findViewById(R.id.et_ci_input);
        mTvCiResult    = root.findViewById(R.id.tv_ci_result);
        mEtEarfcn      = root.findViewById(R.id.et_earfcn_input);
        mTvEarfcnResult= root.findViewById(R.id.tv_earfcn_result);
        mEtTa          = root.findViewById(R.id.et_ta_value);
        mRgTaType      = root.findViewById(R.id.rg_ta_type);
        mTvTaResult    = root.findViewById(R.id.tv_ta_result);
        mEtLat1        = root.findViewById(R.id.et_lat1);
        mEtLon1        = root.findViewById(R.id.et_lon1);
        mEtLat2        = root.findViewById(R.id.et_lat2);
        mEtLon2        = root.findViewById(R.id.et_lon2);
        mTvBearingResult = root.findViewById(R.id.tv_bearing_result);

        root.findViewById(R.id.btn_open_agent).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AgentFragment())
                        .addToBackStack(null)
                        .commit());

        root.findViewById(R.id.btn_open_signaling_log).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new SignalingLogFragment())
                        .addToBackStack(null)
                        .commit());
        root.findViewById(R.id.btn_ci_parse).setOnClickListener(v -> parseCi());
        root.findViewById(R.id.btn_earfcn_calc).setOnClickListener(v -> calcEarfcn());
        root.findViewById(R.id.btn_ta_calc).setOnClickListener(v -> calcTa());
        root.findViewById(R.id.btn_bearing_calc).setOnClickListener(v -> calcBearing());

        return root;
    }

    // ── CI 解析器 ────────────────────────────────────────────────────────────

    private void parseCi() {
        String s = text(mEtCi);
        if (s.isEmpty()) { mTvCiResult.setText("请输入 CI"); return; }
        long ci;
        try { ci = Long.parseLong(s); } catch (NumberFormatException e) {
            mTvCiResult.setText("无效数字"); return;
        }
        long eNbId     = (ci >> 8) & 0xFFFFF;   // 20-bit eNB ID
        int  cellIndex = (int)(ci & 0xFF);        // 8-bit cell index
        mTvCiResult.setText(String.format(Locale.US,
                "CI         : %d (0x%X)\n" +
                "eNB ID     : %d\n" +
                "Cell Index : %d",
                ci, ci, eNbId, cellIndex));
    }

    // ── EARFCN 计算器 ────────────────────────────────────────────────────────

    private void calcEarfcn() {
        String s = text(mEtEarfcn);
        if (s.isEmpty()) { mTvEarfcnResult.setText("请输入 EARFCN"); return; }
        int arfcn;
        try { arfcn = Integer.parseInt(s); } catch (NumberFormatException e) {
            mTvEarfcnResult.setText("无效数字"); return;
        }

        // Try LTE first
        String lteDecode = EarfcnDecoder.decodeLte(arfcn);
        if (lteDecode != null) {
            double freq = EarfcnDecoder.lteFrequencyMhz(arfcn);
            String freqStr = freq > 0
                    ? String.format(Locale.US, "%.1f MHz", freq)
                    : "未知";
            mTvEarfcnResult.setText(String.format(Locale.US,
                    "类型       : LTE EARFCN\n" +
                    "频段       : %s\n" +
                    "DL 频率    : %s",
                    lteDecode, freqStr));
            return;
        }

        // Try NR
        String nrDecode = EarfcnDecoder.decodeNr(arfcn);
        if (nrDecode != null) {
            double freq = EarfcnDecoder.nrFrequencyMhz(arfcn);
            String freqStr = String.format(Locale.US, "%.3f MHz", freq);
            mTvEarfcnResult.setText(String.format(Locale.US,
                    "类型       : NR NRARFCN\n" +
                    "频段       : %s\n" +
                    "频率       : %s",
                    nrDecode, freqStr));
            return;
        }

        // Try NR by formula even if band not in table
        if (arfcn >= 0) {
            double freq = EarfcnDecoder.nrFrequencyMhz(arfcn);
            mTvEarfcnResult.setText(String.format(Locale.US,
                    "类型       : NR NRARFCN (频段未知)\n" +
                    "频率       : %.3f MHz",
                    freq));
        } else {
            mTvEarfcnResult.setText("未找到对应频段");
        }
    }

    // ── TA 距离换算 ──────────────────────────────────────────────────────────

    // TA step size per numerology (LTE/NR μ0 = 78.125m, μ1=39.0625m, μ2=19.53m, μ3=9.77m)
    private static final double[] TA_STEP_M = { 78.125, 39.0625, 19.53125, 9.765625 };

    private void calcTa() {
        String s = text(mEtTa);
        if (s.isEmpty()) { mTvTaResult.setText("请输入 TA"); return; }
        int ta;
        try { ta = Integer.parseInt(s); } catch (NumberFormatException e) {
            mTvTaResult.setText("无效数字"); return;
        }
        if (ta < 0) { mTvTaResult.setText("TA 不能为负数"); return; }

        int checked = mRgTaType.getCheckedRadioButtonId();
        int mu;
        if      (checked == R.id.rb_ta_lte)  mu = 0;
        else if (checked == R.id.rb_ta_nr1)  mu = 1;
        else if (checked == R.id.rb_ta_nr2)  mu = 2;
        else                                  mu = 3;

        double stepM   = TA_STEP_M[mu];
        double distM   = ta * stepM;
        double distKm  = distM / 1000.0;
        String label   = mu == 0 ? "LTE / NR μ0" : "NR μ" + mu;

        mTvTaResult.setText(String.format(Locale.US,
                "制式       : %s\n" +
                "TA 值      : %d\n" +
                "步长       : %.4f m\n" +
                "距离       : %.1f m  (%.3f km)",
                label, ta, stepM, distM, distKm));
    }

    // ── 方位角计算器 ─────────────────────────────────────────────────────────

    private void calcBearing() {
        String s1 = text(mEtLat1), s2 = text(mEtLon1),
               s3 = text(mEtLat2), s4 = text(mEtLon2);
        if (s1.isEmpty() || s2.isEmpty() || s3.isEmpty() || s4.isEmpty()) {
            mTvBearingResult.setText("请填写所有坐标"); return;
        }
        double lat1, lon1, lat2, lon2;
        try {
            lat1 = Double.parseDouble(s1); lon1 = Double.parseDouble(s2);
            lat2 = Double.parseDouble(s3); lon2 = Double.parseDouble(s4);
        } catch (NumberFormatException e) {
            mTvBearingResult.setText("坐标格式错误"); return;
        }
        if (Math.abs(lat1) > 90 || Math.abs(lat2) > 90 ||
            Math.abs(lon1) > 180 || Math.abs(lon2) > 180) {
            mTvBearingResult.setText("坐标超出范围"); return;
        }

        double φ1 = Math.toRadians(lat1), φ2 = Math.toRadians(lat2);
        double Δλ = Math.toRadians(lon2 - lon1);

        double y = Math.sin(Δλ) * Math.cos(φ2);
        double x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        bearing = (bearing + 360) % 360;

        // Haversine distance
        double a = Math.sin((φ2 - φ1) / 2) * Math.sin((φ2 - φ1) / 2)
                + Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distM = 6371000 * c;

        String compassDir = compassDir(bearing);

        mTvBearingResult.setText(String.format(Locale.US,
                "方位角     : %.2f°  (%s)\n" +
                "反方位角   : %.2f°\n" +
                "距离       : %.1f m  (%.3f km)",
                bearing, compassDir, (bearing + 180) % 360, distM, distM / 1000.0));
    }

    private static String compassDir(double deg) {
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                         "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return dirs[(int)((deg + 11.25) / 22.5) % 16];
    }

    private static String text(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }
}
