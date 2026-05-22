package com.asun.cellsentinelapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DriveTestManager implements LocationListener {

    private static final long MIN_TIME_MS   = 2000;  // 2 seconds
    private static final float MIN_DIST_M   = 5f;    // 5 metres

    public interface StateListener {
        void onLocationUpdate(Location location, DriveTestRecord newRecord);
        void onError(String message);
    }

    private final Context mContext;
    private final LocationManager mLM;
    private final List<DriveTestRecord> mRecords = new ArrayList<>();
    private StateListener mListener;
    private boolean mRecording = false;
    private String mSessionName = "";

    public DriveTestManager(Context context) {
        mContext = context;
        mLM = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void setStateListener(StateListener l) {
        mListener = l;
    }

    @SuppressLint("MissingPermission")
    public boolean startRecording(String sessionName) {
        if (!hasLocationPermission()) {
            if (mListener != null) mListener.onError("缺少位置权限");
            return false;
        }
        if (!mLM.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (mListener != null) mListener.onError("GPS 未开启，请在设置中开启");
            return false;
        }
        mSessionName = sessionName;
        mRecording = true;
        mLM.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_MS, MIN_DIST_M, this);
        // Also request network for faster first fix
        if (mLM.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            mLM.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_MS, MIN_DIST_M, this);
        }
        return true;
    }

    public void stopRecording() {
        mRecording = false;
        mLM.removeUpdates(this);
    }

    public void clearRecords() {
        mRecords.clear();
    }

    public boolean isRecording() {
        return mRecording;
    }

    public String getSessionName() {
        return mSessionName;
    }

    public List<DriveTestRecord> getRecords() {
        return Collections.unmodifiableList(mRecords);
    }

    public int getRecordCount() {
        return mRecords.size();
    }

    // ── LocationListener ─────────────────────────────────────────────────────

    @Override
    public void onLocationChanged(Location location) {
        if (!mRecording) return;

        List<CellSignalManager.SimSignalData> sims =
                MainActivity.signalManager != null
                        ? MainActivity.signalManager.getSimDataList()
                        : null;

        if (sims == null || sims.isEmpty()) return;

        // Record one entry per SIM
        DriveTestRecord lastRecord = null;
        for (CellSignalManager.SimSignalData sim : sims) {
            DriveTestRecord rec = new DriveTestRecord(
                    location.getLatitude(), location.getLongitude(),
                    location.getAccuracy(), sim);
            mRecords.add(rec);
            lastRecord = rec;
        }

        if (mListener != null && lastRecord != null) {
            mListener.onLocationUpdate(location, lastRecord);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider) && mRecording) {
            if (mListener != null) mListener.onError("GPS 已关闭");
        }
    }

    // ── Export ───────────────────────────────────────────────────────────────

    /** Write records to CSV in app's external files dir. Returns the file, or null on failure. */
    public File exportCsv() {
        if (mRecords.isEmpty()) return null;
        File dir = mContext.getExternalFilesDir("drivetest");
        if (dir == null) return null;
        if (!dir.exists()) dir.mkdirs();

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        File file = new File(dir, "drivetest_" + ts + ".csv");

        try (FileWriter fw = new FileWriter(file)) {
            fw.write(DriveTestRecord.csvHeader() + "\n");
            for (DriveTestRecord r : mRecords) {
                fw.write(r.toCsvRow() + "\n");
            }
        } catch (IOException e) {
            return null;
        }
        return file;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static String generateSessionName() {
        return "路测_" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date());
    }
}
