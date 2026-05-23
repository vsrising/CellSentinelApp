package com.asun.cellsentinelapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;

import java.io.File;
import java.util.List;

public class SignalingLogFragment extends Fragment implements SignalingLogManager.Listener {

    private static SignalingLogManager sLogManager;

    private Button     mBtnToggle;
    private Button     mBtnClear;
    private Button     mBtnExport;
    private ScrollView mScrollView;
    private TextView   mTvLog;
    private Chip       mChipAll, mChipHandover, mChipRat, mChipService;
    private Chip       mChipSimAll, mChipSim1, mChipSim2;

    private SignalingLogManager.EventType mFilter = null; // null = all

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_signaling_log, container, false);

        mBtnToggle   = root.findViewById(R.id.btn_sig_toggle);
        mBtnClear    = root.findViewById(R.id.btn_sig_clear);
        mBtnExport   = root.findViewById(R.id.btn_sig_export);
        mScrollView  = root.findViewById(R.id.sv_sig_log);
        mTvLog       = root.findViewById(R.id.tv_sig_log);
        mChipAll     = root.findViewById(R.id.chip_filter_all);
        mChipHandover= root.findViewById(R.id.chip_filter_handover);
        mChipRat     = root.findViewById(R.id.chip_filter_rat);
        mChipService = root.findViewById(R.id.chip_filter_service);
        mChipSimAll  = root.findViewById(R.id.chip_sim_all);
        mChipSim1    = root.findViewById(R.id.chip_sim_1);
        mChipSim2    = root.findViewById(R.id.chip_sim_2);

        if (sLogManager == null)
            sLogManager = new SignalingLogManager(requireContext());

        mBtnToggle.setText(sLogManager.isRunning() ? "停止监听" : "开始监听");

        mBtnToggle.setOnClickListener(v -> {
            if (sLogManager.isRunning()) {
                sLogManager.stop();
                mBtnToggle.setText("开始监听");
            } else {
                sLogManager.start();
                mBtnToggle.setText("停止监听");
            }
        });

        mBtnClear.setOnClickListener(v -> {
            sLogManager.clearEvents();
            mTvLog.setText("");
        });

        mBtnExport.setOnClickListener(v -> exportCsv());

        setupSimChips();
        setupFilterChips();
        rebuildLog();
        return root;
    }

    private void setupSimChips() {
        // Restore current selection
        int cur = sLogManager.getTargetSimIndex();
        mChipSimAll.setChecked(cur < 0);
        mChipSim1.setChecked(cur == 0);
        mChipSim2.setChecked(cur == 1);

        View.OnClickListener cl = v -> {
            mChipSimAll.setChecked(false);
            mChipSim1.setChecked(false);
            mChipSim2.setChecked(false);
            if (v == mChipSimAll) { mChipSimAll.setChecked(true); sLogManager.setTargetSimIndex(-1); }
            else if (v == mChipSim1) { mChipSim1.setChecked(true); sLogManager.setTargetSimIndex(0); }
            else { mChipSim2.setChecked(true); sLogManager.setTargetSimIndex(1); }
            rebuildLog();
        };
        mChipSimAll.setOnClickListener(cl);
        mChipSim1.setOnClickListener(cl);
        mChipSim2.setOnClickListener(cl);
    }

    private void setupFilterChips() {
        View.OnClickListener cl = v -> {
            mChipAll.setChecked(false);
            mChipHandover.setChecked(false);
            mChipRat.setChecked(false);
            mChipService.setChecked(false);
            if (v == mChipAll)      { mChipAll.setChecked(true);      mFilter = null; }
            else if (v == mChipHandover) { mChipHandover.setChecked(true); mFilter = SignalingLogManager.EventType.HANDOVER; }
            else if (v == mChipRat) { mChipRat.setChecked(true);      mFilter = SignalingLogManager.EventType.RAT_CHANGE; }
            else                    { mChipService.setChecked(true);   mFilter = SignalingLogManager.EventType.SERVICE_STATE; }
            rebuildLog();
        };
        mChipAll.setOnClickListener(cl);
        mChipHandover.setOnClickListener(cl);
        mChipRat.setOnClickListener(cl);
        mChipService.setOnClickListener(cl);
    }

    private void exportCsv() {
        if (sLogManager.getEvents().isEmpty()) {
            Toast.makeText(requireContext(), "暂无事件可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = sLogManager.exportCsv();
        if (f == null) {
            Toast.makeText(requireContext(), "导出失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), "已保存: " + f.getName(), Toast.LENGTH_LONG).show();
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享 CSV"));
        } catch (Exception ignored) {}
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sLogManager != null) {
            sLogManager.addListener(this);
            rebuildLog();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sLogManager != null) sLogManager.removeListener(this);
    }

    @Override
    public void onNewEvent(SignalingLogManager.SignalingEvent event) {
        if (mFilter != null && event.type != mFilter) return;
        appendEvent(event);
        mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void rebuildLog() {
        if (sLogManager == null || mTvLog == null) return;
        List<SignalingLogManager.SignalingEvent> events = sLogManager.getEvents();
        StringBuilder sb = new StringBuilder();
        for (SignalingLogManager.SignalingEvent e : events) {
            if (mFilter != null && e.type != mFilter) continue;
            appendTo(sb, e);
        }
        mTvLog.setText(sb.length() == 0 ? "暂无事件" : sb.toString());
        mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void appendEvent(SignalingLogManager.SignalingEvent e) {
        if (mTvLog == null) return;
        StringBuilder sb = new StringBuilder(mTvLog.getText());
        if (sb.length() == 0 || "暂无事件".contentEquals(mTvLog.getText()))
            sb = new StringBuilder();
        appendTo(sb, e);
        mTvLog.setText(sb.toString());
    }

    private static void appendTo(StringBuilder sb, SignalingLogManager.SignalingEvent e) {
        String tag;
        switch (e.type) {
            case HANDOVER:      tag = "[HO ] "; break;
            case RAT_CHANGE:    tag = "[RAT] "; break;
            case SERVICE_STATE: tag = "[SVC] "; break;
            case SIGNAL_CHANGE: tag = "[SIG] "; break;
            default:            tag = "[--- ] "; break;
        }
        sb.append(e.formattedTime()).append("  ").append(tag).append(e.summary);
        if (!e.detail.isEmpty()) sb.append("\n             ").append(e.detail);
        sb.append("\n");
    }
}
