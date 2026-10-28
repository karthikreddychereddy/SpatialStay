package com.spatialstay.admin;
import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/hotel-applications")
public class HotelApplicationController{
 private final HotelApplicationService service; public HotelApplicationController(HotelApplicationService service){this.service=service;}
 @PostMapping public ResponseEntity<HotelApplicationResponse> apply(@Valid @RequestBody CreateHotelApplicationRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(request));}
}
