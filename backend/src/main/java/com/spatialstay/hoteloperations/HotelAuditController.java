package com.spatialstay.hoteloperations;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/hotel-operations/audit")
public class HotelAuditController {
    private final HotelAuditService service;
    public HotelAuditController(HotelAuditService service) { this.service = service; }

    @GetMapping
    public List<HotelAuditEventResponse> list(@RequestParam(required = false) Long hotelId,
                                               @RequestParam(required = false) Integer limit) {
        return service.list(hotelId, limit);
    }
}
