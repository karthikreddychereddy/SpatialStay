package com.spatialstay.hoteloperations;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.hotel.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class HotelProfileManagementService {

    private final HotelOperationsAccessService accessService;
    private final HotelRepository hotelRepository;
    private final HotelAuditService auditService;

    public HotelProfileManagementService(
            HotelOperationsAccessService accessService,
            HotelRepository hotelRepository,
            HotelAuditService auditService
    ) {
        this.accessService = accessService;
        this.hotelRepository = hotelRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public HotelProfileResponse get(Long requestedHotelId) {
        return toResponse(accessService.resolveAssignedHotel(requestedHotelId));
    }

    @Transactional
    public HotelProfileResponse updateProfile(Long requestedHotelId, UpdateHotelProfileRequest request) {
        accessService.requireHotelHead();
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        if (request == null) throw new IllegalArgumentException("Hotel profile is required.");

        if (request.name() != null) hotel.setName(required(request.name(), "Hotel name", 200));
        if (request.description() != null) hotel.setDescription(optionalText(request.description(), 10000, "Description"));
        if (request.city() != null) hotel.setCity(required(request.city(), "City", 120));
        if (request.state() != null) hotel.setState(optionalText(request.state(), 120, "State"));
        if (request.country() != null) hotel.setCountry(required(request.country(), "Country", 120));
        if (request.address() != null) hotel.setAddress(optionalText(request.address(), 500, "Address"));
        if (request.latitude() != null) hotel.setLatitude(latitude(request.latitude()));
        if (request.longitude() != null) hotel.setLongitude(longitude(request.longitude()));
        if (request.heroImageUrl() != null) hotel.setHeroImageUrl(optionalText(request.heroImageUrl(), 1000, "Hero image URL"));
        if (request.contactEmail() != null) hotel.setContactEmail(email(request.contactEmail()));
        if (request.contactPhone() != null) hotel.setContactPhone(optionalText(request.contactPhone(), 40, "Contact phone"));

        Hotel saved = hotelRepository.saveAndFlush(hotel);
        auditService.record(saved, "HOTEL_PROFILE_UPDATED", "HOTEL", saved.getId().toString(), "Hotel public/operational profile updated.");
        return toResponse(saved);
    }

    @Transactional
    public HotelProfileResponse updateAmenities(Long requestedHotelId, UpdateHotelAmenitiesRequest request) {
        accessService.requireHotelHead();
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        if (request == null || request.amenities() == null) {
            throw new IllegalArgumentException("Amenities are required; send an empty list to clear them.");
        }
        if (request.amenities().size() > 100) {
            throw new IllegalArgumentException("A hotel can have at most 100 amenities.");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String amenity : request.amenities()) {
            String value = required(amenity, "Amenity", 120);
            normalized.add(value);
        }
        hotel.setAmenities(normalized);
        Hotel saved = hotelRepository.saveAndFlush(hotel);
        auditService.record(saved, "HOTEL_AMENITIES_UPDATED", "HOTEL", saved.getId().toString(), "Hotel amenities updated.");
        return toResponse(saved);
    }

    @Transactional
    public HotelProfileResponse updatePolicies(Long requestedHotelId, UpdateHotelPoliciesRequest request) {
        accessService.requireHotelHead();
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        if (request == null) throw new IllegalArgumentException("Hotel policies are required.");

        if (request.checkInTime() != null) hotel.setCheckInTime(request.checkInTime());
        if (request.checkOutTime() != null) hotel.setCheckOutTime(request.checkOutTime());
        if (request.checkInInstructions() != null) hotel.setCheckInInstructions(optionalText(request.checkInInstructions(), 5000, "Check-in instructions"));
        if (request.houseRules() != null) hotel.setHouseRules(optionalText(request.houseRules(), 10000, "House rules"));
        if (request.childPolicy() != null) hotel.setChildPolicy(optionalText(request.childPolicy(), 5000, "Child policy"));
        if (request.petPolicy() != null) hotel.setPetPolicy(optionalText(request.petPolicy(), 5000, "Pet policy"));
        if (request.smokingPolicy() != null) hotel.setSmokingPolicy(optionalText(request.smokingPolicy(), 5000, "Smoking policy"));

        Hotel saved = hotelRepository.saveAndFlush(hotel);
        auditService.record(saved, "HOTEL_POLICIES_UPDATED", "HOTEL", saved.getId().toString(), "Hotel operational policies updated.");
        return toResponse(saved);
    }

    private HotelProfileResponse toResponse(Hotel hotel) {
        return new HotelProfileResponse(
                hotel.getId(), hotel.getSlug(), hotel.getName(), hotel.getDescription(),
                hotel.getCity(), hotel.getState(), hotel.getCountry(), hotel.getAddress(),
                hotel.getLatitude(), hotel.getLongitude(), hotel.getHeroImageUrl(),
                hotel.getContactEmail(), hotel.getContactPhone(), hotel.getCheckInTime(),
                hotel.getCheckOutTime(), hotel.getCheckInInstructions(), Set.copyOf(hotel.getAmenities()),
                hotel.getHouseRules(), hotel.getChildPolicy(), hotel.getPetPolicy(),
                hotel.getSmokingPolicy(), hotel.getVerified(), hotel.getStatus(), hotel.getUpdatedAt());
    }

    private String required(String value, String field, int max) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank.");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long.");
        return normalized;
    }

    private String optionalText(String value, int max, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized != null && normalized.length() > max) throw new IllegalArgumentException(field + " is too long.");
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    private String email(String value) {
        String normalized = optionalText(value, 254, "Contact email");
        if (normalized != null && !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Contact email is invalid.");
        }
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private BigDecimal latitude(BigDecimal value) {
        if (value.compareTo(new BigDecimal("-90")) < 0 || value.compareTo(new BigDecimal("90")) > 0)
            throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        return value;
    }

    private BigDecimal longitude(BigDecimal value) {
        if (value.compareTo(new BigDecimal("-180")) < 0 || value.compareTo(new BigDecimal("180")) > 0)
            throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        return value;
    }
}
