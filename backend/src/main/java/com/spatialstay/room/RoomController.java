package com.spatialstay.room;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hotels/{hotelSlug}/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(
            RoomService roomService
    ) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> getRoomsByHotel(
            @PathVariable String hotelSlug
    ) {
        return roomService
                .getRoomsByHotel(hotelSlug);
    }

    @GetMapping("/availability")
    public List<RoomAvailabilityResponse> getAvailability(
            @PathVariable String hotelSlug,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate checkIn,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate checkOut,

            @RequestParam
            Integer guests
    ) {
        return roomService.getAvailability(
                hotelSlug,
                checkIn,
                checkOut,
                guests
        );
    }

    @GetMapping("/{roomSlug}")
    public ResponseEntity<RoomResponse> getRoom(
            @PathVariable String hotelSlug,
            @PathVariable String roomSlug
    ) {
        return roomService
                .getRoomByHotelAndSlug(
                        hotelSlug,
                        roomSlug
                )
                .map(ResponseEntity::ok)
                .orElseGet(
                        () ->
                                ResponseEntity
                                        .notFound()
                                        .build()
                );
    }

    @ExceptionHandler(
            IllegalArgumentException.class
    )
    public ResponseEntity<Map<String, String>>
    handleInvalidAvailability(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        Map.of(
                                "error",
                                "INVALID_STAY_SEARCH",
                                "message",
                                exception.getMessage()
                        )
                );
    }
}