package com.spatialstay.hoteloperations;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestController
@RequestMapping("/api/hotel-operations/reservations")
public class HotelReservationController {
    private final HotelReservationService reservationService;
    private final HotelStayOperationService stayOperationService;

    public HotelReservationController(
            HotelReservationService reservationService,
            HotelStayOperationService stayOperationService
    ) {
        this.reservationService = reservationService;
        this.stayOperationService = stayOperationService;
    }

    @GetMapping
    public List<HotelReservationResponse> reservations(
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false, defaultValue = "ALL") HotelReservationView view,
            @RequestParam(required = false) String search
    ) {
        return reservationService.list(hotelId, view, search);
    }

    @GetMapping("/{bookingId}")
    public HotelReservationResponse reservation(
            @PathVariable UUID bookingId,
            @RequestParam(required = false) Long hotelId
    ) {
        return reservationService.get(hotelId, bookingId);
    }

    @PostMapping("/{bookingId}/check-in")
    public HotelStayOperationResponse checkIn(
            @PathVariable UUID bookingId,
            @RequestParam(required = false) Long hotelId
    ) {
        return stayOperationService.checkIn(hotelId, bookingId);
    }

    @PostMapping("/{bookingId}/check-out")
    public HotelStayOperationResponse checkOut(
            @PathVariable UUID bookingId,
            @RequestParam(required = false) Long hotelId
    ) {
        return stayOperationService.checkOut(hotelId, bookingId);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {}

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(
            IllegalStateException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "status", HttpStatus.CONFLICT.value(),
                        "error", "Conflict",
                        "message", ex.getMessage()
                ));
    }
}
