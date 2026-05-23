package com.asun.cellsentinelapp;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DriveTestFragment extends Fragment implements DriveTestManager.StateListener {

    // ── Map tile sources ──────────────────────────────────────────────────────

    private static final String[] LAYER_NAMES = {"OSM 标准", "ESRI 卫星", "Google 卫星", "高德 卫星"};

    // ── Track metric color coding ─────────────────────────────────────────────
    private static final int METRIC_RSRP = 0;
    private static final int METRIC_SINR = 1;
    private static final int METRIC_RSRQ = 2;
    private static final String[] METRIC_NAMES = {"RSRP", "SINR", "RSRQ"};

    // Threshold arrays (best→worst). getMetricColor() walks these top-down.
    // RSRP (dBm) – 3GPP standard breakpoints
    private static final int[] RSRP_THRESH = { -80, -95, -105, -115 };
    private static final int[] RSRP_COLORS = { 0xFF00B050, 0xFF92D050, 0xFFFFFF00, 0xFFFF8C00, 0xFFFF0000 };
    // SINR (dB)
    private static final int[] SINR_THRESH = { 20, 10, 0, -3 };
    private static final int[] SINR_COLORS = { 0xFF00B050, 0xFF92D050, 0xFFFFFF00, 0xFFFF8C00, 0xFFFF0000 };
    // RSRQ (dB)
    private static final int[] RSRQ_THRESH = { -6, -9, -12, -15 };
    private static final int[] RSRQ_COLORS = { 0xFF00B050, 0xFF92D050, 0xFFFFFF00, 0xFFFF8C00, 0xFFFF0000 };

    private static OnlineTileSourceBase esriSatellite() {
        return new XYTileSource("ESRISatellite", 0, 19, 256, ".jpg",
                new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"}) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return getBaseUrl()
                        + MapTileIndex.getZoom(pMapTileIndex) + "/"
                        + MapTileIndex.getY(pMapTileIndex) + "/"
                        + MapTileIndex.getX(pMapTileIndex);
            }
        };
    }

    private static OnlineTileSourceBase googleSatellite() {
        return new XYTileSource("GoogleSatellite", 0, 20, 256, ".png",
                new String[]{"https://mt0.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"}) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return "https://mt0.google.com/vt/lyrs=s"
                        + "&x=" + MapTileIndex.getX(pMapTileIndex)
                        + "&y=" + MapTileIndex.getY(pMapTileIndex)
                        + "&z=" + MapTileIndex.getZoom(pMapTileIndex);
            }
        };
    }

    private static OnlineTileSourceBase amapSatellite() {
        return new XYTileSource("AmapSatellite", 3, 19, 256, ".png",
                new String[]{"https://webst01.is.autonavi.com/appmaptile"}) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return "https://webst01.is.autonavi.com/appmaptile?style=6"
                        + "&x=" + MapTileIndex.getX(pMapTileIndex)
                        + "&y=" + MapTileIndex.getY(pMapTileIndex)
                        + "&z=" + MapTileIndex.getZoom(pMapTileIndex);
            }
        };
    }

    // ── Sector data model for zoom-adaptive re-rendering ──────────────────────

    private static class SectorEntry {
        final double lat, lon;
        final int    azimuth;
        final boolean isServing, isNr;
        final String title, snippet;

        SectorEntry(double lat, double lon, int azimuth,
                    boolean isServing, boolean isNr,
                    String title, String snippet) {
            this.lat = lat;  this.lon = lon;  this.azimuth = azimuth;
            this.isServing = isServing;  this.isNr = isNr;
            this.title = title;  this.snippet = snippet;
        }
    }

    // ── Playback data record ───────────────────────────────────────────────────

    private static class PlaybackEntry {
        final long   timestamp;
        final double latitude, longitude;
        final String rat, simLabel, operatorName;
        final int    rsrp, rsrq, sinr, pci;

        PlaybackEntry(long ts, double lat, double lon,
                      String rat, int rsrp, int rsrq, int sinr, int pci,
                      String simLabel, String operatorName) {
            timestamp = ts; latitude = lat; longitude = lon;
            this.rat = rat; this.rsrp = rsrp; this.rsrq = rsrq;
            this.sinr = sinr; this.pci = pci;
            this.simLabel = simLabel; this.operatorName = operatorName;
        }
    }

    private static class TrackPoint {
        final GeoPoint  point;
        final long      timestamp;
        final int rsrp, rsrq, sinr;
        final int pci, ci, tac, earfcn, ta;
        final float altitude, speed;
        final List<CellSignalManager.NeighborCell> neighbors;

        TrackPoint(DriveTestRecord r) {
            this.point     = new GeoPoint(r.latitude, r.longitude);
            this.timestamp = r.timestamp;
            this.rsrp      = r.rsrp;
            this.rsrq      = r.rsrq;
            this.sinr      = r.sinr;
            this.pci       = r.pci;
            this.ci        = r.ci;
            this.tac       = r.tac;
            this.earfcn    = r.earfcn;
            this.ta        = r.ta;
            this.altitude  = r.altitude;
            this.speed     = r.speed;
            this.neighbors = r.neighbors;
        }

        int estimatedDistM() {
            return (ta != Integer.MAX_VALUE && ta >= 0) ? (int)(ta * 78.12) : -1;
        }
    }

    private static class HandoverEvent {
        final long     timestamp;
        final GeoPoint point;
        final int fromPci, toPci;
        final int rsrp, sinr;
        final int distM;

        HandoverEvent(long ts, GeoPoint pt, int fromPci, int toPci, int rsrp, int sinr, int distM) {
            this.timestamp = ts; this.point = pt;
            this.fromPci = fromPci; this.toPci = toPci;
            this.rsrp = rsrp; this.sinr = sinr; this.distM = distM;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private MapView mMapView;
    private MyLocationNewOverlay mLocationOverlay;
    private ScaleBarOverlay      mScaleBarOverlay;
    private final List<TrackPoint> mTrackPoints   = new ArrayList<>();
    private final List<Polyline>   mTrackSegments = new ArrayList<>();
    private int                    mTrackMetric   = METRIC_RSRP;
    private TextView               mTvMetricSelect;

    // Cell overlays – mSectorData replaced by mAllSectorCache for persistence
    private final Map<String, SectorEntry> mAllSectorCache = new LinkedHashMap<>();
    private final List<Polygon>            mSectorOverlays  = new ArrayList<>();
    private final List<Polyline>           mLineOverlays    = new ArrayList<>();
    private final List<Marker>             mMarkerOverlays  = new ArrayList<>();

    private double   mCurrentZoom        = 15.0;
    private GeoPoint mLastLoadCenter     = null;
    private GeoPoint mLastMapLoadCenter  = null;

    private DriveTestManager mManager;
    private boolean  mMeasureMode   = false;
    private GeoPoint mMeasurePoint1 = null;
    private Polyline mMeasureLine   = null;

    // Live signal display refresh (independent of GPS, fires every 500 ms during recording)
    private final Handler  mSignalHandler  = new Handler(Looper.getMainLooper());
    private final Runnable mSignalRunnable = new Runnable() {
        @Override public void run() {
            if (mManager != null && mManager.isRecording()) {
                refreshLiveSignal();
                mSignalHandler.postDelayed(this, 500);
            }
        }
    };

    // Scroll debounce
    private final Handler  mScrollHandler         = new Handler(Looper.getMainLooper());
    private final Runnable mScrollDebounceRunnable = () -> {
        reRenderSectorPolygons();
        checkAutoLoad();
    };

    // ── Playback state ────────────────────────────────────────────────────────
    private final List<PlaybackEntry> mPlaybackRecords = new ArrayList<>();
    private int      mPlaybackIndex          = 0;
    private boolean  mIsPlaying              = false;
    private int      mPlaybackSpeedMult      = 1;
    private static final long PLAYBACK_STEP_MS = 150;
    private final Handler  mPlaybackHandler = new Handler(Looper.getMainLooper());
    private Marker   mPlaybackMarker = null;
    private Polyline mPlaybackPath   = null;
    private final List<Polyline> mPlaybackSegments = new ArrayList<>();

    private final Runnable mPlaybackRunnable = new Runnable() {
        @Override public void run() {
            if (!mIsPlaying) return;
            if (mPlaybackIndex < mPlaybackRecords.size() - 1) {
                advancePlaybackTo(mPlaybackIndex + 1);
                mPlaybackHandler.postDelayed(this, PLAYBACK_STEP_MS / mPlaybackSpeedMult);
            } else {
                mIsPlaying = false;
                if (mBtnPlaybackPlayPause != null) mBtnPlaybackPlayPause.setText("▶ 播放");
                toast("回放完成");
            }
        }
    };

    // ── UI refs ───────────────────────────────────────────────────────────────
    private TextView         mTvStatus;
    private TextView         mTvCount;
    private TextView         mTvMeasureResult;
    private MaterialCardView mCardCellInfo;
    private TextView         mTvCellInfo;

    // SIM selection  (-1 = all, 0 = SIM1, 1 = SIM2)
    private int      mTestSimIndex = 1;
    private TextView mTvSimSelect;

    // Compass FABs
    private Button  mFabMain;
    private Button  mFabStartStop;
    private Button  mFabMeasure;
    private Button  mFabRefresh;
    private Button  mFabLayer;
    private Button  mFabMore;
    private View    mCompassDisc;
    private boolean mIsCompassExpanded = false;
    private int     mCurrentLayerIndex = 0;

    // Playback UI
    private MaterialCardView mCardPlayback;
    private TextView         mTvPlaybackFile;
    private TextView         mTvPlaybackTime;
    private SeekBar          mSeekBarPlayback;
    private Button           mBtnPlaybackPlayPause;
    private Button           mBtnPlaybackSpeed;
    private Button           mBtnPlaybackClose;

    // Recording overlay: mini chart + live stats
    private MaterialCardView mCardMiniChart;
    private SignalChartView  mMiniChart;
    private TextView         mTvSpeed;
    private TextView         mTvLiveSignal;
    private TextView         mTvStats;

    // Session stats (reset on start recording)
    private int  mStatsRsrpMin   = Integer.MAX_VALUE;
    private int  mStatsRsrpMax   = Integer.MIN_VALUE;
    private long mStatsRsrpSum   = 0;
    private int  mStatsRsrpCount = 0;

    private int  mStatsSinrMin   = Integer.MAX_VALUE;
    private int  mStatsSinrMax   = Integer.MIN_VALUE;
    private long mStatsSinrSum   = 0;
    private int  mStatsSinrCount = 0;

    private int  mStatsRsrqMin   = Integer.MAX_VALUE;
    private int  mStatsRsrqMax   = Integer.MIN_VALUE;
    private long mStatsRsrqSum   = 0;
    private int  mStatsRsrqCount = 0;

    private int  mStatsTaMin   = Integer.MAX_VALUE;
    private int  mStatsTaMax   = 0;
    private long mStatsTaSum   = 0;
    private int  mStatsTaCount = 0;

    // Handover detection
    private int  mLastPci = Integer.MAX_VALUE;
    private final List<Marker>        mHandoverMarkers = new ArrayList<>();
    private final List<HandoverEvent> mHandoverEvents  = new ArrayList<>();

    // WiFi layer (drive test indoor coverage)
    private final List<Marker> mWifiMarkers = new ArrayList<>();
    private boolean mShowWifiLayer = false;

    // Signal overlay (always-visible top-right card)
    private MaterialCardView mCardSignalOverlay;
    private TextView         mTvOverlayRsrp;
    private TextView         mTvOverlaySinr;
    private TextView         mTvOverlayRsrq;

    // Serving cell line (GPS → tower) drawn during recording and playback
    private Polyline mServingCellLine;

    // Playback signal display
    private TextView mTvPlaybackSignal;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        View root = inflater.inflate(R.layout.fragment_drive_test, container, false);

        mMapView         = root.findViewById(R.id.map_view);
        mTvStatus        = root.findViewById(R.id.tv_gps_status);
        mTvCount         = root.findViewById(R.id.tv_record_count);
        mTvSimSelect     = root.findViewById(R.id.tv_sim_select);
        mTvMetricSelect  = root.findViewById(R.id.tv_metric_select);
        mTvMeasureResult = root.findViewById(R.id.tv_measure_result);
        mCardCellInfo    = root.findViewById(R.id.card_cell_info);
        mTvCellInfo      = root.findViewById(R.id.tv_cell_info);

        // Compass UI
        mFabMain      = root.findViewById(R.id.fab_main);
        mFabStartStop = root.findViewById(R.id.fab_start_stop);
        mFabMeasure   = root.findViewById(R.id.fab_measure);
        mFabRefresh   = root.findViewById(R.id.fab_refresh);
        mFabLayer     = root.findViewById(R.id.fab_layer);
        mFabMore      = root.findViewById(R.id.fab_more);
        mCompassDisc  = root.findViewById(R.id.compass_disc);

        // Playback UI
        mCardPlayback         = root.findViewById(R.id.card_playback);
        mTvPlaybackFile       = root.findViewById(R.id.tv_playback_file);
        mTvPlaybackTime       = root.findViewById(R.id.tv_playback_time);
        mTvPlaybackSignal     = root.findViewById(R.id.tv_playback_signal);
        mSeekBarPlayback      = root.findViewById(R.id.seekbar_playback);
        mBtnPlaybackPlayPause = root.findViewById(R.id.btn_playback_play_pause);
        mBtnPlaybackSpeed     = root.findViewById(R.id.btn_playback_speed);
        mBtnPlaybackClose     = root.findViewById(R.id.btn_playback_close);

        // Recording overlay
        mCardMiniChart = root.findViewById(R.id.card_mini_chart);
        mMiniChart     = root.findViewById(R.id.mini_chart);
        mTvSpeed       = root.findViewById(R.id.tv_speed);
        mTvLiveSignal  = root.findViewById(R.id.tv_live_signal);
        mTvStats       = root.findViewById(R.id.tv_stats);

        // Signal overlay
        mCardSignalOverlay = root.findViewById(R.id.card_signal_overlay);
        mTvOverlayRsrp     = root.findViewById(R.id.tv_overlay_rsrp);
        mTvOverlaySinr     = root.findViewById(R.id.tv_overlay_sinr);
        mTvOverlayRsrq     = root.findViewById(R.id.tv_overlay_rsrq);

        setupMap();

        mManager = new DriveTestManager(requireContext());
        mManager.setStateListener(this);

        mTvSimSelect.setOnClickListener(v -> {
            mTestSimIndex++;
            if (mTestSimIndex > 1) mTestSimIndex = -1;
            mManager.setTestSimIndex(mTestSimIndex);
            updateSimLabel();
            refreshCellOverlays();
        });

        mTvMetricSelect.setOnClickListener(v -> {
            mTrackMetric = (mTrackMetric + 1) % METRIC_NAMES.length;
            updateMetricLabel();
            redrawTrack();
            if (!mPlaybackRecords.isEmpty()) redrawPlaybackPath();
        });

        mFabMain.setOnClickListener(v -> toggleCompass());

        mFabStartStop.setOnClickListener(v -> {
            collapseCompassItems();
            mIsCompassExpanded = false;
            toggleRecording();
        });
        mFabMeasure.setOnClickListener(v -> {
            collapseCompassItems();
            mIsCompassExpanded = false;
            toggleMeasureMode();
        });
        mFabRefresh.setOnClickListener(v -> {
            collapseCompassItems();
            mIsCompassExpanded = false;
            refreshCellOverlays();
        });
        mFabLayer.setOnClickListener(v -> {
            collapseCompassItems();
            mIsCompassExpanded = false;
            cycleLayer();
        });
        mFabMore.setOnClickListener(v -> {
            collapseCompassItems();
            mIsCompassExpanded = false;
            PopupMenu pm = new PopupMenu(requireContext(), v);
            pm.getMenu().add(0, 1, 0, "导出 CSV");
            pm.getMenu().add(0, 2, 0, "导出 KML");
            pm.getMenu().add(0, 3, 0, "上报服务器");
            pm.getMenu().add(0, 4, 0, "清除记录");
            pm.getMenu().add(0, 5, 0, "数据回放");
            pm.getMenu().add(0, 6, 0, mShowWifiLayer ? "隐藏 WiFi 层" : "显示 WiFi 层");
            pm.getMenu().add(0, 7, 0, "AI 智能分析");
            pm.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: exportCsv();             return true;
                    case 2: exportKml();             return true;
                    case 3: uploadRecords();          return true;
                    case 4: clearRecords();           return true;
                    case 5: showPlaybackFilePicker(); return true;
                    case 6: toggleWifiLayer();        return true;
                    case 7: startDriveTestAiAnalysis(); return true;
                }
                return false;
            });
            pm.show();
        });

        root.findViewById(R.id.btn_zoom_in).setOnClickListener(v -> mMapView.getController().zoomIn());
        root.findViewById(R.id.btn_zoom_out).setOnClickListener(v -> mMapView.getController().zoomOut());
        root.findViewById(R.id.btn_locate).setOnClickListener(v -> locateMe());

        mBtnPlaybackPlayPause.setOnClickListener(v -> togglePlayback());
        mBtnPlaybackSpeed.setOnClickListener(v -> cyclePlaybackSpeed());
        root.findViewById(R.id.btn_playback_prev).setOnClickListener(v -> resetPlayback());
        mBtnPlaybackClose.setOnClickListener(v -> stopPlayback());

        mSeekBarPlayback.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) advancePlaybackTo(p);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
        if (mLocationOverlay != null) mLocationOverlay.enableMyLocation();
        // Auto-refresh cache after login
        if (SettingUtils.isLoggedIn(requireContext())
                && SettingUtils.getNeedCacheRefresh(requireContext())) {
            SettingUtils.setNeedCacheRefresh(requireContext(), false);
            new Handler(Looper.getMainLooper()).postDelayed(this::refreshCellOverlays, 2000);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mLocationOverlay != null) mLocationOverlay.disableMyLocation();
        if (mMapView != null) mMapView.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        for (Polyline seg : mTrackSegments) mMapView.getOverlays().remove(seg);
        for (Polyline seg : mPlaybackSegments) mMapView.getOverlays().remove(seg);
        mPlaybackHandler.removeCallbacksAndMessages(null);
        mScrollHandler.removeCallbacksAndMessages(null);
        mSignalHandler.removeCallbacksAndMessages(null);
        if (mManager.isRecording()) mManager.stopRecording();
        if (mMapView != null) mMapView.onDetach();
    }

    // ── Map setup ─────────────────────────────────────────────────────────────

    private void setupMap() {
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setMultiTouchControls(true);
        mMapView.setBuiltInZoomControls(false);

        IMapController ctrl = mMapView.getController();
        ctrl.setZoom(15.0);

        mLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(requireContext()), mMapView);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();
        mLocationOverlay.runOnFirstFix(() -> requireActivity().runOnUiThread(() -> {
            GeoPoint loc = mLocationOverlay.getMyLocation();
            if (loc != null) mMapView.getController().animateTo(loc);
        }));
        mMapView.getOverlays().add(mLocationOverlay);

        mScaleBarOverlay = new ScaleBarOverlay(mMapView);
        mScaleBarOverlay.setCentred(true);
        mScaleBarOverlay.setScaleBarOffset(
                getResources().getDisplayMetrics().widthPixels / 2, 10);
        mMapView.getOverlays().add(mScaleBarOverlay);

        CompassOverlay compass = new CompassOverlay(requireContext(), mMapView);
        compass.enableCompass();
        mMapView.getOverlays().add(compass);

        RotationGestureOverlay rotation = new RotationGestureOverlay(mMapView);
        rotation.setEnabled(true);
        mMapView.getOverlays().add(rotation);

        // Zoom-adaptive + viewport-filtered sector re-rendering
        mMapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                mScrollHandler.removeCallbacks(mScrollDebounceRunnable);
                mScrollHandler.postDelayed(mScrollDebounceRunnable, 500);
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                double newZoom = event.getZoomLevel();
                if (Math.abs(newZoom - mCurrentZoom) >= 0.5) {
                    mCurrentZoom = newZoom;
                    reRenderSectorPolygons();
                }
                return false;
            }
        });

        mMapView.setOnTouchListener((v, event) -> {
            if (mMeasureMode && event.getAction() == MotionEvent.ACTION_DOWN) {
                handleMeasureTap(event.getX(), event.getY());
            }
            return false;
        });
    }

    // ── Zoom-adaptive sectors ─────────────────────────────────────────────────

    /** Sector radius in metres based on current zoom (300m at zoom 15, capped 50–5000m). */
    private double getSectorRadiusM() {
        double r = 300.0 * Math.pow(2.0, 15.0 - mCurrentZoom);
        return Math.max(50.0, Math.min(5000.0, r));
    }

    private void reRenderSectorPolygons() {
        for (Polygon p : mSectorOverlays) mMapView.getOverlays().remove(p);
        mSectorOverlays.clear();
        double radiusM = getSectorRadiusM();
        org.osmdroid.util.BoundingBox bbox = mMapView.getBoundingBox();
        for (SectorEntry e : mAllSectorCache.values()) {
            // Only render sectors whose site is within the current viewport
            if (bbox != null && !bbox.contains(new GeoPoint(e.lat, e.lon))) continue;
            double r = e.isNr ? radiusM * 0.7 : radiusM;
            Polygon sector = e.isServing
                    ? SectorDrawer.servingSector(e.lat, e.lon, e.azimuth, r)
                    : SectorDrawer.areaSector(e.lat, e.lon, e.azimuth, r);
            sector.setTitle(e.title);
            sector.setSnippet(e.snippet);
            sector.setOnClickListener((polygon, mapView, eventPos) -> {
                mTvCellInfo.setText(polygon.getTitle() + "\n" + polygon.getSnippet());
                mCardCellInfo.setVisibility(View.VISIBLE);
                return true;
            });
            mSectorOverlays.add(sector);
            mMapView.getOverlays().add(sector);
        }
        mMapView.invalidate();
    }

    private void checkAutoLoad() {
        // GPS movement → reload signal-based cells
        if (mLocationOverlay != null) {
            GeoPoint gps = mLocationOverlay.getMyLocation();
            if (gps != null) {
                if (mLastLoadCenter == null) { mLastLoadCenter = gps; }
                else {
                    double d = SectorDrawer.distanceMetres(
                            mLastLoadCenter.getLatitude(), mLastLoadCenter.getLongitude(),
                            gps.getLatitude(), gps.getLongitude());
                    if (d > 2000) {
                        mLastLoadCenter = gps;
                        mLastMapLoadCenter = null;
                        refreshCellOverlays();
                        return;
                    }
                }
            }
        }
        // Map pan → load cells visible in the new viewport (additive, no clear)
        org.osmdroid.api.IGeoPoint c = mMapView.getMapCenter();
        GeoPoint mc = new GeoPoint(c.getLatitude(), c.getLongitude());
        if (mLastMapLoadCenter == null) { mLastMapLoadCenter = mc; return; }
        double d = SectorDrawer.distanceMetres(
                mLastMapLoadCenter.getLatitude(), mLastMapLoadCenter.getLongitude(),
                mc.getLatitude(), mc.getLongitude());
        if (d > 1500) {
            mLastMapLoadCenter = mc;
            loadCellsForViewport();
        }
    }

    private void loadCellsForViewport() {
        org.osmdroid.util.BoundingBox bbox = mMapView.getBoundingBox();
        if (bbox == null) return;
        double latPad = (bbox.getLatNorth() - bbox.getLatSouth()) * 0.3;
        double lonPad = (bbox.getLonEast() - bbox.getLonWest()) * 0.3;
        double minLat = bbox.getLatSouth() - latPad;
        double maxLat = bbox.getLatNorth() + latPad;
        double minLon = bbox.getLonWest() - lonPad;
        double maxLon = bbox.getLonEast() + lonPad;
        GeoPoint gps = mLocationOverlay != null ? mLocationOverlay.getMyLocation() : null;

        CellSentinelApi.getLteCellsByBounds(requireContext(), minLat, maxLat, minLon, maxLon, 200,
                new CellSentinelApi.CellListCallback<LteCellInfo>() {
                    @Override public void onSuccess(List<LteCellInfo> cells) {
                        if (cells.isEmpty()) return;
                        for (LteCellInfo cell : cells) addLteSector(cell, false, gps);
                        reRenderSectorPolygons();
                    }
                    @Override public void onError(String msg) {}
                });
        CellSentinelApi.getNrCellsByBounds(requireContext(), minLat, maxLat, minLon, maxLon, 100,
                new CellSentinelApi.CellListCallback<NrCellInfo>() {
                    @Override public void onSuccess(List<NrCellInfo> cells) {
                        if (cells.isEmpty()) return;
                        for (NrCellInfo cell : cells) addNrSector(cell, false, gps);
                        reRenderSectorPolygons();
                    }
                    @Override public void onError(String msg) {}
                });
    }

    private void cycleLayer() {
        mCurrentLayerIndex = (mCurrentLayerIndex + 1) % LAYER_NAMES.length;
        switchLayer(mCurrentLayerIndex);
        String[] shortNames = {"OSM", "ESRI", "谷歌", "高德"};
        mFabLayer.setText("图层\n" + shortNames[mCurrentLayerIndex]);
        toast(LAYER_NAMES[mCurrentLayerIndex]);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void switchLayer(int index) {
        switch (index) {
            case 0: mMapView.setTileSource(TileSourceFactory.MAPNIK); break;
            case 1: mMapView.setTileSource(esriSatellite()); break;
            case 2: mMapView.setTileSource(googleSatellite()); break;
            case 3: mMapView.setTileSource(amapSatellite()); break;
        }
        mMapView.invalidate();
    }

    private void locateMe() {
        GeoPoint loc = mLocationOverlay != null ? mLocationOverlay.getMyLocation() : null;
        if (loc != null) {
            mMapView.getController().animateTo(loc);
        } else {
            toast("等待 GPS 定位…");
        }
    }

    // ── Measure mode ──────────────────────────────────────────────────────────

    private void toggleMeasureMode() {
        mMeasureMode = !mMeasureMode;
        mMeasurePoint1 = null;
        mTvMeasureResult.setVisibility(View.GONE);
        requireView().findViewById(R.id.card_measure).setVisibility(View.GONE);
        if (!mMeasureMode && mMeasureLine != null) {
            mMapView.getOverlays().remove(mMeasureLine);
            mMeasureLine = null;
            mMapView.invalidate();
        }
        if (mMeasureMode) toast("在地图上点击两个点以测距");
    }

    private void handleMeasureTap(float screenX, float screenY) {
        GeoPoint tapped = (GeoPoint) mMapView.getProjection()
                .fromPixels((int) screenX, (int) screenY);
        if (mMeasurePoint1 == null) {
            mMeasurePoint1 = tapped;
            toast("已标记起点，请点击终点");
        } else {
            double dist = SectorDrawer.distanceMetres(
                    mMeasurePoint1.getLatitude(), mMeasurePoint1.getLongitude(),
                    tapped.getLatitude(), tapped.getLongitude());

            if (mMeasureLine != null) mMapView.getOverlays().remove(mMeasureLine);
            mMeasureLine = new Polyline(mMapView);
            mMeasureLine.getOutlinePaint().setColor(Color.argb(220, 255, 200, 0));
            mMeasureLine.getOutlinePaint().setStrokeWidth(4f);
            mMeasureLine.getOutlinePaint().setStyle(Paint.Style.STROKE);
            List<GeoPoint> pts = new ArrayList<>();
            pts.add(mMeasurePoint1);
            pts.add(tapped);
            mMeasureLine.setPoints(pts);
            mMapView.getOverlays().add(mMeasureLine);
            mMapView.invalidate();

            mTvMeasureResult.setText("测距结果: " + SectorDrawer.formatDistance(dist));
            mTvMeasureResult.setVisibility(View.VISIBLE);
            requireView().findViewById(R.id.card_measure).setVisibility(View.VISIBLE);
            mMeasurePoint1 = null;
        }
    }

    // ── Cell sector overlays ──────────────────────────────────────────────────

    private static final double MAX_CELL_DIST_M = 30_000;

    /** Refresh cell overlays; works offline using cached data when backend is unavailable. */
    public void refreshCellOverlays() {
        if (MainActivity.signalManager == null) {
            toast("信号管理器未就绪");
            return;
        }
        if (!SettingUtils.isLoggedIn(requireContext())) {
            toast("离线模式，尝试使用缓存数据");
        }
        clearCellOverlays();
        mCurrentZoom = mMapView.getZoomLevelDouble();
        GeoPoint gps = mLocationOverlay != null ? mLocationOverlay.getMyLocation() : null;
        if (gps != null) mLastLoadCenter = gps;
        List<CellSignalManager.SimSignalData> sims = MainActivity.signalManager.getSimDataList();
        for (int i = 0; i < sims.size(); i++) {
            if (mTestSimIndex >= 0 && i != mTestSimIndex) continue;
            loadLteOverlay(sims.get(i));
            loadNrOverlay(sims.get(i));
        }
    }

    /**
     * Load LTE overlay: CI exact match first (CI = eNodeBId*256 + cellIndex).
     * CI match → draw sector + GPS connection line.
     * No CI / CI not found → PCI fallback → draw sector + "缺失基础信息" marker.
     */
    private void loadLteOverlay(CellSignalManager.SimSignalData sim) {
        if (sim.lte_PCI == Integer.MAX_VALUE) return;
        GeoPoint gpsPoint = mLocationOverlay.getMyLocation();

        if (sim.lte_CI != Integer.MAX_VALUE && sim.lte_CI > 0) {
            long enodebId = (sim.lte_CI >> 8) & 0xFFFFFL;
            int  cellIdx  = sim.lte_CI & 0xFF;
            CellSentinelApi.getLteCellsByCi(requireContext(), enodebId, cellIdx, 5,
                    new CellSentinelApi.CellListCallback<LteCellInfo>() {
                        @Override
                        public void onSuccess(List<LteCellInfo> cells) {
                            if (!cells.isEmpty()) {
                                LteCellInfo serving = cells.get(0);
                                addLteSector(serving, true, gpsPoint);
                                drawServingConnection(serving, gpsPoint);  // CI confirmed
                                loadAreaCells(sim, serving, gpsPoint);
                                loadNeighborCellsFromSignal(sim, serving, gpsPoint);
                            } else {
                                loadLteByPci(sim, gpsPoint);  // CI not in DB
                            }
                            mMapView.invalidate();
                        }
                        @Override
                        public void onError(String msg) {
                            loadLteByPci(sim, gpsPoint);
                        }
                    });
        } else {
            loadLteByPci(sim, gpsPoint);
        }
    }

    /**
     * PCI fallback — no CI confirmation, so no connection line.
     * Shows "缺失基础信息" warning marker at GPS position.
     */
    private void loadLteByPci(CellSignalManager.SimSignalData sim, GeoPoint gpsPoint) {
        CellSentinelApi.getLteCellsByPci(requireContext(), sim.lte_PCI, 20,
                new CellSentinelApi.CellListCallback<LteCellInfo>() {
                    @Override
                    public void onSuccess(List<LteCellInfo> cells) {
                        LteCellInfo serving = null;
                        for (LteCellInfo cell : cells) {
                            if (sim.lte_TAC != Integer.MAX_VALUE && cell.tac == sim.lte_TAC) {
                                serving = cell;
                                break;
                            }
                            if (serving == null) serving = cell;
                        }
                        if (serving != null) {
                            addLteSector(serving, true, gpsPoint);
                            loadAreaCells(sim, serving, gpsPoint);
                            loadNeighborCellsFromSignal(sim, serving, gpsPoint);
                        }
                        mMapView.invalidate();
                    }
                    @Override
                    public void onError(String msg) {
                        mMapView.invalidate();
                    }
                });
    }

    /** GPS→serving-cell connection line. Only drawn for CI-confirmed matches. */
    private void drawServingConnection(LteCellInfo serving, GeoPoint gpsPoint) {
        if (gpsPoint == null || !serving.hasValidCoords()) return;
        Polyline l = SectorDrawer.servingLine(
                gpsPoint.getLatitude(), gpsPoint.getLongitude(),
                serving.cellLat, serving.cellLon);
        mLineOverlays.add(l);
        mMapView.getOverlays().add(l);
    }

    /** Load TAC-area cells, filtered to MAX_CELL_DIST_M around the serving cell. */
    private void loadAreaCells(CellSignalManager.SimSignalData sim,
                                LteCellInfo serving, GeoPoint gpsPoint) {
        if (sim.lte_TAC == Integer.MAX_VALUE) return;
        CellSentinelApi.getLteCellsByTac(requireContext(), sim.lte_TAC, 300,
                new CellSentinelApi.CellListCallback<LteCellInfo>() {
                    @Override
                    public void onSuccess(List<LteCellInfo> cells) {
                        for (LteCellInfo cell : cells) {
                            if (cell.pci == sim.lte_PCI) continue;
                            if (serving.hasValidCoords() && cell.hasValidCoords()) {
                                double dist = SectorDrawer.distanceMetres(
                                        serving.cellLat, serving.cellLon,
                                        cell.cellLat, cell.cellLon);
                                if (dist > MAX_CELL_DIST_M) continue;
                            }
                            addLteSector(cell, false, gpsPoint);
                        }
                        mMapView.invalidate();
                    }
                    @Override public void onError(String msg) {}
                });
    }

    /** Load neighbor cells from signal report; draw sectors only (no lines). */
    private void loadNeighborCellsFromSignal(CellSignalManager.SimSignalData sim,
                                              LteCellInfo serving, GeoPoint gpsPoint) {
        for (String nbrStr : sim.neighborCells) {
            int nbrPci = parsePciFromNeighborString(nbrStr);
            if (nbrPci < 0) continue;

            CellSentinelApi.getLteCellsByPci(requireContext(), nbrPci, 20,
                    new CellSentinelApi.CellListCallback<LteCellInfo>() {
                        @Override
                        public void onSuccess(List<LteCellInfo> nbrCells) {
                            for (LteCellInfo nbr : nbrCells) {
                                if (!nbr.hasValidCoords()) continue;
                                if (serving.hasValidCoords()) {
                                    double d = SectorDrawer.distanceMetres(
                                            serving.cellLat, serving.cellLon,
                                            nbr.cellLat, nbr.cellLon);
                                    if (d > MAX_CELL_DIST_M) continue;
                                }
                                addLteSector(nbr, false, gpsPoint);
                            }
                            mMapView.invalidate();
                        }
                        @Override public void onError(String msg) {}
                    });
        }
    }

    private void loadNrOverlay(CellSignalManager.SimSignalData sim) {
        if (sim.nr_PCI == Integer.MAX_VALUE) return;
        GeoPoint gpsPoint = mLocationOverlay.getMyLocation();
        CellSentinelApi.getNrCellsByPci(requireContext(), sim.nr_PCI, 10,
                new CellSentinelApi.CellListCallback<NrCellInfo>() {
                    @Override
                    public void onSuccess(List<NrCellInfo> cells) {
                        for (NrCellInfo cell : cells) addNrSector(cell, true, gpsPoint);
                        mMapView.invalidate();
                    }
                    @Override public void onError(String msg) {}
                });
    }

    private void addLteSector(LteCellInfo cell, boolean isServing, GeoPoint gpsPoint) {
        if (!cell.hasValidCoords()) return;
        double radiusM = getSectorRadiusM();
        String title   = cell.cellName;
        String snippet = String.format(Locale.US,
                "PCI:%d  TAC:%d  方位角:%d°\n%s  %s",
                cell.pci, cell.tac, cell.azimuth, cell.frequencyBand, cell.operator);
        SectorEntry entry = new SectorEntry(cell.cellLat, cell.cellLon, cell.azimuth,
                isServing, false, title, snippet);
        String cacheKey = "lte_" + cell.enodebId + "_" + cell.cellId + "_" + cell.azimuth;
        mAllSectorCache.put(cacheKey, entry);

        Polygon sector = isServing
                ? SectorDrawer.servingSector(cell.cellLat, cell.cellLon, cell.azimuth, radiusM)
                : SectorDrawer.areaSector(cell.cellLat, cell.cellLon, cell.azimuth, radiusM);
        sector.setTitle(title);
        sector.setSnippet(snippet);
        sector.setOnClickListener((polygon, mapView, eventPos) -> {
            mTvCellInfo.setText(polygon.getTitle() + "\n" + polygon.getSnippet());
            mCardCellInfo.setVisibility(View.VISIBLE);
            return true;
        });
        mSectorOverlays.add(sector);
        mMapView.getOverlays().add(sector);

        if (isServing) {
            showServingCellInfo(cell);
        }
    }

    private void addNrSector(NrCellInfo cell, boolean isServing, GeoPoint gpsPoint) {
        if (!cell.hasValidCoords()) return;
        double radiusM = getSectorRadiusM() * 0.7;
        String title   = cell.cellName;
        String snippet = String.format(Locale.US,
                "NR PCI:%d  TAC:%d  方位角:%d°\n%s  %s",
                cell.physicalCellId, cell.tac, cell.azimuth, cell.frequencyBand, cell.operator);
        SectorEntry entry = new SectorEntry(cell.cellLat, cell.cellLon, cell.azimuth,
                isServing, true, title, snippet);
        String cacheKey = "nr_" + cell.gnodebId + "_" + cell.cellId + "_" + cell.azimuth;
        mAllSectorCache.put(cacheKey, entry);

        Polygon sector = isServing
                ? SectorDrawer.servingSector(cell.cellLat, cell.cellLon, cell.azimuth, radiusM)
                : SectorDrawer.neighborSector(cell.cellLat, cell.cellLon, cell.azimuth, radiusM);
        sector.setTitle(title);
        sector.setSnippet(snippet);
        sector.setOnClickListener((polygon, mapView, eventPos) -> {
            mTvCellInfo.setText(polygon.getTitle() + "\n" + polygon.getSnippet());
            mCardCellInfo.setVisibility(View.VISIBLE);
            return true;
        });
        mSectorOverlays.add(sector);
        mMapView.getOverlays().add(sector);

        if (isServing) {
            showServingCellInfo(cell);
        }
    }

    private void showServingCellInfo(LteCellInfo cell) {
        if (mCardCellInfo == null) return;
        StringBuilder sb = new StringBuilder();
        if (!cell.operator.isEmpty())     sb.append(cell.operator).append("  ");
        if (!cell.frequencyBand.isEmpty()) sb.append(cell.frequencyBand);
        sb.append("\n");
        if (!cell.enodebName.isEmpty())   sb.append("基站: ").append(cell.enodebName).append("\n");
        if (!cell.cellName.isEmpty())     sb.append("小区: ").append(cell.cellName).append("\n");
        sb.append("eNB:").append(cell.enodebId)
          .append("  CID:").append(cell.cellId)
          .append("  PCI:").append(cell.pci).append("\n");
        if (cell.downlinkCenterFrequency > 0)
            sb.append("频率:").append(cell.downlinkCenterFrequency).append("MHz");
        if (cell.bandwidthMhz > 0) {
            if (cell.downlinkCenterFrequency > 0) sb.append("  ");
            sb.append("带宽:").append(cell.bandwidthMhz).append("MHz");
        }
        if (cell.downlinkCenterFrequency > 0 || cell.bandwidthMhz > 0) sb.append("\n");
        sb.append("方位角:").append(cell.azimuth).append("°");
        if (cell.antennaHeight > 0)
            sb.append("  塔高:").append(cell.antennaHeight).append("m");
        mTvCellInfo.setText(sb.toString().trim());
        mCardCellInfo.setVisibility(View.VISIBLE);
    }

    private void showServingCellInfo(NrCellInfo cell) {
        if (mCardCellInfo == null) return;
        StringBuilder sb = new StringBuilder();
        if (!cell.operator.isEmpty())      sb.append(cell.operator).append("  ");
        if (!cell.frequencyBand.isEmpty()) sb.append(cell.frequencyBand).append(" NR");
        sb.append("\n");
        if (!cell.gnodebName.isEmpty())    sb.append("基站: ").append(cell.gnodebName).append("\n");
        if (!cell.cellName.isEmpty())      sb.append("小区: ").append(cell.cellName).append("\n");
        sb.append("gNB:").append(cell.gnodebId)
          .append("  CID:").append(cell.cellId)
          .append("  PCI:").append(cell.physicalCellId).append("\n");
        if (cell.downlinkFrequency > 0)
            sb.append("频率:").append(cell.downlinkFrequency).append("MHz");
        if (cell.bandwidthMhz > 0) {
            if (cell.downlinkFrequency > 0) sb.append("  ");
            sb.append("带宽:").append(cell.bandwidthMhz).append("MHz");
        }
        if (cell.downlinkFrequency > 0 || cell.bandwidthMhz > 0) sb.append("\n");
        sb.append("方位角:").append(cell.azimuth).append("°");
        if (cell.antennaHeight > 0)
            sb.append("  塔高:").append(cell.antennaHeight).append("m");
        mTvCellInfo.setText(sb.toString().trim());
        mCardCellInfo.setVisibility(View.VISIBLE);
    }

    private void clearCellOverlays() {
        for (Polygon p : mSectorOverlays) mMapView.getOverlays().remove(p);
        for (Polyline l : mLineOverlays)  mMapView.getOverlays().remove(l);
        for (Marker m : mMarkerOverlays)  mMapView.getOverlays().remove(m);
        mSectorOverlays.clear();
        mLineOverlays.clear();
        mMarkerOverlays.clear();
        // mAllSectorCache is intentionally NOT cleared – it persists across refreshes
        // so that panning the map can show previously loaded cells immediately.
        if (mCardCellInfo != null) mCardCellInfo.setVisibility(View.GONE);
    }

    private static int parsePciFromNeighborString(String s) {
        try {
            int pciIdx = s.indexOf("PCI:");
            if (pciIdx < 0) return -1;
            int start = pciIdx + 4;
            int end   = start;
            while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
            String pciStr = s.substring(start, end).trim();
            return pciStr.isEmpty() ? -1 : Integer.parseInt(pciStr);
        } catch (Exception e) {
            return -1;
        }
    }

    // ── DriveTestManager.StateListener ────────────────────────────────────────

    @Override
    public void onLocationUpdate(Location location, DriveTestRecord newRecord) {
        GeoPoint gp = new GeoPoint(location.getLatitude(), location.getLongitude());
        TrackPoint tp = new TrackPoint(newRecord);
        addTrackPoint(tp);
        mMapView.getController().animateTo(gp);
        mMapView.invalidate();

        String gpsText = String.format(Locale.US,
                "GPS  %.5f, %.5f  ±%.0fm",
                location.getLatitude(), location.getLongitude(), location.getAccuracy());
        mTvStatus.setText(gpsText);
        mTvCount.setText("已录制 " + mManager.getRecordCount() + " 条");

        // GPS speed
        float speedKmh = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
        if (mTvSpeed != null)
            mTvSpeed.setText(String.format(Locale.US, "%.0f km/h", speedKmh));

        // Live signal display
        if (mTvLiveSignal != null && newRecord.rsrp != Integer.MAX_VALUE)
            mTvLiveSignal.setText(String.format(Locale.US,
                    "RSRP %d  SINR %s  RSRQ %s",
                    newRecord.rsrp,
                    newRecord.sinr == Integer.MAX_VALUE ? "—" : String.valueOf(newRecord.sinr),
                    newRecord.rsrq == Integer.MAX_VALUE ? "—" : String.valueOf(newRecord.rsrq)));

        // Session stats
        if (newRecord.rsrp != Integer.MAX_VALUE) {
            if (mStatsRsrpMin == Integer.MAX_VALUE || newRecord.rsrp < mStatsRsrpMin)
                mStatsRsrpMin = newRecord.rsrp;
            if (mStatsRsrpMax == Integer.MIN_VALUE || newRecord.rsrp > mStatsRsrpMax)
                mStatsRsrpMax = newRecord.rsrp;
            mStatsRsrpSum  += newRecord.rsrp;
            mStatsRsrpCount++;
            if (mTvStats != null && mStatsRsrpCount > 0) {
                int avg = (int)(mStatsRsrpSum / mStatsRsrpCount);
                mTvStats.setText(String.format(Locale.US,
                        "RSRP  min %d  avg %d  max %d dBm",
                        mStatsRsrpMin, avg, mStatsRsrpMax));
            }
        }
        if (newRecord.sinr != Integer.MAX_VALUE) {
            if (mStatsSinrMin == Integer.MAX_VALUE || newRecord.sinr < mStatsSinrMin)
                mStatsSinrMin = newRecord.sinr;
            if (mStatsSinrMax == Integer.MIN_VALUE || newRecord.sinr > mStatsSinrMax)
                mStatsSinrMax = newRecord.sinr;
            mStatsSinrSum += newRecord.sinr; mStatsSinrCount++;
        }
        if (newRecord.rsrq != Integer.MAX_VALUE) {
            if (mStatsRsrqMin == Integer.MAX_VALUE || newRecord.rsrq < mStatsRsrqMin)
                mStatsRsrqMin = newRecord.rsrq;
            if (mStatsRsrqMax == Integer.MIN_VALUE || newRecord.rsrq > mStatsRsrqMax)
                mStatsRsrqMax = newRecord.rsrq;
            mStatsRsrqSum += newRecord.rsrq; mStatsRsrqCount++;
        }
        if (newRecord.ta != Integer.MAX_VALUE && newRecord.ta >= 0) {
            if (mStatsTaMin == Integer.MAX_VALUE || newRecord.ta < mStatsTaMin) mStatsTaMin = newRecord.ta;
            if (newRecord.ta > mStatsTaMax) mStatsTaMax = newRecord.ta;
            mStatsTaSum += newRecord.ta; mStatsTaCount++;
        }

        // Handover detection
        if (newRecord.pci != Integer.MAX_VALUE
                && mLastPci != Integer.MAX_VALUE
                && newRecord.pci != mLastPci) {
            markHandover(gp, mLastPci, newRecord.pci);
            mHandoverEvents.add(new HandoverEvent(newRecord.timestamp, gp,
                    mLastPci, newRecord.pci, newRecord.rsrp, newRecord.sinr,
                    newRecord.estimatedDistM()));
        }
        if (newRecord.pci != Integer.MAX_VALUE) mLastPci = newRecord.pci;

        // Mini chart
        if (mMiniChart != null) mMiniChart.addSample(newRecord.rsrp, newRecord.sinr, newRecord.rsrq);

        // WiFi layer: scan at current position (throttled by WifiManager internal cooldown)
        if (mShowWifiLayer) scanAndPlotWifi(gp);

        // Serving cell connecting line (GPS → tower)
        updateServingCellLine(gp);
    }

    @Override
    public void onError(String message) {
        toast(message);
        if (mManager.isRecording()) {
            mManager.stopRecording();
            updateButtonState();
        }
    }

    // ── Recording controls ────────────────────────────────────────────────────

    private void toggleRecording() {
        if (mManager.isRecording()) {
            mManager.stopRecording();
            mSignalHandler.removeCallbacks(mSignalRunnable);
            toast("已停止，共 " + mManager.getRecordCount() + " 条");
        } else {
            // Reset session stats
            mStatsRsrpMin   = Integer.MAX_VALUE; mStatsRsrpMax = Integer.MIN_VALUE;
            mStatsRsrpSum   = 0;                 mStatsRsrpCount = 0;
            mStatsSinrMin   = Integer.MAX_VALUE; mStatsSinrMax = Integer.MIN_VALUE;
            mStatsSinrSum   = 0;                 mStatsSinrCount = 0;
            mStatsRsrqMin   = Integer.MAX_VALUE; mStatsRsrqMax = Integer.MIN_VALUE;
            mStatsRsrqSum   = 0;                 mStatsRsrqCount = 0;
            mStatsTaMin     = Integer.MAX_VALUE; mStatsTaMax = 0;
            mStatsTaSum     = 0;                 mStatsTaCount = 0;
            mLastPci        = Integer.MAX_VALUE;
            mHandoverEvents.clear();
            boolean ok = mManager.startRecording(DriveTestManager.generateSessionName());
            if (ok) {
                toast("开始路测");
                refreshCellOverlays();
                mSignalHandler.post(mSignalRunnable);
            }
        }
        updateButtonState();
    }

    private void exportCsv() {
        if (mManager.getRecordCount() == 0) { toast("暂无数据"); return; }
        File f = mManager.exportCsv();
        toast(f != null ? "CSV 已导出: " + f.getName() : "导出失败");
    }

    private void exportKml() {
        if (mManager.getRecordCount() == 0) { toast("暂无数据"); return; }
        File f = mManager.exportKml();
        toast(f != null ? "KML 已导出: " + f.getName() : "导出失败");
    }

    private void uploadRecords() {
        if (!SettingUtils.isLoggedIn(requireContext())) { toast("请先登录"); return; }
        if (mManager.getRecordCount() == 0) { toast("暂无路测数据"); return; }
        RuoyiApi.uploadDriveTest(requireContext(),
                new ArrayList<>(mManager.getRecords()), mManager.getSessionName(),
                new RuoyiApi.UploadCallback() {
                    @Override public void onSuccess(String msg) { toast(msg); }
                    @Override public void onError(String msg)   { toast("上报失败: " + msg); }
                });
    }

    private void clearRecords() {
        if (mManager.isRecording()) { toast("请先停止录制"); return; }
        mManager.clearRecords();
        for (Polyline seg : mTrackSegments) mMapView.getOverlays().remove(seg);
        mTrackSegments.clear();
        mTrackPoints.clear();
        for (Marker m : mHandoverMarkers) mMapView.getOverlays().remove(m);
        mHandoverMarkers.clear();
        for (Marker m : mWifiMarkers) mMapView.getOverlays().remove(m);
        mWifiMarkers.clear();
        removeServingCellLine();
        clearCellOverlays();
        mMapView.invalidate();
        mTvCount.setText("已录制 0 条");
        // Reset stats display
        mStatsRsrpMin = Integer.MAX_VALUE; mStatsRsrpMax = Integer.MIN_VALUE; mStatsRsrpSum = 0; mStatsRsrpCount = 0;
        mStatsSinrMin = Integer.MAX_VALUE; mStatsSinrMax = Integer.MIN_VALUE; mStatsSinrSum = 0; mStatsSinrCount = 0;
        mStatsRsrqMin = Integer.MAX_VALUE; mStatsRsrqMax = Integer.MIN_VALUE; mStatsRsrqSum = 0; mStatsRsrqCount = 0;
        mStatsTaMin   = Integer.MAX_VALUE; mStatsTaMax   = 0;                 mStatsTaSum   = 0; mStatsTaCount   = 0;
        mHandoverEvents.clear();
        if (mTvStats != null) mTvStats.setText("min/avg/max");
        if (mTvLiveSignal != null) mTvLiveSignal.setText("RSRP: — dBm");
        if (mTvSpeed != null) mTvSpeed.setText("— km/h");
        toast("已清除");
    }

    private void startDriveTestAiAnalysis() {
        String ctx = buildDriveTestContext();
        if (ctx == null) { toast("暂无路测数据可分析"); return; }
        AgentFragment.setPendingAnalysis(ctx, "drive_test");
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new AgentFragment())
                .addToBackStack(null)
                .commit();
    }

    private static String getSimRat(CellSignalManager.SimSignalData d) {
        if (d.nr_SSRSRP != Integer.MAX_VALUE) return "5G NR";
        if (d.lte_RSRP != Integer.MAX_VALUE || d.lte_CI != Integer.MAX_VALUE) return "LTE";
        if (d.wcdma_CID != Integer.MAX_VALUE) return "WCDMA";
        if (d.gsm_CID != Integer.MAX_VALUE) return "GSM";
        return "未知";
    }

    private String buildDriveTestContext() {
        if (mTrackPoints.isEmpty()) return null;

        TrackPoint first = mTrackPoints.get(0);
        TrackPoint last  = mTrackPoints.get(mTrackPoints.size()-1);
        long durSec = (last.timestamp - first.timestamp) / 1000;
        String duration = String.format(Locale.US, "%02d:%02d:%02d",
                durSec/3600, (durSec%3600)/60, durSec%60);

        // RSRP distribution
        int rsrpEx=0,rsrpGood=0,rsrpOk=0,rsrpBad=0,rsrpVBad=0;
        int sinrEx=0,sinrGood=0,sinrOk=0,sinrBad=0;
        int taD0=0,taD1=0,taD2=0,taD3=0,taD4=0; // <500m,500-1k,1-2k,2-3k,>3k
        for (TrackPoint tp : mTrackPoints) {
            if (tp.rsrp != Integer.MAX_VALUE) {
                if      (tp.rsrp >= -80)  rsrpEx++;
                else if (tp.rsrp >= -90)  rsrpGood++;
                else if (tp.rsrp >= -100) rsrpOk++;
                else if (tp.rsrp >= -110) rsrpBad++;
                else                      rsrpVBad++;
            }
            if (tp.sinr != Integer.MAX_VALUE) {
                if      (tp.sinr >= 20) sinrEx++;
                else if (tp.sinr >= 10) sinrGood++;
                else if (tp.sinr >= 0)  sinrOk++;
                else                    sinrBad++;
            }
            int dm = tp.estimatedDistM();
            if (dm >= 0) {
                if      (dm < 500)  taD0++;
                else if (dm < 1000) taD1++;
                else if (dm < 2000) taD2++;
                else if (dm < 3000) taD3++;
                else                taD4++;
            }
        }
        int total = mTrackPoints.size();

        StringBuilder sb = new StringBuilder();
        sb.append("=== 专业路测分析数据 ===\n");
        sb.append("分析时间: ")
          .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()))
          .append("\n");
        sb.append("设备: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL)
          .append("  Android ").append(android.os.Build.VERSION.RELEASE)
          .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");

        // 一、测试配置
        sb.append("【一、测试概况】\n");
        if (MainActivity.signalManager != null) {
            List<CellSignalManager.SimSignalData> sims = MainActivity.signalManager.getSimDataList();
            if (!sims.isEmpty()) {
                CellSignalManager.SimSignalData d = (mTestSimIndex >= 0 && mTestSimIndex < sims.size())
                        ? sims.get(mTestSimIndex) : sims.get(0);
                sb.append("SIM: ").append(d.simLabel).append("  运营商: ").append(d.operatorName)
                  .append("  制式: ").append(getSimRat(d)).append("\n");
            }
        }
        sb.append("总测试点: ").append(total).append("个")
          .append("  测试时长: ").append(duration)
          .append("  切换次数: ").append(mHandoverEvents.size()).append("次\n\n");

        // 二、当前服务小区 (最后一个有效点)
        sb.append("【二、当前服务小区信息】\n");
        if (last.ci != Integer.MAX_VALUE && last.ci > 0) {
            sb.append("CI: ").append(last.ci)
              .append(" (eNB=").append((last.ci >> 8) & 0xFFFFF)
              .append(", Cell=").append(last.ci & 0xFF).append(")\n");
        }
        if (last.pci != Integer.MAX_VALUE) sb.append("PCI: ").append(last.pci).append("\n");
        if (last.tac != Integer.MAX_VALUE) sb.append("TAC: ").append(last.tac).append("\n");
        if (last.earfcn != Integer.MAX_VALUE) {
            String band = EarfcnDecoder.decodeLte(last.earfcn);
            sb.append("EARFCN: ").append(last.earfcn);
            if (band != null) sb.append(" (").append(band).append(")");
            sb.append("\n");
        }
        if (last.rsrp != Integer.MAX_VALUE) {
            sb.append("RSRP: ").append(last.rsrp).append(" dBm");
            if (last.sinr != Integer.MAX_VALUE) sb.append("  SINR: ").append(last.sinr).append(" dB");
            if (last.rsrq != Integer.MAX_VALUE) sb.append("  RSRQ: ").append(last.rsrq).append(" dB");
            sb.append("\n");
        }
        int lastDist = last.estimatedDistM();
        if (last.ta != Integer.MAX_VALUE) {
            sb.append("Timing Advance: ").append(last.ta)
              .append("  →  估算距离: ").append(lastDist >= 0 ? lastDist + " m" : "N/A").append("\n");
        }
        if (last.altitude > 0) sb.append("GPS高度: ").append((int)last.altitude).append(" m\n");
        // serving cell neighbors from last track point
        if (!last.neighbors.isEmpty()) {
            sb.append("末点邻区 (").append(last.neighbors.size()).append("个):\n");
            int serRsrp = last.rsrp;
            for (int i = 0; i < Math.min(8, last.neighbors.size()); i++) {
                CellSignalManager.NeighborCell nc = last.neighbors.get(i);
                sb.append("  #").append(i+1).append(" ").append(nc);
                if (serRsrp != Integer.MAX_VALUE && nc.rsrp != Integer.MAX_VALUE) {
                    int delta = nc.rsrp - serRsrp;
                    sb.append(" [ΔRSRP=").append(delta).append("dB");
                    if (delta >= -3) sb.append(" ⚠导频污染");
                    sb.append("]");
                }
                sb.append("\n");
                if (nc.earfcn != Integer.MAX_VALUE) {
                    String band = "LTE".equals(nc.rat) ? EarfcnDecoder.decodeLte(nc.earfcn)
                                                       : EarfcnDecoder.decodeNr(nc.earfcn);
                    if (band != null) sb.append("      → ").append(band).append("\n");
                }
            }
        }
        sb.append("\n");

        // 三、信号质量统计
        sb.append("【三、信号质量统计】\n");
        if (mStatsRsrpCount > 0) {
            sb.append(String.format(Locale.US, "RSRP: 最小=%d  均值=%d  最大=%d dBm\n",
                    mStatsRsrpMin, (int)(mStatsRsrpSum/mStatsRsrpCount), mStatsRsrpMax));
            sb.append(dtBar("≥-80优秀",   rsrpEx,   total));
            sb.append(dtBar("-80~-90良好", rsrpGood, total));
            sb.append(dtBar("-90~-100可用",rsrpOk,   total));
            sb.append(dtBar("-100~-110差", rsrpBad,  total));
            sb.append(dtBar("<-110极差",   rsrpVBad, total));
        }
        if (mStatsSinrCount > 0) {
            sb.append(String.format(Locale.US, "SINR: 最小=%d  均值=%d  最大=%d dB\n",
                    mStatsSinrMin, (int)(mStatsSinrSum/mStatsSinrCount), mStatsSinrMax));
            sb.append(dtBar("≥20优秀",   sinrEx,   total));
            sb.append(dtBar("10~20良好",  sinrGood, total));
            sb.append(dtBar("0~10可用",   sinrOk,   total));
            sb.append(dtBar("<0干扰",      sinrBad,  total));
        }
        if (mStatsRsrqCount > 0) {
            sb.append(String.format(Locale.US, "RSRQ: 最小=%d  均值=%d  最大=%d dB\n",
                    mStatsRsrqMin, (int)(mStatsRsrqSum/mStatsRsrqCount), mStatsRsrqMax));
        }
        if (mStatsTaCount > 0) {
            sb.append(String.format(Locale.US,
                    "TA: 最小=%d  均值=%d  最大=%d  →  距离范围: %dm ~ %dm\n",
                    mStatsTaMin, (int)(mStatsTaSum/mStatsTaCount), mStatsTaMax,
                    (int)(mStatsTaMin*78.12), (int)(mStatsTaMax*78.12)));
            int taTot = taD0+taD1+taD2+taD3+taD4;
            if (taTot > 0) {
                sb.append(dtBar("<500m",    taD0, taTot));
                sb.append(dtBar("500m-1km", taD1, taTot));
                sb.append(dtBar("1km-2km",  taD2, taTot));
                sb.append(dtBar("2km-3km",  taD3, taTot));
                sb.append(dtBar(">3km",     taD4, taTot));
            }
        }
        sb.append("\n");

        // 四、切换事件
        if (!mHandoverEvents.isEmpty()) {
            sb.append("【四、切换事件记录】\n");
            sb.append(String.format(Locale.US, "%-4s %-10s %-14s %-8s %-7s %s\n",
                    "序号","时间","PCI切换","RSRP","距离","位置"));
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            for (int i = 0; i < mHandoverEvents.size(); i++) {
                HandoverEvent he = mHandoverEvents.get(i);
                String rsrpStr = he.rsrp != Integer.MAX_VALUE ? he.rsrp + "dBm" : "N/A";
                String distStr = he.distM >= 0 ? he.distM + "m" : "N/A";
                String posStr  = String.format(Locale.US, "%.4f,%.4f",
                        he.point.getLatitude(), he.point.getLongitude());
                sb.append(String.format(Locale.US, "#%-3d %-10s %-14s %-8s %-7s %s\n",
                        i+1, sdf.format(new Date(he.timestamp)),
                        he.fromPci + "→" + he.toPci, rsrpStr, distStr, posStr));
            }
            sb.append("\n");
        }

        // 五、路测时间序列 (均匀采样最多60点)
        sb.append("【五、路测时间序列 (均匀采样最多60点)】\n");
        sb.append(String.format(Locale.US, "%-10s %-5s %-5s %-5s %-5s %-10s %-5s %-6s %-7s %-6s %s\n",
                "时间","RSRP","SINR","RSRQ","PCI","CI","TA","距离m","速度","高度m","GPS坐标"));
        int step = Math.max(1, mTrackPoints.size() / 60);
        SimpleDateFormat sdf2 = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        for (int i = 0; i < mTrackPoints.size(); i += step) {
            TrackPoint tp = mTrackPoints.get(i);
            int dm = tp.estimatedDistM();
            String speedKmh = tp.speed >= 0
                    ? String.format(Locale.US, "%.0fkm/h", tp.speed * 3.6f) : "N/A";
            sb.append(String.format(Locale.US,
                    "%-10s %-5s %-5s %-5s %-5s %-10s %-5s %-6s %-7s %-6s %.5f,%.5f\n",
                    sdf2.format(new Date(tp.timestamp)),
                    tp.rsrp  != Integer.MAX_VALUE ? String.valueOf(tp.rsrp)  : "N/A",
                    tp.sinr  != Integer.MAX_VALUE ? String.valueOf(tp.sinr)  : "N/A",
                    tp.rsrq  != Integer.MAX_VALUE ? String.valueOf(tp.rsrq)  : "N/A",
                    tp.pci   != Integer.MAX_VALUE ? String.valueOf(tp.pci)   : "N/A",
                    tp.ci    != Integer.MAX_VALUE && tp.ci > 0 ? String.valueOf(tp.ci) : "N/A",
                    tp.ta    != Integer.MAX_VALUE ? String.valueOf(tp.ta)    : "N/A",
                    dm >= 0 ? String.valueOf(dm) : "N/A",
                    speedKmh,
                    tp.altitude > 0 ? String.format(Locale.US,"%.0f",tp.altitude) : "N/A",
                    tp.point.getLatitude(), tp.point.getLongitude()));
        }
        sb.append("\n");

        // 六、问题区域标注
        sb.append("【六、网络问题区域】\n");
        boolean hasIssue = false;
        for (TrackPoint tp : mTrackPoints) {
            if (tp.rsrp != Integer.MAX_VALUE && tp.rsrp < -110) {
                sb.append(String.format(Locale.US, "弱覆盖 RSRP=%d dBm @ %.4f,%.4f\n",
                        tp.rsrp, tp.point.getLatitude(), tp.point.getLongitude()));
                hasIssue = true;
            }
        }
        for (TrackPoint tp : mTrackPoints) {
            if (tp.sinr != Integer.MAX_VALUE && tp.sinr < 3) {
                sb.append(String.format(Locale.US, "高干扰  SINR=%d dB  @ %.4f,%.4f\n",
                        tp.sinr, tp.point.getLatitude(), tp.point.getLongitude()));
                hasIssue = true;
            }
        }
        if (!hasIssue) sb.append("未检测到明显弱覆盖/高干扰区域\n");
        sb.append("\n");

        sb.append("注：天线挂高/方位角/俯仰角/发射功率需从网管系统获取，设备端无法直接采集。\n");
        sb.append("建议结合网管MR数据及路测专业软件进行深度分析。\n");

        return sb.toString();
    }

    private static String dtBar(String label, int count, int total) {
        int pct = total > 0 ? (int)(count * 100.0 / total) : 0;
        return String.format(Locale.US, "  %-12s %4d点 (%2d%%)\n", label, count, pct);
    }

    private void markHandover(GeoPoint location, int fromPci, int toPci) {
        int sizePx = (int)(dpToPx(22));
        Bitmap bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c  = new Canvas(bm);
        Paint  p  = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFF8800); // orange
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 1, p);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 2, p);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(sizePx * 0.45f);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("HO", sizePx / 2f, sizePx / 2f + p.getTextSize() * 0.35f, p);

        Marker m = new Marker(mMapView);
        m.setPosition(location);
        m.setIcon(new android.graphics.drawable.BitmapDrawable(getResources(), bm));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        m.setTitle(String.format(Locale.US, "切换: PCI %d → %d", fromPci, toPci));
        mHandoverMarkers.add(m);
        mMapView.getOverlays().add(m);
    }

    private void updateButtonState() {
        boolean rec = mManager.isRecording();
        if (mFabStartStop != null)
            mFabStartStop.setText(rec ? "停止\n路测" : "开始\n路测");
        int statVis = rec ? View.VISIBLE : View.GONE;
        if (mCardMiniChart != null) mCardMiniChart.setVisibility(statVis);
        if (mTvSpeed       != null) mTvSpeed.setVisibility(statVis);
        if (mTvLiveSignal  != null) mTvLiveSignal.setVisibility(statVis);
        if (mTvStats       != null) mTvStats.setVisibility(statVis);
        // Hide cell info card during recording (avoid overlap with mini chart)
        // On stop, leave it GONE — user re-opens it by tapping a sector
        if (rec && mCardCellInfo != null) mCardCellInfo.setVisibility(View.GONE);
        // Hide signal overlay during recording (tv_live_signal shows same values)
        if (mCardSignalOverlay != null) mCardSignalOverlay.setVisibility(rec ? View.GONE : View.VISIBLE);
        // Remove serving cell line when not recording
        if (!rec) removeServingCellLine();
    }

    /** Refresh live signal text from the latest CellSignalManager snapshot (500 ms cadence). */
    private void refreshLiveSignal() {
        if (MainActivity.signalManager == null) return;
        List<CellSignalManager.SimSignalData> sims = MainActivity.signalManager.getSimDataList();
        if (sims.isEmpty()) return;
        CellSignalManager.SimSignalData d = (mTestSimIndex >= 0 && mTestSimIndex < sims.size())
                ? sims.get(mTestSimIndex) : sims.get(0);
        int rsrp = d.lte_RSRP  != Integer.MAX_VALUE ? d.lte_RSRP  : d.nr_SSRSRP;
        int sinr = d.lte_SINR  != Integer.MAX_VALUE ? d.lte_SINR  : d.nr_SSSINR;
        int rsrq = d.lte_RSRQ  != Integer.MAX_VALUE ? d.lte_RSRQ  : d.nr_SSRSRQ;
        if (mTvLiveSignal != null && rsrp != Integer.MAX_VALUE) {
            mTvLiveSignal.setText(String.format(Locale.US,
                    "RSRP %d  SINR %s  RSRQ %s",
                    rsrp,
                    sinr == Integer.MAX_VALUE ? "—" : String.valueOf(sinr),
                    rsrq == Integer.MAX_VALUE ? "—" : String.valueOf(rsrq)));
        }
    }

    public void updateSignalOverlay() {
        if (mTvOverlayRsrp == null || MainActivity.signalManager == null) return;
        List<CellSignalManager.SimSignalData> sims = MainActivity.signalManager.getSimDataList();
        if (sims.isEmpty()) return;
        CellSignalManager.SimSignalData d = (mTestSimIndex >= 0 && mTestSimIndex < sims.size())
                ? sims.get(mTestSimIndex) : sims.get(0);

        int rsrp = d.lte_RSRP != Integer.MAX_VALUE ? d.lte_RSRP : d.nr_SSRSRP;
        int sinr = d.lte_SINR != Integer.MAX_VALUE ? d.lte_SINR : d.nr_SSSINR;
        int rsrq = d.lte_RSRQ != Integer.MAX_VALUE ? d.lte_RSRQ : d.nr_SSRSRQ;

        mTvOverlayRsrp.setText(String.format(Locale.US, "RSRP %s dBm",
                rsrp != Integer.MAX_VALUE ? String.valueOf(rsrp) : "—"));
        mTvOverlaySinr.setText(String.format(Locale.US, "SINR %s dB",
                sinr != Integer.MAX_VALUE ? String.valueOf(sinr) : "—"));
        mTvOverlayRsrq.setText(String.format(Locale.US, "RSRQ %s dB",
                rsrq != Integer.MAX_VALUE ? String.valueOf(rsrq) : "—"));

        int rsrpColor = rsrp != Integer.MAX_VALUE ? getMetricColor(rsrp, METRIC_RSRP) : 0xFFFFFFFF;
        mTvOverlayRsrp.setTextColor(rsrpColor);
    }

    // ── Serving cell connecting line ──────────────────────────────────────────

    /** Returns the lat/lon of the currently-marked serving sector, or null. */
    private GeoPoint findServingCellPoint() {
        for (SectorEntry s : mAllSectorCache.values()) {
            if (s.isServing) return new GeoPoint(s.lat, s.lon);
        }
        return null;
    }

    /** Draw/update a semi-transparent line from currentPos to the serving cell tower. */
    private void updateServingCellLine(GeoPoint currentPos) {
        GeoPoint tower = findServingCellPoint();
        if (tower == null) { removeServingCellLine(); return; }

        if (mServingCellLine == null) {
            mServingCellLine = new Polyline();
            mServingCellLine.getOutlinePaint().setStrokeWidth(3.5f);
            mServingCellLine.getOutlinePaint().setColor(0xAAFF8800);   // semi-transparent orange
            mServingCellLine.getOutlinePaint().setPathEffect(
                    new android.graphics.DashPathEffect(new float[]{18, 10}, 0));
            mServingCellLine.setInfoWindow(null);
            mMapView.getOverlays().add(mServingCellLine);
        }
        List<GeoPoint> pts = new ArrayList<>(2);
        pts.add(currentPos);
        pts.add(tower);
        mServingCellLine.setPoints(pts);
        mMapView.invalidate();
    }

    private void removeServingCellLine() {
        if (mServingCellLine != null) {
            mMapView.getOverlays().remove(mServingCellLine);
            mServingCellLine = null;
            mMapView.invalidate();
        }
    }

    private void updateSimLabel() {
        if (mTvSimSelect == null) return;
        String[] labels = {"测试: 全部", "测试: 卡1", "测试: 卡2"};
        mTvSimSelect.setText(labels[mTestSimIndex + 1]);
    }

    // ── Compass radial menu ───────────────────────────────────────────────────

    private void toggleCompass() {
        if (mIsCompassExpanded) collapseCompassItems();
        else expandCompassItems();
        mIsCompassExpanded = !mIsCompassExpanded;
    }

    private void expandCompassItems() {
        float r = dpToPx(96);
        Button[] fabs   = {mFabStartStop, mFabMeasure, mFabRefresh, mFabLayer, mFabMore};
        double[] angles = {90, 112.5, 135, 157.5, 180};

        mCompassDisc.setVisibility(View.VISIBLE);
        mCompassDisc.animate().alpha(0.7f).setDuration(200).start();

        for (int i = 0; i < fabs.length; i++) {
            fabs[i].setVisibility(View.VISIBLE);
            fabs[i].setTranslationX(0);
            fabs[i].setTranslationY(0);
            float tx = (float)(r * Math.cos(Math.toRadians(angles[i])));
            float ty = (float)(-r * Math.sin(Math.toRadians(angles[i])));
            fabs[i].animate()
                    .translationX(tx).translationY(ty)
                    .setDuration(300)
                    .setStartDelay(i * 30L)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        }
    }

    private void collapseCompassItems() {
        Button[] fabs = {mFabStartStop, mFabMeasure, mFabRefresh, mFabLayer, mFabMore};

        mCompassDisc.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> mCompassDisc.setVisibility(View.INVISIBLE))
                .start();

        for (Button fab : fabs) {
            fab.animate()
                    .translationX(0).translationY(0)
                    .setDuration(200)
                    .setInterpolator(null)
                    .withEndAction(() -> fab.setVisibility(View.INVISIBLE))
                    .start();
        }
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private void showPlaybackFilePicker() {
        File dir = requireContext().getExternalFilesDir("drivetest");
        if (dir == null || !dir.exists()) { toast("无可回放的路测文件"); return; }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".csv"));
        if (files == null || files.length == 0) { toast("无可回放的路测文件"); return; }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
        final File[] filesFinal = files;
        new AlertDialog.Builder(requireContext())
                .setTitle("选择回放文件")
                .setItems(names, (dialog, which) -> startPlayback(filesFinal[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void startPlayback(File csvFile) {
        List<PlaybackEntry> records = parseCsvForPlayback(csvFile);
        if (records.isEmpty()) { toast("文件解析失败或无数据"); return; }
        mPlaybackRecords.clear();
        mPlaybackRecords.addAll(records);
        mPlaybackIndex   = 0;
        mIsPlaying       = false;

        mCardPlayback.setVisibility(View.VISIBLE);
        mCardCellInfo.setVisibility(View.GONE);
        mTvPlaybackFile.setText(csvFile.getName());
        mSeekBarPlayback.setMax(records.size() - 1);
        mSeekBarPlayback.setProgress(0);
        mBtnPlaybackPlayPause.setText("▶ 播放");

        if (mPlaybackPath != null) { mMapView.getOverlays().remove(mPlaybackPath); mPlaybackPath = null; }
        redrawPlaybackPath();

        // Playback position marker
        if (mPlaybackMarker != null) mMapView.getOverlays().remove(mPlaybackMarker);
        mPlaybackMarker = new Marker(mMapView);
        mPlaybackMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mMapView.getOverlays().add(mPlaybackMarker);

        advancePlaybackTo(0);
        mMapView.invalidate();
        toast("已加载 " + records.size() + " 条，点击播放开始回放");
    }

    private List<PlaybackEntry> parseCsvForPlayback(File csvFile) {
        List<PlaybackEntry> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String header = br.readLine();
            if (header == null) return result;
            // New format adds altitude,speed_ms,bearing after accuracy (shifts simLabel etc by 3)
            boolean newFmt = header.contains("altitude");
            int slIdx = newFmt ? 7 : 4, opIdx = newFmt ? 8 : 5, ratIdx = newFmt ? 9 : 6;
            int rsrpIdx = newFmt ? 10 : 7, rsrqIdx = newFmt ? 11 : 8;
            int sinrIdx = newFmt ? 12 : 9, pciIdx  = newFmt ? 13 : 10;
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length <= pciIdx) continue;
                try {
                    long   ts  = Long.parseLong(p[0].trim());
                    double lat = Double.parseDouble(p[1].trim());
                    double lon = Double.parseDouble(p[2].trim());
                    String sl  = p[slIdx].trim();
                    String op  = p[opIdx].trim();
                    String rat = p[ratIdx].trim();
                    int rsrp = parseIntOrMax(p[rsrpIdx]);
                    int rsrq = parseIntOrMax(p[rsrqIdx]);
                    int sinr = parseIntOrMax(p[sinrIdx]);
                    int pci  = parseIntOrMax(p[pciIdx]);
                    result.add(new PlaybackEntry(ts, lat, lon, rat, rsrp, rsrq, sinr, pci, sl, op));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException ignored) {}
        return result;
    }

    private void advancePlaybackTo(int index) {
        if (index < 0 || index >= mPlaybackRecords.size()) return;
        mPlaybackIndex = index;
        PlaybackEntry e = mPlaybackRecords.get(index);
        GeoPoint gp = new GeoPoint(e.latitude, e.longitude);
        if (mPlaybackMarker != null) {
            mPlaybackMarker.setPosition(gp);
            mPlaybackMarker.setTitle(String.format(Locale.US, "%s %s RSRP:%s",
                    e.simLabel, e.rat,
                    e.rsrp == Integer.MAX_VALUE ? "N/A" : e.rsrp + "dBm"));
        }
        mMapView.getController().animateTo(gp);
        mSeekBarPlayback.setProgress(index);
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date(e.timestamp));
        mTvPlaybackTime.setText(ts + "  " + (index + 1) + " / " + mPlaybackRecords.size() + " 条");

        // Signal parameters display
        if (mTvPlaybackSignal != null) {
            String rsrpStr = e.rsrp == Integer.MAX_VALUE ? "—" : e.rsrp + " dBm";
            String sinrStr = e.sinr == Integer.MAX_VALUE ? "—" : e.sinr + " dB";
            String rsrqStr = e.rsrq == Integer.MAX_VALUE ? "—" : e.rsrq + " dB";
            String pciStr  = e.pci  == Integer.MAX_VALUE ? "—" : String.valueOf(e.pci);
            mTvPlaybackSignal.setText(String.format(Locale.US,
                    "RSRP %s  SINR %s  RSRQ %s  PCI %s", rsrpStr, sinrStr, rsrqStr, pciStr));
            int rsrpColor = (e.rsrp != Integer.MAX_VALUE)
                    ? getMetricColor(e.rsrp, METRIC_RSRP) : 0xFF757575;
            mTvPlaybackSignal.setTextColor(rsrpColor);
        }

        // Serving cell line for playback
        updateServingCellLine(gp);

        mMapView.invalidate();
    }

    private void togglePlayback() {
        if (mPlaybackRecords.isEmpty()) return;
        mIsPlaying = !mIsPlaying;
        mBtnPlaybackPlayPause.setText(mIsPlaying ? "⏸ 暂停" : "▶ 播放");
        if (mIsPlaying) {
            if (mPlaybackIndex >= mPlaybackRecords.size() - 1) mPlaybackIndex = 0;
            mPlaybackHandler.post(mPlaybackRunnable);
        } else {
            mPlaybackHandler.removeCallbacks(mPlaybackRunnable);
        }
    }

    private void cyclePlaybackSpeed() {
        int[] speeds = {1, 2, 5, 10};
        int idx = 0;
        for (int i = 0; i < speeds.length; i++) {
            if (speeds[i] == mPlaybackSpeedMult) { idx = i; break; }
        }
        mPlaybackSpeedMult = speeds[(idx + 1) % speeds.length];
        mBtnPlaybackSpeed.setText(mPlaybackSpeedMult + "×");
    }

    private void resetPlayback() {
        mPlaybackHandler.removeCallbacks(mPlaybackRunnable);
        mIsPlaying = false;
        mBtnPlaybackPlayPause.setText("▶ 播放");
        if (!mPlaybackRecords.isEmpty()) advancePlaybackTo(0);
    }

    private void stopPlayback() {
        mPlaybackHandler.removeCallbacks(mPlaybackRunnable);
        mIsPlaying = false;
        mPlaybackRecords.clear();
        mPlaybackIndex = 0;
        if (mPlaybackPath != null) {
            mMapView.getOverlays().remove(mPlaybackPath);
            mPlaybackPath = null;
        }
        for (Polyline seg : mPlaybackSegments) mMapView.getOverlays().remove(seg);
        mPlaybackSegments.clear();
        if (mPlaybackMarker != null) {
            mMapView.getOverlays().remove(mPlaybackMarker);
            mPlaybackMarker = null;
        }
        removeServingCellLine();
        mCardPlayback.setVisibility(View.GONE);
        mMapView.invalidate();
    }

    // ── Track drawing helpers ─────────────────────────────────────────────────

    private static int parseIntOrMax(String s) {
        s = s.trim();
        return "N/A".equals(s) || s.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(s);
    }

    private void addTrackPoint(TrackPoint tp) {
        if (!mTrackPoints.isEmpty()) {
            TrackPoint prev = mTrackPoints.get(mTrackPoints.size() - 1);
            Polyline seg = makeTrackSegment(prev, tp);
            mTrackSegments.add(seg);
            mMapView.getOverlays().add(seg);
        }
        mTrackPoints.add(tp);
    }

    private Polyline makeTrackSegment(TrackPoint p1, TrackPoint p2) {
        int v1 = getMetricValue(p1, mTrackMetric);
        int v2 = getMetricValue(p2, mTrackMetric);
        int avg = (v1 == Integer.MAX_VALUE || v2 == Integer.MAX_VALUE)
                ? Integer.MAX_VALUE : (v1 + v2) / 2;
        Polyline seg = new Polyline();
        seg.getOutlinePaint().setStrokeWidth(7f);
        seg.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        seg.getOutlinePaint().setColor(getMetricColor(avg, mTrackMetric));
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(p1.point);
        pts.add(p2.point);
        seg.setPoints(pts);
        return seg;
    }

    private int getMetricValue(TrackPoint tp, int metric) {
        switch (metric) {
            case METRIC_RSRP: return tp.rsrp;
            case METRIC_SINR: return tp.sinr;
            case METRIC_RSRQ: return tp.rsrq;
            default:          return Integer.MAX_VALUE;
        }
    }

    private int getMetricColor(int value, int metric) {
        if (value == Integer.MAX_VALUE) return 0xFF888888;
        int[] thresh;
        int[] colors;
        switch (metric) {
            case METRIC_RSRP: thresh = RSRP_THRESH; colors = RSRP_COLORS; break;
            case METRIC_SINR: thresh = SINR_THRESH; colors = SINR_COLORS; break;
            case METRIC_RSRQ: thresh = RSRQ_THRESH; colors = RSRQ_COLORS; break;
            default: return 0xFF888888;
        }
        for (int i = 0; i < thresh.length; i++) {
            if (value >= thresh[i]) return colors[i];
        }
        return colors[colors.length - 1];
    }

    private void redrawTrack() {
        for (Polyline seg : mTrackSegments) mMapView.getOverlays().remove(seg);
        mTrackSegments.clear();
        for (int i = 1; i < mTrackPoints.size(); i++) {
            Polyline seg = makeTrackSegment(mTrackPoints.get(i - 1), mTrackPoints.get(i));
            mTrackSegments.add(seg);
            mMapView.getOverlays().add(seg);
        }
        mMapView.invalidate();
    }

    private void redrawPlaybackPath() {
        for (Polyline seg : mPlaybackSegments) mMapView.getOverlays().remove(seg);
        mPlaybackSegments.clear();
        for (int i = 1; i < mPlaybackRecords.size(); i++) {
            PlaybackEntry a = mPlaybackRecords.get(i - 1);
            PlaybackEntry b = mPlaybackRecords.get(i);
            int va = getPlaybackMetricValue(a);
            int vb = getPlaybackMetricValue(b);
            int avg = (va == Integer.MAX_VALUE || vb == Integer.MAX_VALUE)
                    ? Integer.MAX_VALUE : (va + vb) / 2;
            Polyline seg = new Polyline();
            seg.getOutlinePaint().setStrokeWidth(5f);
            seg.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
            seg.getOutlinePaint().setColor(getMetricColor(avg, mTrackMetric));
            List<GeoPoint> pts = new ArrayList<>();
            pts.add(new GeoPoint(a.latitude, a.longitude));
            pts.add(new GeoPoint(b.latitude, b.longitude));
            seg.setPoints(pts);
            mPlaybackSegments.add(seg);
            mMapView.getOverlays().add(seg);
        }
        mMapView.invalidate();
    }

    private int getPlaybackMetricValue(PlaybackEntry e) {
        switch (mTrackMetric) {
            case METRIC_RSRP: return e.rsrp;
            case METRIC_SINR: return e.sinr;
            case METRIC_RSRQ: return e.rsrq;
            default:          return Integer.MAX_VALUE;
        }
    }

    private void updateMetricLabel() {
        if (mTvMetricSelect != null) mTvMetricSelect.setText(METRIC_NAMES[mTrackMetric]);
    }

    // ── WiFi layer (indoor coverage) ─────────────────────────────────────────

    private void toggleWifiLayer() {
        mShowWifiLayer = !mShowWifiLayer;
        if (mShowWifiLayer) {
            toast("WiFi 层已开启，每次定位时自动扫描");
            scanAndPlotWifi(mLocationOverlay != null ? mLocationOverlay.getMyLocation() : null);
        } else {
            for (Marker m : mWifiMarkers) mMapView.getOverlays().remove(m);
            mWifiMarkers.clear();
            mMapView.invalidate();
            toast("WiFi 层已关闭");
        }
    }

    private void scanAndPlotWifi(GeoPoint location) {
        if (!mShowWifiLayer || location == null) return;
        WifiManager wm = (WifiManager) requireContext()
                .getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
        if (wm == null || !wm.isWifiEnabled()) return;
        wm.startScan();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isAdded() || mMapView == null) return;
            java.util.List<ScanResult> results = wm.getScanResults();
            if (results == null) return;
            // Plot top-10 strongest APs at current GPS position with RSSI color
            java.util.List<ScanResult> sorted = new java.util.ArrayList<>(results);
            java.util.Collections.sort(sorted, (a, b) -> Integer.compare(b.level, a.level));
            int plotCount = Math.min(sorted.size(), 10);
            for (int i = 0; i < plotCount; i++) {
                ScanResult r = sorted.get(i);
                Marker m = new Marker(mMapView);
                m.setPosition(new GeoPoint(
                        location.getLatitude() + (i - plotCount / 2) * 0.000005,
                        location.getLongitude()));
                m.setIcon(makeWifiMarker(r.level));
                m.setTitle(r.SSID.isEmpty() ? "<隐藏>" : r.SSID);
                m.setSnippet(String.format(Locale.US, "%d dBm  Ch.%d  %s",
                        r.level, wifiFreqToChannel(r.frequency), r.BSSID));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                mWifiMarkers.add(m);
                mMapView.getOverlays().add(m);
            }
            mMapView.invalidate();
        }, 1500);
    }

    private android.graphics.drawable.Drawable makeWifiMarker(int rssi) {
        int sizePx = (int) dpToPx(16);
        Bitmap bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Color by RSSI: ≥-50 green, ≥-65 yellow, ≥-75 orange, else red
        int color = rssi >= -50 ? 0xFF00B050
                  : rssi >= -65 ? 0xFFFFCC00
                  : rssi >= -75 ? 0xFFFF8C00 : 0xFFFF0000;
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 1, p);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 2, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bm);
    }

    private static int wifiFreqToChannel(int freqMhz) {
        if (freqMhz == 2484) return 14;
        if (freqMhz < 2484)  return (freqMhz - 2407) / 5;
        if (freqMhz >= 5180) return (freqMhz - 5000) / 5;
        return 0;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void toast(String msg) {
        if (getContext() != null)
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
