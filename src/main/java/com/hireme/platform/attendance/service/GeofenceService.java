package com.hireme.platform.attendance.service;

import com.hireme.platform.common.util.GeoUtils;
import com.hireme.platform.config.GeofenceProperties;
import com.hireme.platform.shift.entity.Shift;
import org.springframework.stereotype.Service;

/**
 * Circular-geofence validator with GPS-drift tolerance (spec §2.C). Sites
 * that need an irregular polygon boundary instead of a circle store
 * shifts.site_polygon (PostGIS GEOGRAPHY(POLYGON)) and should be validated
 * with a native {@code ST_Contains} query rather than this class - polygon
 * ray-casting in application code would just re-implement what PostGIS
 * already does correctly, with an index. This service handles the circular
 * case, which covers the large majority of single-site shifts.
 */
@Service
public class GeofenceService {

    private final GeofenceProperties properties;

    public GeofenceService(GeofenceProperties properties) {
        this.properties = properties;
    }

    public GeofenceCheck check(Shift shift, double lat, double lon, double accuracyM) {
        double distance = GeoUtils.distanceMeters(shift.getSiteLat(), shift.getSiteLon(), lat, lon);
        double radius = shift.getGeofenceRadiusM();

        if (distance <= radius) {
            return new GeofenceCheck(true, distance, false);
        }

        // Drift tolerance: accept slightly-outside readings when the device itself
        // reports low GPS accuracy, up to a capped buffer. A production build should
        // additionally require two consecutive readings ~5s apart that both land in
        // tolerance before committing the clock event, to blunt single-ping spoofing;
        // that requires call-to-call state (e.g. a short-lived Redis pending-reading
        // key) which is out of scope for this pass but noted here for the next iteration.
        double allowedBuffer = Math.min(accuracyM, properties.getAccuracyBufferM());
        boolean withinDrift = distance <= radius + allowedBuffer;

        return new GeofenceCheck(withinDrift, distance, false);
    }

    public record GeofenceCheck(boolean withinGeofence, double distanceMeters, boolean flaggedForReview) {
    }
}
