package com.spatialstay.hoteloperations;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/hotel-operations/profile")
public class HotelProfileController {

    private final HotelProfileManagementService profileService;

    public HotelProfileController(
            HotelProfileManagementService profileService
    ) {
        this.profileService = profileService;
    }

    @GetMapping
    public HotelProfileResponse getProfile(
            @RequestParam(required = false) Long hotelId
    ) {
        return profileService.get(hotelId);
    }

    @PatchMapping
    public HotelProfileResponse updateProfile(
            @RequestParam(required = false) Long hotelId,
            @RequestBody UpdateHotelProfileRequest request
    ) {
        return profileService.updateProfile(
                hotelId,
                request
        );
    }

    @PutMapping("/amenities")
    public HotelProfileResponse updateAmenities(
            @RequestParam(required = false) Long hotelId,
            @RequestBody UpdateHotelAmenitiesRequest request
    ) {
        return profileService.updateAmenities(
                hotelId,
                request
        );
    }

    @PatchMapping("/policies")
    public HotelProfileResponse updatePolicies(
            @RequestParam(required = false) Long hotelId,
            @RequestBody UpdateHotelPoliciesRequest request
    ) {
        return profileService.updatePolicies(
                hotelId,
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "error", "Bad Request",
                        "message", exception.getMessage()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(
            IllegalStateException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "status", HttpStatus.CONFLICT.value(),
                        "error", "Conflict",
                        "message", exception.getMessage()
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(
            AccessDeniedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "status", HttpStatus.FORBIDDEN.value(),
                        "error", "Forbidden",
                        "message", exception.getMessage()
                ));
    }
}