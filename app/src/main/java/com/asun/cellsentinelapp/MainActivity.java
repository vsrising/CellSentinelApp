package com.asun.cellsentinelapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements CellSignalManager.UpdateListener {

    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
    };

    public static CellSignalManager signalManager;

    private SignalContainerFragment mSignalFragment;
    private DriveTestFragment       mDriveTestFragment;
    private ProfileFragment         mProfileFragment;
    private Fragment                mCurrentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setupFragments(savedInstanceState);
        setupBottomNav();

        if (hasAllPermissions()) {
            initSignalManager();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
        }
    }

    private void setupFragments(Bundle savedState) {
        if (savedState != null) {
            // Recover fragments after process re-creation
            mSignalFragment    = (SignalContainerFragment) getSupportFragmentManager()
                    .findFragmentByTag("signal");
            mDriveTestFragment = (DriveTestFragment) getSupportFragmentManager()
                    .findFragmentByTag("drivetest");
            mProfileFragment   = (ProfileFragment) getSupportFragmentManager()
                    .findFragmentByTag("profile");
        }
        if (mSignalFragment    == null) mSignalFragment    = new SignalContainerFragment();
        if (mDriveTestFragment == null) mDriveTestFragment = new DriveTestFragment();
        if (mProfileFragment   == null) mProfileFragment   = new ProfileFragment();

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, mSignalFragment,    "signal")
                .add(R.id.fragment_container, mDriveTestFragment, "drivetest")
                .add(R.id.fragment_container, mProfileFragment,   "profile")
                .hide(mDriveTestFragment)
                .hide(mProfileFragment)
                .commitAllowingStateLoss();

        mCurrentFragment = mSignalFragment;
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            Fragment target;
            int id = item.getItemId();
            if      (id == R.id.nav_signal)    target = mSignalFragment;
            else if (id == R.id.nav_drivetest) target = mDriveTestFragment;
            else if (id == R.id.nav_profile)   target = mProfileFragment;
            else return false;

            if (target != mCurrentFragment) {
                getSupportFragmentManager().beginTransaction()
                        .hide(mCurrentFragment)
                        .show(target)
                        .commit();
                mCurrentFragment = target;
            }
            return true;
        });
    }

    private boolean hasAllPermissions() {
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void initSignalManager() {
        if (signalManager != null) signalManager.release();
        signalManager = new CellSignalManager(this, this);
        if (mSignalFragment != null) mSignalFragment.refreshAdapter();
    }

    @Override
    public void onSignalUpdated() {
        if (mSignalFragment != null) mSignalFragment.notifySignalUpdate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            initSignalManager();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (signalManager != null) {
            signalManager.release();
            signalManager = null;
        }
    }
}
