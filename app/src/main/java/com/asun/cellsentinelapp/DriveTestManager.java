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
import java.io.PrintWriter;
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
    private int mTestSimIndex = -1;  // -1 = all, 0 = SIM1, 1 = SIM2

    public DriveTestManager(Context context) {
        mContext = context;
        mLM = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void setStateListener(StateListener l) {
        mListener = l;
    }

    public void setTestSimIndex(int idx) {
        mTestSimIndex = idx;
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

        // Record entry for selected SIM (or all SIMs if none selected)
        DriveTestRecord lastRecord = null;
        for (int i = 0; i < sims.size(); i++) {
            if (mTestSimIndex >= 0 && i != mTestSimIndex) continue;
            DriveTestRecord rec = new DriveTestRecord(
                    location.getLatitude(), location.getLongitude(),
                    location.getAccuracy(), sims.get(i));
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

    /**
     * Write records to KML in app's external files dir.
     * Track segments are colored by RSRP quality level (5-level 网优 scale).
     * Returns the file, or null on failure.
     */
    public File exportKml() {
        if (mRecords.isEmpty()) return null;
        File dir = mContext.getExternalFilesDir("drivetest");
        if (dir == null) return null;
        if (!dir.exists()) dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(dir, "drivetest_" + ts + ".kml");

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
            pw.println("<Document>");
            pw.println("  <name>" + escapeXml(mSessionName) + "</name>");

            // Quality level styles (KML colors: AABBGGRR)
            String[][] styles = {
                {"sig_good",     "ff50b000", "优质 (≥-80)"},
                {"sig_fair",     "ff50d092", "良好 (<-80)"},
                {"sig_moderate", "ff00ffff", "中等 (<-95)"},
                {"sig_poor",     "ff008cff", "较差 (<-105)"},
                {"sig_bad",      "ff0000ff", "极差 (<-115)"},
            };
            for (String[] s : styles) {
                pw.println("  <Style id=\"" + s[0] + "\">");
                pw.println("    <LineStyle><color>" + s[1] + "</color><width>4</width></LineStyle>");
                pw.println("    <LabelStyle><scale>0</scale></LabelStyle>");
                pw.println("  </Style>");
            }

            // Track: output one Placemark per consecutive same-quality run
            pw.println("  <Folder><name>路测轨迹</name>");
            if (!mRecords.isEmpty()) {
                int curLevel = rsrpLevel(mRecords.get(0).rsrp);
                StringBuilder coords = new StringBuilder();
                coords.append(String.format(Locale.US, "%.6f,%.6f,0\n",
                        mRecords.get(0).longitude, mRecords.get(0).latitude));

                for (int i = 1; i < mRecords.size(); i++) {
                    DriveTestRecord r = mRecords.get(i);
                    int level = rsrpLevel(r.rsrp);
                    coords.append(String.format(Locale.US, "%.6f,%.6f,0\n", r.longitude, r.latitude));
                    if (level != curLevel || i == mRecords.size() - 1) {
                        writeSegment(pw, styles[curLevel][0], coords.toString());
                        coords = new StringBuilder();
                        coords.append(String.format(Locale.US, "%.6f,%.6f,0\n", r.longitude, r.latitude));
                        curLevel = level;
                    }
                }
                // Flush last segment
                if (coords.length() > 0)
                    writeSegment(pw, styles[curLevel][0], coords.toString());
            }
            pw.println("  </Folder>");

            // Individual measurement points
            pw.println("  <Folder><name>测量点</name>");
            for (DriveTestRecord r : mRecords) {
                pw.println("    <Placemark>");
                pw.println("      <styleUrl>#" + styles[rsrpLevel(r.rsrp)][0] + "</styleUrl>");
                pw.println("      <name>" + (r.rsrp != Integer.MAX_VALUE ? r.rsrp + "dBm" : "N/A") + "</name>");
                pw.println("      <description><![CDATA[" +
                        r.simLabel + "  " + r.rat + "<br/>" +
                        "RSRP:" + fmt(r.rsrp) + "  SINR:" + fmt(r.sinr) + "  RSRQ:" + fmt(r.rsrq) +
                        "]]></description>");
                pw.println("      <Point><coordinates>" +
                        String.format(Locale.US, "%.6f,%.6f,0", r.longitude, r.latitude) +
                        "</coordinates></Point>");
                pw.println("    </Placemark>");
            }
            pw.println("  </Folder>");

            pw.println("</Document>");
            pw.println("</kml>");
        } catch (IOException e) {
            return null;
        }
        return file;
    }

    private static void writeSegment(PrintWriter pw, String styleId, String coords) {
        pw.println("    <Placemark>");
        pw.println("      <styleUrl>#" + styleId + "</styleUrl>");
        pw.println("      <LineString><tessellate>1</tessellate>");
        pw.println("        <coordinates>" + coords + "        </coordinates>");
        pw.println("      </LineString>");
        pw.println("    </Placemark>");
    }

    /** RSRP quality level 0 (best) … 4 (worst) */
    private static int rsrpLevel(int rsrp) {
        if (rsrp == Integer.MAX_VALUE) return 4;
        if (rsrp >= -80)  return 0;
        if (rsrp >= -95)  return 1;
        if (rsrp >= -105) return 2;
        if (rsrp >= -115) return 3;
        return 4;
    }

    private static String fmt(int v) {
        return v == Integer.MAX_VALUE ? "N/A" : String.valueOf(v);
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
