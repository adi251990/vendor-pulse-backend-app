package com.hireme.platform.common.util;

/**
 * Haversine distance + a cheap lat/lon bounding box helper. Used by the
 * matching engine's cold-start candidate query (spec §2.B) and by the
 * geofence validator (spec §2.C) for the circular-geofence case. Polygonal
 * geofences are validated in Postgres via PostGIS ST_Contains instead (see
 * migration V1, shifts.site_polygon) since ray-casting an arbitrary polygon
 * in application code duplicates logic Postgres already does correctly and
 * with an index.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtils() {
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_M * c;
    }

    public record BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {
    }

    /** Coarse bounding box (not great-circle exact, but cheap and index-friendly) for a pre-filter query. */
    public static BoundingBox boundingBox(double lat, double lon, double radiusKm) {
        double latDelta = radiusKm / 111.32; // ~km per degree latitude
        double lonDelta = radiusKm / (111.32 * Math.max(0.1, Math.cos(Math.toRadians(lat))));
        return new BoundingBox(lat - latDelta, lat + latDelta, lon - lonDelta, lon + lonDelta);
    }
}
