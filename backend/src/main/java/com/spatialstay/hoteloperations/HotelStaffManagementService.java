package com.spatialstay.hoteloperations;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.user.PasswordPolicy;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;
import com.spatialstay.user.UserRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class HotelStaffManagementService {

    private final HotelOperationsAccessService accessService;
    private final HotelUserAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HotelAuditService auditService;

    public HotelStaffManagementService(
            HotelOperationsAccessService accessService,
            HotelUserAssignmentRepository assignmentRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            HotelAuditService auditService
    ) {
        this.accessService = accessService;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<HotelStaffResponse> list(Long requestedHotelId) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        accessService.requireHotelHead();
        return assignmentRepository.findStaffAssignmentsForHotel(hotel.getId(), UserRole.HOTEL_STAFF)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public HotelStaffResponse create(Long requestedHotelId, CreateHotelStaffRequest request) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        accessService.requireHotelHead();
        validateRequest(request);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists. Existing accounts cannot be converted to hotel staff here."
            );
        }

        PasswordPolicy.validate(request.password());
        User staff = User.createHotelOperator(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                UserRole.HOTEL_STAFF
        );

        try {
            staff = userRepository.saveAndFlush(staff);
            HotelUserAssignment assignment = assignmentRepository.saveAndFlush(
                    new HotelUserAssignment(staff, hotel)
            );
            auditService.record(hotel, "STAFF_CREATED", "USER", staff.getPublicId().toString(), "Hotel staff account created and assigned.");
            return toResponse(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("The staff account or hotel assignment already exists.");
        }
    }

    @Transactional
    public HotelStaffResponse deactivate(Long requestedHotelId, UUID staffUserId) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        accessService.requireHotelHead();
        HotelUserAssignment assignment = requireStaffAssignment(hotel.getId(), staffUserId);
        if (!assignment.isActive()) {
            throw new IllegalStateException("This staff assignment is already inactive.");
        }
        assignment.deactivate();
        auditService.record(hotel, "STAFF_DEACTIVATED", "USER", staffUserId.toString(), "Hotel staff assignment deactivated.");
        return toResponse(assignment);
    }

    @Transactional
    public HotelStaffResponse reactivate(Long requestedHotelId, UUID staffUserId) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        accessService.requireHotelHead();
        HotelUserAssignment assignment = requireStaffAssignment(hotel.getId(), staffUserId);
        if (assignment.isActive()) {
            throw new IllegalStateException("This staff assignment is already active.");
        }
        assignment.activate();
        auditService.record(hotel, "STAFF_REACTIVATED", "USER", staffUserId.toString(), "Hotel staff assignment reactivated.");
        return toResponse(assignment);
    }

    private HotelUserAssignment requireStaffAssignment(Long hotelId, UUID staffUserId) {
        return assignmentRepository.findStaffAssignment(hotelId, staffUserId, UserRole.HOTEL_STAFF)
                .orElseThrow(() -> new NoSuchElementException("Hotel staff assignment was not found."));
    }

    private void validateRequest(CreateHotelStaffRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Staff email is required.");
        }
        if (request.firstName() == null || request.firstName().isBlank()) {
            throw new IllegalArgumentException("Staff first name is required.");
        }
        if (request.lastName() == null || request.lastName().isBlank()) {
            throw new IllegalArgumentException("Staff last name is required.");
        }
    }

    private HotelStaffResponse toResponse(HotelUserAssignment assignment) {
        User user = assignment.getUser();
        return new HotelStaffResponse(
                user.getPublicId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getStatus(), assignment.isActive(), assignment.getCreatedAt(), assignment.getUpdatedAt()
        );
    }
}
