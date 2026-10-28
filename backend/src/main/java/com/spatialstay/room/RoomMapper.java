package com.spatialstay.room;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RoomMapper {

    public RoomResponse toResponse(
            Room room
    ) {
        var amenityResponses =
                room.getAmenities()
                        .stream()
                        .map(
                                amenity ->
                                        new RoomResponse.AmenityResponse(
                                                amenity.getCode(),
                                                amenity.getName()
                                        )
                        )
                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        return new RoomResponse(
                room.getId(),
                room.getSlug(),
                room.getName(),
                room.getDescription(),
                room.getRoomType(),
                room.getMaxGuests(),
                room.getBedType(),
                room.getRoomSizeSqFt(),
                room.getPricePerNight(),
                room.getCurrency(),
                room.getHeroImageUrl(),
                List.copyOf(
                        room.getImageUrls()
                ),
                room.getSpatialAvailable(),
                room.getStatus(),
                room.getHotel().getId(),
                room.getHotel().getSlug(),
                room.getHotel().getName(),
                amenityResponses,
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}