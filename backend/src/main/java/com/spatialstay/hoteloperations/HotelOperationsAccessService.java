package com.spatialstay.hoteloperations;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.user.CurrentUser;
import com.spatialstay.user.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HotelOperationsAccessService {

    private final CurrentUser currentUser;
    private final HotelUserAssignmentRepository assignmentRepository;

    public HotelOperationsAccessService(
            CurrentUser currentUser,
            HotelUserAssignmentRepository assignmentRepository
    ) {
        this.currentUser = currentUser;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<HotelUserAssignment> requireAndGetAssignments() {
        UserRole role = currentUser.role();

        if (role != UserRole.HOTEL_STAFF
                && role != UserRole.HOTEL_HEAD
                && role != UserRole.ADMIN) {
            throw new AccessDeniedException("Hotel operations access is required.");
        }

        if (role == UserRole.ADMIN) {
            // Admin platform-wide behavior is implemented in Step 12B, not here.
            return List.of();
        }

        List<HotelUserAssignment> assignments =
                assignmentRepository.findActiveAssignments(currentUser.publicId());

        if (assignments.isEmpty()) {
            throw new AccessDeniedException("No active hotel assignment exists for this account.");
        }

        return assignments;
    }

    @Transactional(readOnly = true)
    public Hotel resolveAssignedHotel(Long requestedHotelId) {
        List<HotelUserAssignment> assignments = requireAndGetAssignments();

        if (requestedHotelId == null) {
            if (assignments.size() == 1) {
                return assignments.get(0).getHotel();
            }
            throw new IllegalArgumentException(
                    "hotelId is required when an operator is assigned to multiple hotels."
            );
        }

        return assignments.stream()
                .filter(assignment -> assignment.getHotel().getId().equals(requestedHotelId))
                .map(HotelUserAssignment::getHotel)
                .findFirst()
                .orElseThrow(() ->
                        new AccessDeniedException("You are not assigned to this hotel.")
                );
    }

    @Transactional(readOnly = true)
    public void requireHotelAccess(Long hotelId) {
        UserRole role = currentUser.role();

        if (role == UserRole.ADMIN) {
            return;
        }

        if (role != UserRole.HOTEL_STAFF && role != UserRole.HOTEL_HEAD) {
            throw new AccessDeniedException("Hotel operations access is required.");
        }

        boolean allowed = assignmentRepository.hasActiveHotelAccess(
                currentUser.publicId(),
                hotelId
        );

        if (!allowed) {
            throw new AccessDeniedException("You are not assigned to this hotel.");
        }
    }

    public void requireHotelHead() {
        UserRole role = currentUser.role();
        if (role != UserRole.HOTEL_HEAD && role != UserRole.ADMIN) {
            throw new AccessDeniedException("HOTEL_HEAD access is required.");
        }
    }
}
