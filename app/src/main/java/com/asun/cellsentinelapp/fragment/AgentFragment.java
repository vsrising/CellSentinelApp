package com.asun.cellsentinelapp.fragment;

import com.asun.cellsentinelapp.R;
import com.asun.cellsentinelapp.activity.MainActivity;
import com.asun.cellsentinelapp.manager.CellSignalManager;
import com.asun.cellsentinelapp.network.HermesClient;
import com.asun.cellsentinelapp.util.MarkdownUtils;
import com.asun.cellsentinelapp.util.PdfReportBuilder;
import com.asun.cellsentinelapp.util.SettingUtils;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AgentFragment extends Fragment {

    // ── Persistent state across recreations ──────────────────────────────────
    private static final List<ChatMsg> sChatHistory = new ArrayList<>();
    private static String sPendingContext = null;
    private static String sPendingType   = null;

    public static void setPendingAnalysis(String context, String type) {
        sPendingContext = context;
        sPendingType   = type;
    }

    // ── Chat message model ────────────────────────────────────────────────────
    private static class ChatMsg {
        final boolean isUser;
        final String  text;
        ChatMsg(boolean isUser, String text) { this.isUser = isUser; this.text = text; }
    }

    // ── UI refs ───────────────────────────────────────────────────────────────
    private LinearLayout    mMsgContainer;
    private ScrollView      mScrollView;
    private EditText        mEtInput;
    private Button          mBtnSend;
    private Button          mBtnExportPdf;
    private MaterialCardView mCardContext;
    private TextView        mTvContextSummary;
    private TextView        mTvTyping;

    private final Handler   mHandler = new Handler(Looper.getMainLooper());

    // ── Analysis context ──────────────────────────────────────────────────────
    private String  mAttachedContext;
    private String  mAttachedType;
    private String  mLastAiResponse;
    private boolean mThinking = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_agent, container, false);

        mMsgContainer    = root.findViewById(R.id.ll_messages);
        mScrollView      = root.findViewById(R.id.sv_messages);
        mEtInput         = root.findViewById(R.id.et_agent_input);
        mBtnSend         = root.findViewById(R.id.btn_agent_send);
        mBtnExportPdf    = root.findViewById(R.id.btn_export_pdf);
        mCardContext     = root.findViewById(R.id.card_context);
        mTvContextSummary= root.findViewById(R.id.tv_context_summary);
        mTvTyping        = root.findViewById(R.id.tv_typing);

        root.findViewById(R.id.btn_agent_settings).setOnClickListener(v -> showSettingsDialog());
        root.findViewById(R.id.btn_agent_clear).setOnClickListener(v -> clearChat());

        mBtnSend.setOnClickListener(v -> onSendClick());
        mBtnExportPdf.setOnClickListener(v -> exportPdf());

        // Rebuild UI from static history
        rebuildMessages();

        // Handle pending analysis
        if (sPendingContext != null) {
            String ctx  = sPendingContext;
            String type = sPendingType;
            sPendingContext = null;
            sPendingType   = null;
            mHandler.postDelayed(() -> startAnalysis(ctx, type), 300);
        }

        // Fetch available models if none set
        if (SettingUtils.getHermesModel(requireContext()).isEmpty()) {
            fetchAndSetModel();
        }

        return root;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void startAnalysis(String contextData, String type) {
        mAttachedContext = contextData;
        mAttachedType    = type;

        // Show context summary card
        mCardContext.setVisibility(View.VISIBLE);
        String label = "drive_test".equals(type) ? "路测数据" : "信令事件";
        int lines = contextData.split("\n").length;
        mTvContextSummary.setText(label + "  ·  " + lines + " 行数据");

        // Build the analysis prompt and send
        String typeLabel = "drive_test".equals(type) ? "路测数据" : "信令事件日志";
        String userPrompt = "请对以下" + typeLabel + "进行专业网络优化分析：\n\n"
                + contextData
                + "\n\n请从以下几个方面分析：\n"
                + "1. 信号质量评估（RSRP/SINR/RSRQ水平）\n"
                + "2. 覆盖问题诊断\n"
                + ("drive_test".equals(type)
                    ? "3. 切换性能分析\n4. 网络质量综合评分（1-10分）\n5. 具体优化建议\n"
                    : "3. 切换事件分析\n4. 服务稳定性评估\n5. 网络问题诊断与优化建议\n");

        sendToApi(userPrompt, true);
    }

    // ── Message sending ───────────────────────────────────────────────────────

    private void onSendClick() {
        if (mThinking) return;
        String text = mEtInput.getText().toString().trim();
        if (text.isEmpty()) return;
        mEtInput.setText("");
        sendToApi(text, false);
    }

    private void sendToApi(String userText, boolean isAnalysis) {
        if (mThinking) return;
        mThinking = true;

        // Add user message to history and UI
        sChatHistory.add(new ChatMsg(true, userText));
        String displayText = isAnalysis
                ? ("[AI分析请求] " + (mAttachedContext != null
                    ? ("drive_test".equals(mAttachedType) ? "路测数据" : "信令日志") : ""))
                : userText;
        addBubble(true, displayText);

        // Build API message list with system prompt
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", "你是一名专业的移动通信网络优化工程师，拥有丰富的LTE和5G NR网络分析经验。"
                + "请根据用户提供的网络测量数据进行专业分析，给出准确的问题诊断和可行的优化建议。"
                + "回答使用中文，格式清晰，重点突出。");
        messages.add(sys);
        for (ChatMsg m : sChatHistory)
            messages.add(msgMap(m.isUser ? "user" : "assistant", m.text));

        setTyping(true);

        final boolean forPdf = isAnalysis;
        HermesClient.chat(requireContext(), messages, new HermesClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                mThinking = false;
                setTyping(false);
                sChatHistory.add(new ChatMsg(false, response));
                addBubble(false, response);
                if (forPdf) {
                    mLastAiResponse = response;
                    mBtnExportPdf.setVisibility(View.VISIBLE);
                }
                scrollToBottom();
            }
            @Override
            public void onError(String message) {
                mThinking = false;
                setTyping(false);
                String err = "⚠ 请求失败: " + message;
                sChatHistory.add(new ChatMsg(false, err));
                addBubble(false, err);
                scrollToBottom();
            }
        });
    }

    // ── PDF export ────────────────────────────────────────────────────────────

    private void exportPdf() {
        if (mLastAiResponse == null || mLastAiResponse.isEmpty()) {
            toast("暂无分析结论可导出");
            return;
        }
        List<CellSignalManager.SimSignalData> sims = (MainActivity.signalManager != null)
                ? MainActivity.signalManager.getSimDataList() : new ArrayList<>();

        new Thread(() -> {
            File f = PdfReportBuilder.build(requireContext(),
                    mLastAiResponse, mAttachedContext, mAttachedType, sims);
            mHandler.post(() -> {
                if (f == null) { toast("PDF生成失败"); return; }
                toast("已保存: " + f.getName());
                sharePdf(f);
            });
        }).start();
    }

    private void sharePdf(File f) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享分析报告"));
        } catch (Exception e) {
            toast("分享失败: " + e.getMessage());
        }
    }

    // ── Settings dialog ───────────────────────────────────────────────────────

    private void showSettingsDialog() {
        View dlgView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_hermes_settings, null);
        EditText etUrl   = dlgView.findViewById(R.id.et_hermes_url);
        EditText etToken = dlgView.findViewById(R.id.et_hermes_token);
        EditText etModel = dlgView.findViewById(R.id.et_hermes_model);

        etUrl.setText(SettingUtils.getHermesUrl(requireContext()));
        etToken.setText(SettingUtils.getHermesToken(requireContext()));
        etModel.setText(SettingUtils.getHermesModel(requireContext()));

        new AlertDialog.Builder(requireContext())
                .setTitle("智能体 AI 接口设置")
                .setView(dlgView)
                .setPositiveButton("保存", (d, w) -> {
                    SettingUtils.setHermesUrl(requireContext(),   etUrl.getText().toString().trim());
                    SettingUtils.setHermesToken(requireContext(), etToken.getText().toString().trim());
                    SettingUtils.setHermesModel(requireContext(), etModel.getText().toString().trim());
                    toast("设置已保存");
                    fetchAndSetModel();
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("测试连接", (d, w) -> testConnection())
                .show();
    }

    private void testConnection() {
        toast("正在连接…");
        HermesClient.fetchModels(requireContext(), new HermesClient.ModelsCallback() {
            @Override public void onSuccess(List<String> models) {
                String first = models.isEmpty() ? "（无模型）" : models.get(0);
                if (!models.isEmpty() && SettingUtils.getHermesModel(requireContext()).isEmpty())
                    SettingUtils.setHermesModel(requireContext(), first);
                toast("连接成功  模型: " + first);
            }
            @Override public void onError(String message) {
                toast("连接失败: " + message);
            }
        });
    }

    private void fetchAndSetModel() {
        HermesClient.fetchModels(requireContext(), new HermesClient.ModelsCallback() {
            @Override public void onSuccess(List<String> models) {
                if (!models.isEmpty() && SettingUtils.getHermesModel(requireContext()).isEmpty())
                    SettingUtils.setHermesModel(requireContext(), models.get(0));
            }
            @Override public void onError(String message) { /* silent */ }
        });
    }

    // ── Chat UI helpers ───────────────────────────────────────────────────────

    private void addBubble(boolean isUser, String text) {
        if (mMsgContainer == null) return;

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 6, 0, 6);
        row.setLayoutParams(rowParams);

        TextView tv = new TextView(requireContext());
        if (isUser) {
            tv.setText(text);
        } else {
            Spanned rendered = MarkdownUtils.toSpanned(text);
            tv.setText(rendered != null ? rendered : text);
        }
        tv.setTextSize(13f);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));

        int maxW = (int)(getResources().getDisplayMetrics().widthPixels * 0.82f);
        tv.setMaxWidth(maxW);   // limit bubble width to 82 % of screen

        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvParams.setMargins(dp(8), 0, dp(8), 0);

        if (isUser) {
            tv.setTextColor(Color.WHITE);
            tv.setBackground(roundedBg(0xFF1565C0, dp(16)));
            tvParams.gravity = Gravity.END;
            tvParams.setMarginStart(dp(60));
            row.setGravity(Gravity.END);
        } else {
            tv.setTextColor(0xFF212121);
            tv.setBackground(roundedBg(0xFFF1F3F4, dp(16)));
            tvParams.gravity = Gravity.START;
            tvParams.setMarginEnd(dp(60));
            row.setGravity(Gravity.START);
        }

        row.addView(tv, tvParams);
        mMsgContainer.addView(row);
        scrollToBottom();
    }

    private void rebuildMessages() {
        if (mMsgContainer == null) return;
        mMsgContainer.removeAllViews();
        if (sChatHistory.isEmpty()) {
            addSystemHint();
        } else {
            for (ChatMsg m : sChatHistory) addBubble(m.isUser, m.text);
        }
    }

    private void addSystemHint() {
        TextView hint = new TextView(requireContext());
        hint.setText("智能体已就绪\n\n• 直接输入问题进行知识问答\n• 从路测/信令页面触发 AI 分析\n• 分析完成后可导出 PDF 报告");
        hint.setTextSize(13f);
        hint.setTextColor(0xFF757575);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), dp(40), dp(24), dp(24));
        mMsgContainer.addView(hint);
    }

    private void clearChat() {
        sChatHistory.clear();
        mAttachedContext = null;
        mAttachedType    = null;
        mLastAiResponse  = null;
        mBtnExportPdf.setVisibility(View.GONE);
        mCardContext.setVisibility(View.GONE);
        mMsgContainer.removeAllViews();
        addSystemHint();
    }

    private void setTyping(boolean show) {
        if (mTvTyping == null) return;
        mTvTyping.setVisibility(show ? View.VISIBLE : View.GONE);
        mBtnSend.setEnabled(!show);
        mBtnSend.setAlpha(show ? 0.5f : 1f);
        if (show) scrollToBottom();
    }

    private void scrollToBottom() {
        mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private static GradientDrawable roundedBg(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(color);
        return d;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static Map<String, String> msgMap(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private void toast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
