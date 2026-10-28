package com.spatialstay.admin;
import org.springframework.data.jpa.repository.*; import java.util.*;
public interface HotelApplicationRepository extends JpaRepository<HotelApplication,Long>{
 long countByStatus(HotelApplicationStatus status);
 boolean existsByApplicantPublicIdAndStatus(java.util.UUID publicId,HotelApplicationStatus status);
 @EntityGraph(attributePaths={"applicant","approvedHotel","hotelImageUrls","amenities","rooms","rooms.imageUrls"}) List<HotelApplication> findAllByOrderByCreatedAtDesc();
 @EntityGraph(attributePaths={"applicant","approvedHotel","hotelImageUrls","amenities","rooms","rooms.imageUrls"}) Optional<HotelApplication> findWithApplicantById(Long id);
}
