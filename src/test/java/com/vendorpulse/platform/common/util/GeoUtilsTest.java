package com.vendorpulse.platform.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    @Test
    void testDistanceMetersSamePoint() {
        double dist = GeoUtils.distanceMeters(34.0522, -118.2437, 34.0522, -118.2437);
        assertEquals(0.0, dist, 0.001);
    }

    @Test
    void testDistanceMetersKnownDistance() {
        // Los Angeles (34.0522, -118.2437) to New York (40.7128, -74.0060) is ~3935 km (~3,935,000 meters)
        double dist = GeoUtils.distanceMeters(34.0522, -118.2437, 40.7128, -74.0060);
        assertTrue(dist > 3_900_000 && dist < 4_000_000);
    }

    @Test
    void testBoundingBox() {
        double lat = 34.0522;
        double lon = -118.2437;
        double radiusKm = 25.0;

        GeoUtils.BoundingBox box = GeoUtils.boundingBox(lat, lon, radiusKm);

        assertTrue(box.minLat() < lat);
        assertTrue(box.maxLat() > lat);
        assertTrue(box.minLon() < lon);
        assertTrue(box.maxLon() > lon);
    }
}

