package com.spatialstay.hotel;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;
    private final HotelMapper hotelMapper;

    public HotelController(
            HotelService hotelService,
            HotelMapper hotelMapper
    ) {
        this.hotelService =
                hotelService;

        this.hotelMapper =
                hotelMapper;
    }

    @GetMapping
    public List<HotelResponse> searchHotels(
            @RequestParam(required = false)
            String destination,

            @RequestParam(required = false)
            String query,

            @RequestParam(required = false)
            BigDecimal minPrice,

            @RequestParam(required = false)
            BigDecimal maxPrice,

            @RequestParam(required = false)
            BigDecimal minRating,

            @RequestParam(required = false)
            Boolean verified,

            @RequestParam(
                    required = false,
                    defaultValue = "recommended"
            )
            String sort,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso =
                    DateTimeFormat.ISO.DATE
            )
            LocalDate checkIn,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso =
                    DateTimeFormat.ISO.DATE
            )
            LocalDate checkOut,

            @RequestParam(required = false)
            Integer guests
    ) {
        HotelSearch search =
                new HotelSearch(
                        destination,
                        query,
                        minPrice,
                        maxPrice,
                        minRating,
                        verified,
                        sort,
                        checkIn,
                        checkOut,
                        guests
                );

        return hotelService.searchHotelResponses(
                        search
                );
    }

    @GetMapping("/{slug}")
    public ResponseEntity<HotelResponse>
    getHotelBySlug(
            @PathVariable
            String slug
    ) {
        return hotelService
                .getHotelResponseBySlug(slug)
                .map(
                        ResponseEntity::ok
                )
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
    public ResponseEntity<
            Map<String, String>
            >
    handleInvalidSearch(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .status(
                        HttpStatus.BAD_REQUEST
                )
                .body(
                        Map.of(
                                "error",
                                "INVALID_HOTEL_SEARCH",
                                "message",
                                exception.getMessage()
                        )
                );
    }
}