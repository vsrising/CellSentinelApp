package com.asun.cellsentinelapp.fragment;

import com.asun.cellsentinelapp.R;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocationInfoFragment extends Fragment implements LocationListener {

    private LocationManager mLocMgr;
    private TextView        mTvLocationInfo;
    private TextView        mTvProviderInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_location_info, container, false);
        mTvLocationInfo = root.findViewById(R.id.tv_location_info);
        mTvProviderInfo = root.findViewById(R.id.tv_provider_info);
        mLocMgr = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                mLocMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
                mLocMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this);
                // Show last known immediately
                Location last = mLocMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last != null) updateLocation(last);
            } catch (Exception ignored) {}
        }
        updateProviders();
    }

    @Override
    public void onPause() {
        super.onPause();
        try { mLocMgr.removeUpdates(this); } catch (Exception ignored) {}
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        updateLocation(location);
    }

    private void updateLocation(Location loc) {
        if (mTvLocationInfo == null) return;
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date(loc.getTime()));
        // Convert decimal degrees to DMS
        String latDms = toDms(Math.abs(loc.getLatitude()))
                + (loc.getLatitude() >= 0 ? " N" : " S");
        String lonDms = toDms(Math.abs(loc.getLongitude()))
                + (loc.getLongitude() >= 0 ? " E" : " W");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "纬度       : %.6f°\n             (%s)\n" +
                "经度       : %.6f°\n             (%s)\n",
                loc.getLatitude(), latDms, loc.getLongitude(), lonDms));
        if (loc.hasAltitude())
            sb.append(String.format(Locale.US, "高程       : %.1f m\n", loc.getAltitude()));
        sb.append(String.format(Locale.US, "精度       : ±%.1f m\n", loc.getAccuracy()));
        if (loc.hasSpeed())
            sb.append(String.format(Locale.US, "速度       : %.1f km/h\n",
                    loc.getSpeed() * 3.6f));
        if (loc.hasBearing())
            sb.append(String.format(Locale.US, "方向       : %.1f°\n", loc.getBearing()));
        sb.append("提供者     : ").append(loc.getProvider()).append("\n");
        sb.append("更新时间   : ").append(time);
        mTvLocationInfo.setText(sb.toString());
    }

    private void updateProviders() {
        if (mTvProviderInfo == null || mLocMgr == null) return;
        List<String> providers = mLocMgr.getAllProviders();
        StringBuilder sb = new StringBuilder();
        for (String p : providers) {
            boolean enabled = mLocMgr.isProviderEnabled(p);
            sb.append(p).append(": ").append(enabled ? "已启用" : "已禁用").append("\n");
        }
        mTvProviderInfo.setText(sb.toString().trim());
    }

    private static String toDms(double decimal) {
        int deg = (int) decimal;
        double minFull = (decimal - deg) * 60;
        int min = (int) minFull;
        double sec = (minFull - min) * 60;
        return String.format(Locale.US, "%d°%d'%.2f\"", deg, min, sec);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(@NonNull String provider) { updateProviders(); }
    @Override public void onProviderDisabled(@NonNull String provider) { updateProviders(); }
}
