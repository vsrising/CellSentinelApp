package com.asun.cellsentinelapp;

import android.graphics.Color;
import android.graphics.Paint;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws cell sectors, connection lines, and distance labels on an OsmDroid MapView.
 */
public class SectorDrawer {

    public static final double DEFAULT_SECTOR_RADIUS_M = 400.0;
    public static final double BEAM_WIDTH_DEG          = 120.0;  // ±60° half-width

    // ── Sector polygon ────────────────────────────────────────────────────────

    /**
     * Build a sector polygon overlay.
     *
     * @param lat       Cell latitude
     * @param lon       Cell longitude
     * @param azimuth   Azimuth in degrees (0 = North, clockwise)
     * @param radiusM   Sector radius in metres
     * @param fillColor Fill color (with alpha)
     * @param strokeColor Stroke color
     */
    public static Polygon buildSector(double lat, double lon, int azimuth,
                                      double radiusM, int fillColor, int strokeColor) {
        List<GeoPoint> pts = computeSectorPoints(lat, lon, azimuth, BEAM_WIDTH_DEG, radiusM);

        Polygon polygon = new Polygon();
        polygon.setPoints(pts);
        polygon.setFillColor(fillColor);
        polygon.setStrokeColor(strokeColor);
        polygon.setStrokeWidth(2f);
        return polygon;
    }

    /** Serving cell sector — solid green fill. */
    public static Polygon servingSector(double lat, double lon, int azimuth, double radiusM) {
        return buildSector(lat, lon, azimuth, radiusM,
                Color.argb(80, 0, 200, 0),
                Color.argb(220, 0, 180, 0));
    }

    /** Neighbor cell sector — blue fill. */
    public static Polygon neighborSector(double lat, double lon, int azimuth, double radiusM) {
        return buildSector(lat, lon, azimuth, radiusM,
                Color.argb(60, 0, 120, 255),
                Color.argb(200, 0, 100, 220));
    }

    /** Unknown / area cell sector — grey fill. */
    public static Polygon areaSector(double lat, double lon, int azimuth, double radiusM) {
        return buildSector(lat, lon, azimuth, radiusM,
                Color.argb(40, 100, 100, 100),
                Color.argb(150, 80, 80, 80));
    }

    // ── Connection lines ──────────────────────────────────────────────────────

    /** Line from GPS position to serving cell (orange). */
    public static Polyline servingLine(double gpsLat, double gpsLon,
                                       double cellLat, double cellLon) {
        Polyline line = new Polyline();
        line.getOutlinePaint().setColor(Color.argb(220, 255, 140, 0));
        line.getOutlinePaint().setStrokeWidth(4f);
        line.getOutlinePaint().setStyle(Paint.Style.STROKE);
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(new GeoPoint(gpsLat, gpsLon));
        pts.add(new GeoPoint(cellLat, cellLon));
        line.setPoints(pts);
        return line;
    }

    /** Line from serving cell to neighbor cell (dashed blue). */
    public static Polyline neighborLine(double servLat, double servLon,
                                        double nbrLat, double nbrLon) {
        Polyline line = new Polyline();
        line.getOutlinePaint().setColor(Color.argb(180, 0, 100, 220));
        line.getOutlinePaint().setStrokeWidth(2.5f);
        line.getOutlinePaint().setStyle(Paint.Style.STROKE);
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(new GeoPoint(servLat, servLon));
        pts.add(new GeoPoint(nbrLat, nbrLon));
        line.setPoints(pts);
        return line;
    }

    // ── Named marker at cell site ─────────────────────────────────────────────

    /** Visible marker at the cell site showing cell name; tap to see details. */
    public static Marker nameLabel(MapView mapView, double lat, double lon,
                                   String title, String snippet) {
        Marker m = new Marker(mapView);
        m.setPosition(new GeoPoint(lat, lon));
        m.setTitle(title);
        m.setSnippet(snippet);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        return m;
    }

    // ── Distance label marker ─────────────────────────────────────────────────

    /** Invisible midpoint marker; tap to show distance. */
    public static Marker distanceMarker(MapView mapView,
                                         double lat1, double lon1,
                                         double lat2, double lon2,
                                         String label) {
        double midLat = (lat1 + lat2) / 2;
        double midLon = (lon1 + lon2) / 2;
        double distM  = distanceMetres(lat1, lon1, lat2, lon2);
        Marker m = new Marker(mapView);
        m.setPosition(new GeoPoint(midLat, midLon));
        m.setTitle(label);
        m.setSnippet(formatDistance(distM));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        m.setAlpha(0f);
        return m;
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /**
     * Compute a list of GeoPoints forming a sector polygon.
     * Points: center → arc from (azimuth-half) to (azimuth+half) → center.
     */
    public static List<GeoPoint> computeSectorPoints(double lat, double lon,
                                                      int azimuth, double beamWidthDeg,
                                                      double radiusM) {
        List<GeoPoint> pts = new ArrayList<>();
        double half = beamWidthDeg / 2.0;
        double startBearing = azimuth - half;
        double endBearing   = azimuth + half;
        int arcSteps = 24;

        pts.add(new GeoPoint(lat, lon));  // center
        for (int i = 0; i <= arcSteps; i++) {
            double bearing = startBearing + (endBearing - startBearing) * i / arcSteps;
            pts.add(destination(lat, lon, radiusM, bearing));
        }
        pts.add(new GeoPoint(lat, lon));  // back to center
        return pts;
    }

    /**
     * Compute destination point given origin, distance (metres), and bearing (degrees from N).
     * Uses spherical Earth Haversine approximation.
     */
    public static GeoPoint destination(double lat, double lon, double distM, double bearingDeg) {
        final double R = 6_371_000.0;
        double φ1 = Math.toRadians(lat);
        double λ1 = Math.toRadians(lon);
        double θ  = Math.toRadians(bearingDeg);
        double δ  = distM / R;

        double φ2 = Math.asin(Math.sin(φ1) * Math.cos(δ)
                + Math.cos(φ1) * Math.sin(δ) * Math.cos(θ));
        double λ2 = λ1 + Math.atan2(Math.sin(θ) * Math.sin(δ) * Math.cos(φ1),
                Math.cos(δ) - Math.sin(φ1) * Math.sin(φ2));

        return new GeoPoint(Math.toDegrees(φ2), Math.toDegrees(λ2));
    }

    /** Distance in metres between two coordinates (Haversine). */
    public static double distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Human-readable distance string. */
    public static String formatDistance(double metres) {
        if (metres >= 1000) {
            return String.format(Locale.US, "%.2f km", metres / 1000);
        }
        return String.format(Locale.US, "%.0f m", metres);
    }
}
