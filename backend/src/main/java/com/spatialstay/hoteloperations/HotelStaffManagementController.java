package com.spatialstay.hoteloperations;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/hotel-operations/staff")
public class HotelStaffManagementController {

    private final HotelStaffManagementService service;

    public HotelStaffManagementController(HotelStaffManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<HotelStaffResponse> list(@RequestParam(required = false) Long hotelId) {
        return service.list(hotelId);
    }

    @PostMapping
    public ResponseEntity<HotelStaffResponse> create(
            @RequestParam(required = false) Long hotelId,
            @RequestBody CreateHotelStaffRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(hotelId, request));
    }

    @PostMapping("/{staffUserId}/deactivate")
    public HotelStaffResponse deactivate(
            @RequestParam(required = false) Long hotelId,
            @PathVariable UUID staffUserId
    ) {
        return service.deactivate(hotelId, staffUserId);
    }

    @PostMapping("/{staffUserId}/reactivate")
    public HotelStaffResponse reactivate(
            @RequestParam(required = false) Long hotelId,
            @PathVariable UUID staffUserId
    ) {
        return service.reactivate(hotelId, staffUserId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }
}
