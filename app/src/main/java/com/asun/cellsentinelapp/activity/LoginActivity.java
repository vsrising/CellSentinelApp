package com.asun.cellsentinelapp.activity;

import com.asun.cellsentinelapp.R;
import com.asun.cellsentinelapp.network.RuoyiApi;
import com.asun.cellsentinelapp.util.SettingUtils;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class LoginActivity extends AppCompatActivity {

    private EditText    mEtUsername;
    private EditText    mEtPassword;
    private EditText    mEtCaptcha;
    private ImageView   mIvCaptcha;
    private Button      mBtnLogin;
    private ProgressBar mProgress;
    private TextView    mTvError;
    private CheckBox    mCbRemember;

    private String mCaptchaUuid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        WindowInsetsControllerCompat insetsCtrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsCtrl.hide(WindowInsetsCompat.Type.statusBars());
        insetsCtrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        Toolbar toolbar = findViewById(R.id.toolbar_login);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("登录");
        }

        mEtUsername = findViewById(R.id.et_username);
        mEtPassword = findViewById(R.id.et_password);
        mEtCaptcha  = findViewById(R.id.et_captcha);
        mIvCaptcha  = findViewById(R.id.iv_captcha);
        mBtnLogin   = findViewById(R.id.btn_do_login);
        mProgress   = findViewById(R.id.login_progress);
        mTvError    = findViewById(R.id.tv_login_error);
        mCbRemember = findViewById(R.id.cb_remember);

        // Pre-fill saved credentials
        if (SettingUtils.hasSavedCredentials(this)) {
            mEtUsername.setText(SettingUtils.getSavedUsername(this));
            mEtPassword.setText(SettingUtils.getSavedPassword(this));
            mCbRemember.setChecked(true);
        }

        mIvCaptcha.setOnClickListener(v -> loadCaptcha());
        mBtnLogin.setOnClickListener(v -> doLogin());

        loadCaptcha();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadCaptcha() {
        mIvCaptcha.setAlpha(0.4f);
        mIvCaptcha.setImageBitmap(null);
        RuoyiApi.getCaptcha(this, new RuoyiApi.CaptchaCallback() {
            @Override
            public void onSuccess(Bitmap image, String uuid) {
                mCaptchaUuid = uuid;
                mIvCaptcha.setImageBitmap(image);
                mIvCaptcha.setAlpha(1.0f);
                mEtCaptcha.setText("");
                mIvCaptcha.setVisibility(View.VISIBLE);
                ((View) mEtCaptcha.getParent().getParent()).setVisibility(View.VISIBLE);
                mTvError.setVisibility(View.GONE);
            }

            @Override
            public void onDisabled() {
                hideCaptchaRow();
            }

            @Override
            public void onError(String msg) {
                mIvCaptcha.setAlpha(1.0f);
                mIvCaptcha.setImageResource(android.R.drawable.ic_menu_rotate);
                showError("验证码加载失败，点击图片重试");
            }
        });
    }

    private void hideCaptchaRow() {
        mIvCaptcha.setVisibility(View.GONE);
        View inputLayout = (View) mEtCaptcha.getParent().getParent();
        if (inputLayout != null) inputLayout.setVisibility(View.GONE);
    }

    private void doLogin() {
        String username = mEtUsername.getText().toString().trim();
        String password = mEtPassword.getText().toString().trim();
        String code     = mEtCaptcha.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("用户名和密码不能为空");
            return;
        }

        setLoading(true);
        mTvError.setVisibility(View.GONE);

        RuoyiApi.login(this, username, password, code, mCaptchaUuid,
                new RuoyiApi.LoginCallback() {
                    @Override
                    public void onSuccess(String token) {
                        setLoading(false);
                        if (mCbRemember.isChecked()) {
                            SettingUtils.saveLoginCredentials(LoginActivity.this, username, password);
                        } else {
                            SettingUtils.clearSavedCredentials(LoginActivity.this);
                        }
                        SettingUtils.setNeedCacheRefresh(LoginActivity.this, true);
                        RuoyiApi.getInfo(LoginActivity.this, new RuoyiApi.UserInfoCallback() {
                            @Override
                            public void onSuccess(String u, String n) {
                                Toast.makeText(LoginActivity.this,
                                        "欢迎，" + (n.isEmpty() ? u : n),
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onError(String msg) {
                                finish();
                            }
                        });
                    }

                    @Override
                    public void onError(String msg) {
                        setLoading(false);
                        showError(msg);
                        loadCaptcha();
                    }
                });
    }

    private void setLoading(boolean loading) {
        mProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        mBtnLogin.setEnabled(!loading);
    }

    private void showError(String msg) {
        mTvError.setText(msg);
        mTvError.setVisibility(View.VISIBLE);
    }
}
