package com.vendorpulse.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VendorPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(VendorPulseApplication.class, args);
    }
}
