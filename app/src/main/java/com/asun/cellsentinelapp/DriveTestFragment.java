package com.asun.cellsentinelapp;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DriveTestFragment extends Fragment implements DriveTestManager.StateListener {

    private DriveTestManager mManager;
    private RecordAdapter mAdapter;

    private TextView  mTvStatus;
    private TextView  mTvCount;
    private Button    mBtnStartStop;
    private Button    mBtnExport;
    private Button    mBtnUpload;
    private Button    mBtnClear;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_drive_test, container, false);

        mTvStatus     = root.findViewById(R.id.tv_gps_status);
        mTvCount      = root.findViewById(R.id.tv_record_count);
        mBtnStartStop = root.findViewById(R.id.btn_start_stop);
        mBtnExport    = root.findViewById(R.id.btn_export);
        mBtnUpload    = root.findViewById(R.id.btn_upload);
        mBtnClear     = root.findViewById(R.id.btn_clear);

        RecyclerView rv = root.findViewById(R.id.rv_records);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new RecordAdapter();
        rv.setAdapter(mAdapter);

        mManager = new DriveTestManager(requireContext());
        mManager.setStateListener(this);

        mBtnStartStop.setOnClickListener(v -> toggleRecording());
        mBtnExport.setOnClickListener(v -> exportCsv());
        mBtnUpload.setOnClickListener(v -> uploadRecords());
        mBtnClear.setOnClickListener(v -> clearRecords());

        updateUi();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mManager.isRecording()) mManager.stopRecording();
    }

    // ── Recording control ────────────────────────────────────────────────────

    private void toggleRecording() {
        if (mManager.isRecording()) {
            mManager.stopRecording();
            toast("已停止录制，共 " + mManager.getRecordCount() + " 条");
        } else {
            String name = DriveTestManager.generateSessionName();
            boolean ok = mManager.startRecording(name);
            if (ok) toast("开始路测录制: " + name);
        }
        updateUi();
    }

    private void exportCsv() {
        if (mManager.getRecordCount() == 0) {
            toast("暂无数据");
            return;
        }
        File f = mManager.exportCsv();
        if (f != null) {
            toast("已导出: " + f.getAbsolutePath());
        } else {
            toast("导出失败，请检查存储权限");
        }
    }

    private void uploadRecords() {
        if (!SettingUtils.isLoggedIn(requireContext())) {
            toast("请先在「我的」页面登录");
            return;
        }
        if (mManager.getRecordCount() == 0) {
            toast("暂无路测数据");
            return;
        }
        mBtnUpload.setEnabled(false);
        RuoyiApi.uploadDriveTest(requireContext(),
                new ArrayList<>(mManager.getRecords()),
                mManager.getSessionName(),
                new RuoyiApi.UploadCallback() {
                    @Override public void onSuccess(String msg) {
                        toast(msg);
                        if (mBtnUpload != null) mBtnUpload.setEnabled(true);
                    }
                    @Override public void onError(String msg) {
                        toast("上报失败: " + msg);
                        if (mBtnUpload != null) mBtnUpload.setEnabled(true);
                    }
                });
    }

    private void clearRecords() {
        if (mManager.isRecording()) {
            toast("请先停止录制");
            return;
        }
        mManager.clearRecords();
        mAdapter.setRecords(new ArrayList<>());
        updateUi();
        toast("已清除");
    }

    // ── DriveTestManager.StateListener ──────────────────────────────────────

    @Override
    public void onLocationUpdate(Location location, DriveTestRecord newRecord) {
        String gps = String.format(Locale.US, "GPS  %.5f, %.5f  精度:%.0fm",
                location.getLatitude(), location.getLongitude(), location.getAccuracy());
        mTvStatus.setText(gps);
        mTvCount.setText("已录制 " + mManager.getRecordCount() + " 条");
        mAdapter.addRecord(newRecord);
    }

    @Override
    public void onError(String message) {
        toast(message);
        if (mManager.isRecording()) {
            mManager.stopRecording();
            updateUi();
        }
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private void updateUi() {
        boolean rec = mManager.isRecording();
        mBtnStartStop.setText(rec ? "停止路测" : "开始路测");
        mBtnStartStop.setBackgroundTintList(
                requireContext().getColorStateList(
                        rec ? android.R.color.holo_red_light : android.R.color.holo_green_dark));
        mTvCount.setText("已录制 " + mManager.getRecordCount() + " 条");
        if (!rec && mTvStatus.getText().toString().startsWith("GPS")) {
            // Keep last known location text
        } else if (!rec) {
            mTvStatus.setText("等待 GPS...");
        }
    }

    private void toast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // ── RecyclerView Adapter ─────────────────────────────────────────────────

    private static class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.VH> {

        private final List<DriveTestRecord> mList = new ArrayList<>();

        void setRecords(List<DriveTestRecord> list) {
            mList.clear();
            mList.addAll(list);
            notifyDataSetChanged();
        }

        void addRecord(DriveTestRecord r) {
            mList.add(0, r);  // newest at top
            notifyItemInserted(0);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_drive_record, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            h.bind(mList.get(position));
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvTime, tvCoords, tvSignal;

            VH(@NonNull View itemView) {
                super(itemView);
                tvTime   = itemView.findViewById(R.id.tv_time);
                tvCoords = itemView.findViewById(R.id.tv_coords);
                tvSignal = itemView.findViewById(R.id.tv_signal_summary);
            }

            void bind(DriveTestRecord r) {
                tvTime.setText(r.getTimeString());
                tvCoords.setText(String.format(Locale.US, "%.5f, %.5f", r.latitude, r.longitude));
                tvSignal.setText(r.getSignalSummary());
            }
        }
    }
}
