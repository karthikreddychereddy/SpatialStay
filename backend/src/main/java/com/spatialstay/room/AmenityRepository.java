package com.spatialstay.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AmenityRepository extends JpaRepository<Amenity, Long> {

    Optional<Amenity> findByCode(String code);
}