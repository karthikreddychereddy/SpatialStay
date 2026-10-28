package com.spatialstay.hoteloperations;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HotelAuditEventRepository extends JpaRepository<HotelAuditEvent, Long> {
    List<HotelAuditEvent> findByHotelIdOrderByOccurredAtDescIdDesc(Long hotelId, Pageable pageable);
}
