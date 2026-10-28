package com.spatialstay.hotel;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class HotelMapper {

    public HotelResponse toResponse(
            Hotel hotel
    ) {
        return new HotelResponse(
                hotel.getId(),
                hotel.getSlug(),
                hotel.getName(),
                hotel.getDescription(),
                hotel.getCity(),
                hotel.getState(),
                hotel.getCountry(),
                hotel.getAddress(),
                hotel.getLatitude(),
                hotel.getLongitude(),
                hotel.getRating(),
                hotel.getReviewCount(),
                hotel.getStartingPrice(),
                hotel.getHeroImageUrl(),
                List.copyOf(
                        hotel.getImageUrls()
                ),
                hotel.getVerified(),
                hotel.getStatus(),
                hotel.getContactEmail(),
                hotel.getContactPhone(),
                hotel.getCheckInTime(),
                hotel.getCheckOutTime(),
                hotel.getCheckInInstructions(),
                Set.copyOf(
                        hotel.getAmenities()
                ),
                hotel.getHouseRules(),
                hotel.getChildPolicy(),
                hotel.getPetPolicy(),
                hotel.getSmokingPolicy(),
                hotel.getCreatedAt(),
                hotel.getUpdatedAt()
        );
    }
}