package com.spatialstay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpatialStayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpatialStayApplication.class, args);
    }
}