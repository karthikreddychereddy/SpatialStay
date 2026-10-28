package com.spatialstay.admin;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/hotel-applications")
public class AdminHotelApplicationController {

    private final HotelApplicationService service;

    public AdminHotelApplicationController(
            HotelApplicationService service
    ) {
        this.service = service;
    }

    @GetMapping
    public List<HotelApplicationResponse> all() {
        return service.all();
    }

    @PostMapping("/{id}/approve")
    public HotelApplicationResponse approve(
            @PathVariable Long id,
            @Valid
            @RequestBody(required = false)
            AdminDecisionRequest request
    ) {
        return service.approve(
                id,
                request
        );
    }

    @PostMapping("/{id}/reject")
    public HotelApplicationResponse reject(
            @PathVariable Long id,
            @Valid
            @RequestBody(required = false)
            AdminDecisionRequest request
    ) {
        return service.reject(
                id,
                request
        );
    }
}