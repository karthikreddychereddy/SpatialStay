package com.spatialstay.hoteloperations;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotel-operations")
public class HotelOperationsDashboardController {

    private final HotelOperationsDashboardService dashboardService;

    public HotelOperationsDashboardController(
            HotelOperationsDashboardService dashboardService
    ) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public HotelOperationsDashboardResponse dashboard(
            @RequestParam(required = false) Long hotelId
    ) {
        return dashboardService.getDashboard(hotelId);
    }
}
