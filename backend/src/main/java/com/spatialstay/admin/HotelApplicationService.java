package com.spatialstay.admin;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.hotel.HotelRepository;
import com.spatialstay.hoteloperations.HotelUserAssignment;
import com.spatialstay.hoteloperations.HotelUserAssignmentRepository;
import com.spatialstay.room.Room;
import com.spatialstay.room.RoomRepository;
import com.spatialstay.user.CurrentUser;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;
import com.spatialstay.user.UserRole;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class HotelApplicationService {

    private final HotelApplicationRepository repository;
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final HotelUserAssignmentRepository assignmentRepository;
    private final RoomRepository roomRepository;
    private final CurrentUser currentUser;
    private final AdminAuditEventRepository auditRepository;
    private final PasswordEncoder passwordEncoder;

    public HotelApplicationService(
            HotelApplicationRepository repository,
            UserRepository userRepository,
            HotelRepository hotelRepository,
            HotelUserAssignmentRepository assignmentRepository,
            RoomRepository roomRepository,
            CurrentUser currentUser,
            AdminAuditEventRepository auditRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.assignmentRepository = assignmentRepository;
        this.roomRepository = roomRepository;
        this.currentUser = currentUser;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public HotelApplicationResponse apply(
            CreateHotelApplicationRequest request
    ) {
        String email =
                request.ownerEmail()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                    "An account already exists with this email. " +
                    "Existing partners should use Hotel Partner Login."
            );
        }

        User applicant =
                userRepository.saveAndFlush(
                        new User(
                                email,
                                passwordEncoder.encode(
                                        request.password()
                                ),
                                request.ownerFirstName(),
                                request.ownerLastName()
                        )
                );

        HotelApplication application =
                repository.saveAndFlush(
                        new HotelApplication(
                                applicant,
                                request
                        )
                );

        return map(application);
    }

    @Transactional(readOnly = true)
    public List<HotelApplicationResponse> all() {
        requireAdmin();

        return repository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::map)
                .toList();
    }

    @Transactional
    public HotelApplicationResponse approve(
            Long id,
            AdminDecisionRequest request
    ) {
        User admin = requireAdmin();

        HotelApplication application =
                repository
                        .findWithApplicantById(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Hotel application was not found."
                                        )
                        );

        if (
                application.getStatus() !=
                HotelApplicationStatus.PENDING_APPROVAL
        ) {
            throw new IllegalStateException(
                    "Only pending applications can be approved."
            );
        }

        User owner =
                application.getApplicant();

        if (
                owner.getRole() !=
                UserRole.CUSTOMER
        ) {
            throw new IllegalStateException(
                    "Applicant account is no longer eligible for " +
                    "HOTEL_HEAD provisioning."
            );
        }

        Hotel hotel =
                Hotel.createForAdminOnboarding();

        hotel.setName(
                application.getHotelName()
        );

        hotel.setSlug(
                uniqueSlug(
                        application.getHotelName()
                )
        );

        hotel.setDescription(
                application.getDescription()
        );

        hotel.setCity(
                application.getCity()
        );

        hotel.setState(
                application.getState()
        );

        hotel.setCountry(
                application.getCountry()
        );

        hotel.setAddress(
                application.getAddress()
        );

        hotel.setContactEmail(
                application.getContactEmail()
        );

        hotel.setContactPhone(
                application.getContactPhone()
        );

        hotel.setStartingPrice(
                application.getStartingPrice()
        );

        /*
         * Preserve the complete hotel gallery.
         * The first uploaded image remains the cover image.
         */
        hotel.setImageUrls(
                application.getHotelImageUrls()
        );

        hotel.setHeroImageUrl(
                application
                        .getHotelImageUrls()
                        .isEmpty()
                        ? null
                        : application
                                .getHotelImageUrls()
                                .get(0)
        );

        hotel.setAmenities(
                application.getAmenities()
        );

        hotel.setCheckInTime(
                application.getCheckInTime()
        );

        hotel.setCheckOutTime(
                application.getCheckOutTime()
        );

        hotel.setHouseRules(
                application.getHouseRules()
        );

        hotel.setChildPolicy(
                application.getChildPolicy()
        );

        hotel.setPetPolicy(
                application.getPetPolicy()
        );

        hotel.setSmokingPolicy(
                application.getSmokingPolicy()
        );

        hotel.setVerified(true);
        hotel.setStatus("ACTIVE");

        hotel =
                hotelRepository.saveAndFlush(
                        hotel
                );

        int roomNumber = 1;

        for (
                HotelApplicationRoom proposal :
                application.getRooms()
        ) {
            Room room =
                    Room.createForAdminOnboarding();

            room.setHotel(hotel);

            room.setSlug(
                    uniqueRoomSlug(
                            proposal.getName(),
                            roomNumber++
                    )
            );

            room.setName(
                    proposal.getName()
            );

            room.setDescription(
                    proposal.getDescription()
            );

            room.setRoomType(
                    proposal.getRoomType()
            );

            room.setMaxGuests(
                    proposal.getMaxGuests()
            );

            room.setTotalUnits(
                    proposal.getTotalUnits()
            );

            room.setBedType(
                    proposal.getBedType()
            );

            room.setRoomSizeSqFt(
                    proposal.getRoomSizeSqFt()
            );

            room.setPricePerNight(
                    proposal.getPricePerNight()
            );

            room.setCurrency("INR");

            /*
             * Preserve the complete room gallery.
             * The first uploaded image remains the room cover.
             */
            room.setImageUrls(
                    proposal.getImageUrls()
            );

            room.setHeroImageUrl(
                    proposal
                            .getImageUrls()
                            .isEmpty()
                            ? null
                            : proposal
                                    .getImageUrls()
                                    .get(0)
            );

            room.setSpatialAvailable(false);
            room.setStatus("ACTIVE");

            roomRepository.save(room);
        }

        owner.changeRole(
                UserRole.HOTEL_HEAD
        );

        userRepository.saveAndFlush(
                owner
        );

        assignmentRepository.save(
                new HotelUserAssignment(
                        owner,
                        hotel
                )
        );

        application.approve(
                hotel,
                note(request)
        );

        repository.save(
                application
        );

        auditRepository.save(
                new AdminAuditEvent(
                        admin,
                        "HOTEL_APPLICATION_APPROVED",
                        "HOTEL_APPLICATION",
                        id.toString(),
                        "Approved application " +
                        reference(id) +
                        ", created hotel " +
                        hotel.getId() +
                        " and provisioned HOTEL_HEAD."
                )
        );

        return map(application);
    }

    @Transactional
    public HotelApplicationResponse reject(
            Long id,
            AdminDecisionRequest request
    ) {
        User admin = requireAdmin();

        HotelApplication application =
                repository
                        .findWithApplicantById(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Hotel application was not found."
                                        )
                        );

        if (
                application.getStatus() !=
                HotelApplicationStatus.PENDING_APPROVAL
        ) {
            throw new IllegalStateException(
                    "Only pending applications can be rejected."
            );
        }

        String note =
                note(request);

        if (
                note == null ||
                note.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "A rejection reason is required."
            );
        }

        application.reject(note);

        repository.save(
                application
        );

        auditRepository.save(
                new AdminAuditEvent(
                        admin,
                        "HOTEL_APPLICATION_REJECTED",
                        "HOTEL_APPLICATION",
                        id.toString(),
                        note
                )
        );

        return map(application);
    }

    private User requireAdmin() {
        User user =
                userRepository
                        .findByPublicId(
                                currentUser.publicId()
                        )
                        .orElseThrow();

        if (
                user.getRole() !=
                UserRole.ADMIN
        ) {
            throw new AccessDeniedException(
                    "ADMIN access is required."
            );
        }

        return user;
    }

    private String uniqueSlug(
            String name
    ) {
        String base =
                slug(name);

        String candidate =
                base;

        int number = 2;

        while (
                hotelRepository.existsBySlug(
                        candidate
                )
        ) {
            candidate =
                    base +
                    "-" +
                    number++;
        }

        return candidate;
    }

    private String uniqueRoomSlug(
            String name,
            int fallback
    ) {
        String value =
                slug(name);

        return value.isBlank()
                ? "room-" + fallback
                : value + "-" + fallback;
    }

    private String slug(
            String value
    ) {
        String result =
                Normalizer
                        .normalize(
                                value,
                                Normalizer.Form.NFD
                        )
                        .replaceAll(
                                "\\p{M}",
                                ""
                        )
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^a-z0-9]+",
                                "-"
                        )
                        .replaceAll(
                                "(^-|-$)",
                                ""
                        );

        return result.isBlank()
                ? "hotel"
                : result;
    }

    private String note(
            AdminDecisionRequest request
    ) {
        return request == null ||
               request.note() == null ||
               request.note().isBlank()
                ? null
                : request.note().trim();
    }

    private String reference(
            Long id
    ) {
        return "SS-HA-" +
                String.format(
                        "%06d",
                        id
                );
    }

    private HotelApplicationResponse map(
            HotelApplication application
    ) {
        User user =
                application.getApplicant();

        var rooms =
                application
                        .getRooms()
                        .stream()
                        .map(
                                room ->
                                        new HotelApplicationResponse.RoomProposal(
                                                room.getId(),
                                                room.getName(),
                                                room.getRoomType(),
                                                room.getDescription(),
                                                room.getMaxGuests(),
                                                room.getTotalUnits(),
                                                room.getBedType(),
                                                room.getRoomSizeSqFt(),
                                                room.getPricePerNight(),
                                                List.copyOf(
                                                        room.getImageUrls()
                                                )
                                        )
                        )
                        .toList();

        return new HotelApplicationResponse(
                application.getId(),
                reference(
                        application.getId()
                ),
                user.getPublicId(),
                (
                        user.getFirstName() +
                        " " +
                        user.getLastName()
                ).trim(),
                user.getEmail(),
                application.getOwnerPhone(),
                application.getHotelName(),
                application.getPropertyType(),
                application.getStarCategory(),
                application.getCity(),
                application.getState(),
                application.getCountry(),
                application.getAddress(),
                application.getContactPhone(),
                application.getContactEmail(),
                application.getWebsite(),
                application.getStartingPrice(),
                application.getDescription(),
                application.getTotalRooms(),
                application.getCheckInTime(),
                application.getCheckOutTime(),
                application.getHouseRules(),
                application.getChildPolicy(),
                application.getPetPolicy(),
                application.getSmokingPolicy(),
                Set.copyOf(
                        application.getAmenities()
                ),
                List.copyOf(
                        application.getHotelImageUrls()
                ),
                rooms,
                application.getStatus(),
                application.getAdminNote(),
                application.getApprovedHotel() == null
                        ? null
                        : application
                                .getApprovedHotel()
                                .getId(),
                application.getCreatedAt(),
                application.getDecidedAt()
        );
    }
}