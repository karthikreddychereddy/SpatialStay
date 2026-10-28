package com.spatialstay.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record CreateHotelApplicationRequest(
        @NotBlank @Size(max=100) String ownerFirstName,
        @NotBlank @Size(max=100) String ownerLastName,
        @NotBlank @Email @Size(max=320) String ownerEmail,
        @NotBlank @Size(min=8,max=72) String password,
        @NotBlank @Size(max=40) String ownerPhone,
        @NotBlank @Size(max=200) String hotelName,
        @NotBlank @Size(max=80) String propertyType,
        @Min(1) @Max(5) Integer starCategory,
        @NotBlank @Size(max=120) String city,
        @Size(max=120) String state,
        @NotBlank @Size(max=120) String country,
        @NotBlank @Size(max=500) String address,
        @NotBlank @Size(max=40) String contactPhone,
        @NotBlank @Email @Size(max=254) String contactEmail,
        @Size(max=300) String website,
        @NotNull @DecimalMin("0.01") BigDecimal startingPrice,
        @Size(max=10000) String description,
        @Min(1) Integer totalRooms,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        @Size(max=5000) String houseRules,
        @Size(max=3000) String childPolicy,
        @Size(max=3000) String petPolicy,
        @Size(max=3000) String smokingPolicy,
        @Size(max=40) Set<@Size(max=120) String> amenities,
        @NotEmpty @Size(max=12) List<@Size(max=1000) String> hotelImageUrls,
        @NotEmpty @Size(max=20) List<@Valid ProposedRoom> rooms
) {
    public record ProposedRoom(
            @NotBlank @Size(max=200) String name,
            @NotBlank @Size(max=80) String roomType,
            @Size(max=5000) String description,
            @Min(1) @Max(20) Integer maxGuests,
            @Min(1) Integer totalUnits,
            @NotBlank @Size(max=100) String bedType,
            @Min(1) Integer roomSizeSqFt,
            @NotNull @DecimalMin("0.01") BigDecimal pricePerNight,
            @NotEmpty @Size(max=8) List<@Size(max=1000) String> imageUrls
    ) {}
}
