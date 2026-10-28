package com.spatialstay.hoteloperations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HotelStayRepository extends JpaRepository<HotelStay, Long> {
    Optional<HotelStay> findByBookingPublicId(UUID bookingPublicId);
    List<HotelStay> findAllByBookingPublicIdIn(Collection<UUID> bookingPublicIds);
}
