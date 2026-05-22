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
        RuoyiApi.getCaptcha(this, new RuoyiApi.CaptchaCallback() {
            @Override
            public void onSuccess(Bitmap image, String uuid) {
                mCaptchaUuid = uuid;
                mIvCaptcha.setImageBitmap(image);
                mEtCaptcha.setText("");
            }

            @Override
            public void onError(String msg) {
                // Captcha may be disabled on server; hide field and proceed without it
                mIvCaptcha.setVisibility(View.GONE);
                mEtCaptcha.setVisibility(View.GONE);
            }
        });
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
                        // Fetch user info after login
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
                                // Login succeeded even if getInfo fails
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
