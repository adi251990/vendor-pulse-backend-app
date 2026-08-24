package com.vendorpulse.platform.payment.repository;

import com.vendorpulse.platform.payment.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    List<Dispute> findByResolvedAtIsNull();

    List<Dispute> findByResolvedAtIsNullAndCreatedAtBefore(Instant cutoff);
}
