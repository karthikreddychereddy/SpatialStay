package com.spatialstay.hoteloperations;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/hotel-operations/financial-overview")
public class HotelFinancialOverviewController {

    private final HotelFinancialOverviewService service;

    public HotelFinancialOverviewController(HotelFinancialOverviewService service) {
        this.service = service;
    }

    @GetMapping
    public HotelFinancialOverviewResponse get(
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return service.get(hotelId, from, to);
    }
}
