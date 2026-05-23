package com.asun.cellsentinelapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {

    private TextView mTvLoginStatus;
    private TextView mTvNickName;
    private EditText mEtServerUrl;
    private EditText mEtBackupServerUrl;
    private Button   mBtnLogin;
    private Button   mBtnLogout;
    private Button   mBtnSaveUrl;
    private Button   mBtnUploadSignal;
    private View     mCardUser;

    private EditText mEtHermesUrl;
    private EditText mEtHermesToken;
    private EditText mEtHermesModel;
    private Button   mBtnSaveHermes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_profile, container, false);

        mTvLoginStatus  = root.findViewById(R.id.tv_login_status);
        mTvNickName     = root.findViewById(R.id.tv_nick_name);
        mEtServerUrl    = root.findViewById(R.id.et_server_url);
        mBtnLogin       = root.findViewById(R.id.btn_login);
        mBtnLogout      = root.findViewById(R.id.btn_logout);
        mBtnSaveUrl     = root.findViewById(R.id.btn_save_url);
        mBtnUploadSignal = root.findViewById(R.id.btn_upload_signal);
        mCardUser       = root.findViewById(R.id.card_user);

        mEtBackupServerUrl = root.findViewById(R.id.et_backup_server_url);
        mEtServerUrl.setText(SettingUtils.getServerUrl(requireContext()));
        mEtBackupServerUrl.setText(SettingUtils.getBackupServerUrl(requireContext()));

        mEtHermesUrl   = root.findViewById(R.id.et_hermes_url);
        mEtHermesToken = root.findViewById(R.id.et_hermes_token);
        mEtHermesModel = root.findViewById(R.id.et_hermes_model);
        mBtnSaveHermes = root.findViewById(R.id.btn_save_hermes);
        mEtHermesUrl.setText(SettingUtils.getHermesUrl(requireContext()));
        mEtHermesToken.setText(SettingUtils.getHermesToken(requireContext()));
        mEtHermesModel.setText(SettingUtils.getHermesModel(requireContext()));
        mBtnSaveHermes.setOnClickListener(v -> saveHermesSettings());

        mBtnSaveUrl.setOnClickListener(v -> saveServerUrl());
        mBtnLogin.setOnClickListener(v -> goToLogin());
        mBtnLogout.setOnClickListener(v -> logout());
        mBtnUploadSignal.setOnClickListener(v -> uploadSignalNow());

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLoginState();
    }

    private void refreshLoginState() {
        boolean loggedIn = SettingUtils.isLoggedIn(requireContext());
        if (loggedIn) {
            String nick = SettingUtils.getNickName(requireContext());
            String user = SettingUtils.getUsername(requireContext());
            mTvLoginStatus.setText("已登录");
            mTvNickName.setText(nick.isEmpty() ? user : nick + " (" + user + ")");
            mCardUser.setVisibility(View.VISIBLE);
            mBtnLogin.setVisibility(View.GONE);
            mBtnLogout.setVisibility(View.VISIBLE);
            mBtnUploadSignal.setEnabled(true);
        } else {
            mTvLoginStatus.setText("未登录");
            mTvNickName.setText("—");
            mCardUser.setVisibility(View.GONE);
            mBtnLogin.setVisibility(View.VISIBLE);
            mBtnLogout.setVisibility(View.GONE);
            mBtnUploadSignal.setEnabled(false);
        }
    }

    private void saveServerUrl() {
        String url = mEtServerUrl.getText().toString().trim();
        if (url.isEmpty()) {
            toast("主服务器地址不能为空");
            return;
        }
        SettingUtils.setServerUrl(requireContext(), url);
        String backup = mEtBackupServerUrl.getText().toString().trim();
        SettingUtils.setBackupServerUrl(requireContext(), backup);
        toast("服务器地址已保存" + (backup.isEmpty() ? "" : "（备用已配置）"));
    }

    private void goToLogin() {
        startActivity(new Intent(requireContext(), LoginActivity.class));
    }

    private void logout() {
        SettingUtils.clearToken(requireContext());
        refreshLoginState();
        toast("已退出登录");
    }

    private void uploadSignalNow() {
        if (MainActivity.signalManager == null) {
            toast("信号管理器未就绪");
            return;
        }
        mBtnUploadSignal.setEnabled(false);
        RuoyiApi.uploadSignalSnapshot(requireContext(),
                MainActivity.signalManager.getSimDataList(),
                new RuoyiApi.UploadCallback() {
                    @Override public void onSuccess(String msg) {
                        toast(msg);
                        if (mBtnUploadSignal != null) mBtnUploadSignal.setEnabled(true);
                    }
                    @Override public void onError(String msg) {
                        toast("上报失败: " + msg);
                        if (mBtnUploadSignal != null) mBtnUploadSignal.setEnabled(true);
                    }
                });
    }

    private void saveHermesSettings() {
        SettingUtils.setHermesUrl(requireContext(),
                mEtHermesUrl.getText().toString().trim());
        SettingUtils.setHermesToken(requireContext(),
                mEtHermesToken.getText().toString().trim());
        SettingUtils.setHermesModel(requireContext(),
                mEtHermesModel.getText().toString().trim());
        toast("智能体 设置已保存");
    }

    private void toast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
