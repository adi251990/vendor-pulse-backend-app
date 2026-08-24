package com.vendorpulse.platform.attendance.service;

import com.vendorpulse.platform.config.GeofenceProperties;
import com.vendorpulse.platform.shift.entity.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeofenceServiceTest {

    private GeofenceProperties properties;
    private GeofenceService geofenceService;

    @BeforeEach
    void setUp() {
        properties = new GeofenceProperties();
        properties.setAccuracyBufferM(50.0);
        geofenceService = new GeofenceService(properties);
    }

    @Test
    void testInsideGeofenceRadius() {
        Shift shift = Shift.builder()
                .siteLat(34.0522)
                .siteLon(-118.2437)
                .geofenceRadiusM(150)
                .build();

        // Exact location
        var result = geofenceService.check(shift, 34.0522, -118.2437, 10.0);
        assertTrue(result.withinGeofence());
        assertTrue(result.distanceMeters() < 1.0);
    }

    @Test
    void testWithinGpsDriftTolerance() {
        Shift shift = Shift.builder()
                .siteLat(34.0522)
                .siteLon(-118.2437)
                .geofenceRadiusM(150)
                .build();

        // ~170 meters away (outside 150m radius), but accuracy reported is 30m
        // allowed buffer = min(30, 50) = 30m => total allowance = 180m => inside drift tolerance
        // 0.0015 deg lat ~ 166 meters
        double testLat = 34.0522 + (170.0 / 111320.0);
        var result = geofenceService.check(shift, testLat, -118.2437, 30.0);

        assertTrue(result.withinGeofence());
    }

    @Test
    void testOutsideGeofenceAndDriftTolerance() {
        Shift shift = Shift.builder()
                .siteLat(34.0522)
                .siteLon(-118.2437)
                .geofenceRadiusM(150)
                .build();

        // ~500 meters away
        double testLat = 34.0522 + (500.0 / 111320.0);
        var result = geofenceService.check(shift, testLat, -118.2437, 10.0);

        assertFalse(result.withinGeofence());
    }
}

