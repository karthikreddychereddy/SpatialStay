package com.spatialstay.hoteloperations;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/hotel-operations/rooms")
public class HotelRoomManagementController {

    private final HotelRoomManagementService service;

    public HotelRoomManagementController(HotelRoomManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<HotelRoomManagementResponse> list(
            @RequestParam(required = false) Long hotelId
    ) {
        return service.list(hotelId);
    }

    @GetMapping("/{roomId}")
    public HotelRoomManagementResponse get(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long hotelId
    ) {
        return service.get(hotelId, roomId);
    }

    @PatchMapping("/{roomId}/commercial")
    public HotelRoomManagementResponse updateCommercialConfiguration(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long hotelId,
            @RequestBody UpdateHotelRoomCommercialRequest request
    ) {
        return service.updateCommercialConfiguration(hotelId, roomId, request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }
}
