package com.asun.cellsentinelapp;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class LoginActivity extends AppCompatActivity {

    private EditText   mEtUsername;
    private EditText   mEtPassword;
    private EditText   mEtCaptcha;
    private ImageView  mIvCaptcha;
    private Button     mBtnLogin;
    private ProgressBar mProgress;
    private TextView   mTvError;

    private String mCaptchaUuid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

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
                // Re-show in case a previous error had hidden them
                mIvCaptcha.setVisibility(View.VISIBLE);
                ((View) mEtCaptcha.getParent().getParent()).setVisibility(View.VISIBLE);
                mTvError.setVisibility(View.GONE);
            }

            @Override
            public void onDisabled() {
                // Server has captcha turned off — hide the row entirely
                hideCaptchaRow();
            }

            @Override
            public void onError(String msg) {
                // Network error or wrong URL — keep captcha visible so user can tap to retry
                mIvCaptcha.setAlpha(1.0f);
                mIvCaptcha.setImageResource(android.R.drawable.ic_menu_rotate);
                showError("验证码加载失败，点击图片重试");
            }
        });
    }

    /**
     * Called when server confirms captcha is disabled.
     * Hides the captcha row completely so the user doesn't need to fill it in.
     */
    private void hideCaptchaRow() {
        mIvCaptcha.setVisibility(View.GONE);
        // Hide the TextInputLayout wrapper (grandparent of TextInputEditText)
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
