package com.hireme.platform.attendance.repository;

import com.hireme.platform.attendance.entity.GeoEventType;
import com.hireme.platform.attendance.entity.GeoLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeoLogRepository extends JpaRepository<GeoLog, Long> {

    List<GeoLog> findByBookingIdOrderByRecordedAtAsc(UUID bookingId);

    Optional<GeoLog> findFirstByBookingIdAndEventTypeOrderByRecordedAtDesc(UUID bookingId, GeoEventType eventType);

    boolean existsByBookingIdAndEventType(UUID bookingId, GeoEventType eventType);
}
