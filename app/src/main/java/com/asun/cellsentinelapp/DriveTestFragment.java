package com.asun.cellsentinelapp;

import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DriveTestFragment extends Fragment implements DriveTestManager.StateListener {

    // ── Map tile sources ──────────────────────────────────────────────────────

    private static final String[] LAYER_NAMES = {"OSM 标准", "ESRI 卫星", "Google 卫星", "高德 卫星"};

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

    private static final OnlineTileSourceBase[] TILE_SOURCES = {
            null,              // placeholder for OSM (set at init)
            null,              // ESRI — created lazily
            null,              // Google — created lazily
            null               // Amap — created lazily
    };

    // ── Fields ────────────────────────────────────────────────────────────────

    private MapView mMapView;
    private MyLocationNewOverlay mLocationOverlay;
    private ScaleBarOverlay      mScaleBarOverlay;
    private Polyline             mPathOverlay;
    private List<GeoPoint>       mPathPoints = new ArrayList<>();

    // Cell overlays (cleared on each refresh)
    private final List<Polygon>  mSectorOverlays = new ArrayList<>();
    private final List<Polyline> mLineOverlays   = new ArrayList<>();
    private final List<Marker>   mMarkerOverlays = new ArrayList<>();

    private DriveTestManager mManager;
    private boolean  mMeasureMode   = false;
    private GeoPoint mMeasurePoint1 = null;
    private Polyline mMeasureLine   = null;

    private TextView mTvStatus;
    private TextView mTvCount;
    private Button   mBtnStartStop;
    private Button   mBtnMeasure;
    private Button   mBtnRefreshCells;
    private Spinner  mSpinnerLayer;
    private TextView mTvMeasureResult;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Must init osmdroid config before MapView is created
        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        View root = inflater.inflate(R.layout.fragment_drive_test, container, false);

        mMapView          = root.findViewById(R.id.map_view);
        mTvStatus         = root.findViewById(R.id.tv_gps_status);
        mTvCount          = root.findViewById(R.id.tv_record_count);
        mBtnStartStop     = root.findViewById(R.id.btn_start_stop);
        mBtnMeasure       = root.findViewById(R.id.btn_measure);
        mBtnRefreshCells  = root.findViewById(R.id.btn_refresh_cells);
        mSpinnerLayer     = root.findViewById(R.id.spinner_layer);
        mTvMeasureResult  = root.findViewById(R.id.tv_measure_result);
        View cardMeasure  = root.findViewById(R.id.card_measure);

        setupMap();
        setupLayerSpinner();

        mManager = new DriveTestManager(requireContext());
        mManager.setStateListener(this);

        mBtnStartStop.setOnClickListener(v -> toggleRecording());
        mBtnMeasure.setOnClickListener(v -> toggleMeasureMode());
        mBtnRefreshCells.setOnClickListener(v -> refreshCellOverlays());

        root.findViewById(R.id.btn_export).setOnClickListener(v -> exportCsv());
        root.findViewById(R.id.btn_upload).setOnClickListener(v -> uploadRecords());
        root.findViewById(R.id.btn_clear).setOnClickListener(v -> clearRecords());

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
        if (mLocationOverlay != null) mLocationOverlay.enableMyLocation();
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

        // GPS location overlay
        mLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(requireContext()), mMapView);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();
        mLocationOverlay.runOnFirstFix(() -> requireActivity().runOnUiThread(() -> {
            GeoPoint loc = mLocationOverlay.getMyLocation();
            if (loc != null) mMapView.getController().animateTo(loc);
        }));
        mMapView.getOverlays().add(mLocationOverlay);

        // Scale bar
        mScaleBarOverlay = new ScaleBarOverlay(mMapView);
        mScaleBarOverlay.setCentred(true);
        mScaleBarOverlay.setScaleBarOffset(
                getResources().getDisplayMetrics().widthPixels / 2, 10);
        mMapView.getOverlays().add(mScaleBarOverlay);

        // Compass
        CompassOverlay compass = new CompassOverlay(requireContext(), mMapView);
        compass.enableCompass();
        mMapView.getOverlays().add(compass);

        // Rotation gestures
        RotationGestureOverlay rotation = new RotationGestureOverlay(mMapView);
        rotation.setEnabled(true);
        mMapView.getOverlays().add(rotation);

        // Drive path polyline (empty, updated on each GPS fix)
        mPathOverlay = new Polyline(mMapView);
        mPathOverlay.getOutlinePaint().setColor(Color.argb(200, 255, 60, 60));
        mPathOverlay.getOutlinePaint().setStrokeWidth(5f);
        mPathOverlay.setPoints(mPathPoints);
        mMapView.getOverlays().add(mPathOverlay);

        // Tap listener for measurement mode
        mMapView.setOnTouchListener((v, event) -> {
            if (mMeasureMode && event.getAction() == MotionEvent.ACTION_DOWN) {
                handleMeasureTap(event.getX(), event.getY());
            }
            return false;
        });
    }

    private void setupLayerSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, LAYER_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerLayer.setAdapter(adapter);
        mSpinnerLayer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                switchLayer(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
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

    // ── Zoom controls (called from layout buttons) ────────────────────────────

    public void zoomIn()  { mMapView.getController().zoomIn(); }
    public void zoomOut() { mMapView.getController().zoomOut(); }

    // ── Measure mode ──────────────────────────────────────────────────────────

    private void toggleMeasureMode() {
        mMeasureMode = !mMeasureMode;
        mMeasurePoint1 = null;
        mBtnMeasure.setText(mMeasureMode ? "取消测距" : "测距");
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

            // Draw measurement line
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

            // Reset for next measurement
            mMeasurePoint1 = null;
        }
    }

    // ── Cell sector overlays ──────────────────────────────────────────────────

    /** Max distance (metres) from serving cell to show area/neighbor sectors. */
    private static final double MAX_CELL_DIST_M = 30_000;

    public void refreshCellOverlays() {
        if (MainActivity.signalManager == null) {
            toast("信号管理器未就绪");
            return;
        }
        if (!SettingUtils.isLoggedIn(requireContext())) {
            toast("请先登录以加载基站数据");
            return;
        }
        clearCellOverlays();
        List<CellSignalManager.SimSignalData> sims = MainActivity.signalManager.getSimDataList();
        for (CellSignalManager.SimSignalData sim : sims) {
            loadLteOverlay(sim);
            loadNrOverlay(sim);
        }
    }

    /**
     * Load LTE cell overlays for one SIM.
     * Strategy: use CI decomposition (eNodeBId + cellId) for an exact serving-cell match first;
     * fall back to PCI+TAC if CI is unavailable or not found in DB.
     *
     * LTE CI convention (China carriers): CI = eNodeBId * 256 + cellIndex (8-bit)
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
                                drawServingConnection(serving, gpsPoint);
                                loadAreaCells(sim, serving, gpsPoint);
                                loadNeighborCellsFromSignal(sim, serving, gpsPoint);
                            } else {
                                // CI matched nothing — try PCI fallback
                                loadLteByPci(sim, gpsPoint);
                            }
                            mMapView.invalidate();
                        }
                        @Override
                        public void onError(String msg) {
                            toast("基站查询失败: " + msg);
                            loadLteByPci(sim, gpsPoint);
                        }
                    });
        } else {
            loadLteByPci(sim, gpsPoint);
        }
    }

    /** Fallback: find serving cell by PCI, preferring TAC match when PCI is ambiguous. */
    private void loadLteByPci(CellSignalManager.SimSignalData sim, GeoPoint gpsPoint) {
        CellSentinelApi.getLteCellsByPci(requireContext(), sim.lte_PCI, 20,
                new CellSentinelApi.CellListCallback<LteCellInfo>() {
                    @Override
                    public void onSuccess(List<LteCellInfo> cells) {
                        LteCellInfo serving = null;
                        for (LteCellInfo cell : cells) {
                            // Prefer cell whose TAC matches (reduces PCI-reuse ambiguity)
                            if (sim.lte_TAC != Integer.MAX_VALUE && cell.tac == sim.lte_TAC) {
                                serving = cell;
                                break;
                            }
                            if (serving == null) serving = cell;
                        }
                        if (serving != null) {
                            addLteSector(serving, true, gpsPoint);
                            drawServingConnection(serving, gpsPoint);
                            loadAreaCells(sim, serving, gpsPoint);
                            loadNeighborCellsFromSignal(sim, serving, gpsPoint);
                        }
                        mMapView.invalidate();
                    }
                    @Override public void onError(String msg) { toast("PCI查询失败: " + msg); }
                });
    }

    /** Draw GPS→serving-cell orange line + distance marker. */
    private void drawServingConnection(LteCellInfo serving, GeoPoint gpsPoint) {
        if (gpsPoint == null || !serving.hasValidCoords()) return;
        Polyline l = SectorDrawer.servingLine(
                gpsPoint.getLatitude(), gpsPoint.getLongitude(),
                serving.cellLat, serving.cellLon);
        mLineOverlays.add(l);
        mMapView.getOverlays().add(l);
        Marker dm = SectorDrawer.distanceMarker(mMapView,
                gpsPoint.getLatitude(), gpsPoint.getLongitude(),
                serving.cellLat, serving.cellLon, "GPS→" + serving.cellName);
        mMarkerOverlays.add(dm);
        mMapView.getOverlays().add(dm);
    }

    /** Load TAC-area cells, filtered to MAX_CELL_DIST_M around the serving cell. */
    private void loadAreaCells(CellSignalManager.SimSignalData sim,
                                LteCellInfo serving, GeoPoint gpsPoint) {
        if (sim.lte_TAC == Integer.MAX_VALUE) return;
        CellSentinelApi.getLteCellsByTac(requireContext(), sim.lte_TAC, 50,
                new CellSentinelApi.CellListCallback<LteCellInfo>() {
                    @Override
                    public void onSuccess(List<LteCellInfo> cells) {
                        for (LteCellInfo cell : cells) {
                            if (cell.pci == sim.lte_PCI) continue; // serving already drawn
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

    /** Load neighbor cells by PCI (from signal report), filtered by proximity to serving cell. */
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
                                // Filter by proximity to serving cell
                                if (serving.hasValidCoords()) {
                                    double d = SectorDrawer.distanceMetres(
                                            serving.cellLat, serving.cellLon,
                                            nbr.cellLat, nbr.cellLon);
                                    if (d > MAX_CELL_DIST_M) continue;
                                    addLteSector(nbr, false, gpsPoint);
                                    Polyline nl = SectorDrawer.neighborLine(
                                            serving.cellLat, serving.cellLon,
                                            nbr.cellLat, nbr.cellLon);
                                    mLineOverlays.add(nl);
                                    mMapView.getOverlays().add(nl);
                                    Marker dm = SectorDrawer.distanceMarker(mMapView,
                                            serving.cellLat, serving.cellLon,
                                            nbr.cellLat, nbr.cellLon,
                                            serving.cellName + "→" + nbr.cellName
                                            + "  " + SectorDrawer.formatDistance(d));
                                    mMarkerOverlays.add(dm);
                                    mMapView.getOverlays().add(dm);
                                } else {
                                    addLteSector(nbr, false, gpsPoint);
                                }
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
        Polygon sector = isServing
                ? SectorDrawer.servingSector(cell.cellLat, cell.cellLon, cell.azimuth,
                                             SectorDrawer.DEFAULT_SECTOR_RADIUS_M)
                : SectorDrawer.areaSector(cell.cellLat, cell.cellLon, cell.azimuth,
                                          SectorDrawer.DEFAULT_SECTOR_RADIUS_M);
        sector.setTitle(cell.cellName);
        sector.setSnippet(String.format(Locale.US,
                "PCI:%d  TAC:%d  方位角:%d°\n%s  %s",
                cell.pci, cell.tac, cell.azimuth, cell.frequencyBand, cell.operator));
        sector.setOnClickListener((polygon, mapView, eventPos) -> {
            polygon.showInfoWindow();
            return true;
        });
        mSectorOverlays.add(sector);
        mMapView.getOverlays().add(sector);
    }

    private void addNrSector(NrCellInfo cell, boolean isServing, GeoPoint gpsPoint) {
        if (!cell.hasValidCoords()) return;
        Polygon sector = isServing
                ? SectorDrawer.servingSector(cell.cellLat, cell.cellLon, cell.azimuth,
                                             SectorDrawer.DEFAULT_SECTOR_RADIUS_M * 0.7)
                : SectorDrawer.neighborSector(cell.cellLat, cell.cellLon, cell.azimuth,
                                              SectorDrawer.DEFAULT_SECTOR_RADIUS_M * 0.7);
        sector.setTitle(cell.cellName);
        sector.setSnippet(String.format(Locale.US,
                "PCI:%d  TAC:%d  方位角:%d°\n%s  %s",
                cell.physicalCellId, cell.tac, cell.azimuth, cell.frequencyBand, cell.operator));
        sector.setOnClickListener((polygon, mapView, eventPos) -> {
            polygon.showInfoWindow();
            return true;
        });
        mSectorOverlays.add(sector);
        mMapView.getOverlays().add(sector);
    }

    private void clearCellOverlays() {
        for (Polygon p : mSectorOverlays) mMapView.getOverlays().remove(p);
        for (Polyline l : mLineOverlays)  mMapView.getOverlays().remove(l);
        for (Marker m : mMarkerOverlays)  mMapView.getOverlays().remove(m);
        mSectorOverlays.clear();
        mLineOverlays.clear();
        mMarkerOverlays.clear();
    }

    /** Parse PCI from neighbor cell description string, e.g. "LTE   PCI:123  EARFCN:..." */
    private static int parsePciFromNeighborString(String s) {
        try {
            int pciIdx = s.indexOf("PCI:");
            if (pciIdx < 0) return -1;
            int start = pciIdx + 4;
            int end = start;
            while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == ' ')) {
                if (s.charAt(end) == ' ' && end > start) break;
                if (Character.isDigit(s.charAt(end))) end++;
                else break;
            }
            // Trim spaces
            String pciStr = s.substring(start, end).trim();
            if (pciStr.isEmpty()) return -1;
            return Integer.parseInt(pciStr);
        } catch (Exception e) {
            return -1;
        }
    }

    // ── DriveTestManager.StateListener ──────────────────────────────────────

    @Override
    public void onLocationUpdate(Location location, DriveTestRecord newRecord) {
        GeoPoint gp = new GeoPoint(location.getLatitude(), location.getLongitude());
        mPathPoints.add(gp);
        mPathOverlay.setPoints(new ArrayList<>(mPathPoints));
        mMapView.getController().animateTo(gp);
        mMapView.invalidate();

        String gpsText = String.format(Locale.US,
                "GPS  %.5f, %.5f  ±%.0fm",
                location.getLatitude(), location.getLongitude(), location.getAccuracy());
        mTvStatus.setText(gpsText);
        mTvCount.setText("已录制 " + mManager.getRecordCount() + " 条");
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
            toast("已停止，共 " + mManager.getRecordCount() + " 条");
        } else {
            boolean ok = mManager.startRecording(DriveTestManager.generateSessionName());
            if (ok) {
                toast("开始路测");
                // Auto-refresh cell overlays when recording starts
                refreshCellOverlays();
            }
        }
        updateButtonState();
    }

    private void exportCsv() {
        if (mManager.getRecordCount() == 0) { toast("暂无数据"); return; }
        File f = mManager.exportCsv();
        toast(f != null ? "已导出: " + f.getName() : "导出失败");
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
        mPathPoints.clear();
        mPathOverlay.setPoints(new ArrayList<>());
        clearCellOverlays();
        mMapView.invalidate();
        mTvCount.setText("已录制 0 条");
        toast("已清除");
    }

    private void updateButtonState() {
        boolean rec = mManager.isRecording();
        mBtnStartStop.setText(rec ? "停止路测" : "开始路测");
    }

    private void toast(String msg) {
        if (getContext() != null)
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
