package com.hireme.platform.identity.repository;

import com.hireme.platform.identity.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
}
