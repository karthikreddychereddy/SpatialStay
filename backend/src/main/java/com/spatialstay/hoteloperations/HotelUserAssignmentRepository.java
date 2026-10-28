package com.spatialstay.hoteloperations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HotelUserAssignmentRepository
        extends JpaRepository<HotelUserAssignment, Long> {

    @Query("""
            select a
            from HotelUserAssignment a
            join fetch a.hotel h
            where a.user.publicId = :userPublicId
              and a.active = true
            order by h.name asc
            """)
    List<HotelUserAssignment> findActiveAssignments(
            @Param("userPublicId") UUID userPublicId
    );

    @Query("""
            select count(a) > 0
            from HotelUserAssignment a
            where a.user.publicId = :userPublicId
              and a.hotel.id = :hotelId
              and a.active = true
            """)
    boolean hasActiveHotelAccess(
            @Param("userPublicId") UUID userPublicId,
            @Param("hotelId") Long hotelId
    );
    @Query("""
            select a
            from HotelUserAssignment a
            join fetch a.user u
            join fetch a.hotel h
            where h.id = :hotelId
              and u.role = :role
            order by u.firstName asc, u.lastName asc, u.email asc
            """)
    List<HotelUserAssignment> findStaffAssignmentsForHotel(
            @Param("hotelId") Long hotelId,
            @Param("role") com.spatialstay.user.UserRole role
    );

    @Query("""
            select a
            from HotelUserAssignment a
            join fetch a.user u
            join fetch a.hotel h
            where h.id = :hotelId
              and u.publicId = :userPublicId
              and u.role = :role
            """)
    java.util.Optional<HotelUserAssignment> findStaffAssignment(
            @Param("hotelId") Long hotelId,
            @Param("userPublicId") UUID userPublicId,
            @Param("role") com.spatialstay.user.UserRole role
    );

}
