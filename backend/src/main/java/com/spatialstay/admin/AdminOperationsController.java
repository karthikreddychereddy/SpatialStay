package com.spatialstay.admin;

import com.spatialstay.user.UserStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminOperationsController {
    private final AdminOperationsService service;
    public AdminOperationsController(AdminOperationsService service){this.service=service;}

    @GetMapping("/hotels") public Object hotels(){return service.hotels();}
    @PatchMapping("/hotels/{id}/status") public Object hotelStatus(@PathVariable Long id,@RequestBody StatusRequest request){return service.setHotelStatus(id,request.status());}
    @GetMapping("/users") public Object users(){return service.users();}
    @PatchMapping("/users/{id}/status") public Object userStatus(@PathVariable UUID id,@RequestBody StatusRequest request){return service.setUserStatus(id,UserStatus.valueOf(request.status().trim().toUpperCase()));}
    @GetMapping("/bookings") public Object bookings(){return service.bookings();}
    @GetMapping("/payments") public Object payments(){return service.payments();}
    @GetMapping("/refunds") public Object refunds(){return service.refunds();}
    @GetMapping("/reconciliation") public Object reconciliation(){return service.reconciliation();}
    @GetMapping("/analytics") public Object analytics(){return service.analytics();}
    @GetMapping("/audit") public Object audit(){return service.audits();}

    public record StatusRequest(String status){}
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,Object>> bad(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("status",400,"error","Bad Request","message",e.getMessage()));}
    @ExceptionHandler(IllegalStateException.class) ResponseEntity<Map<String,Object>> conflict(IllegalStateException e){return ResponseEntity.status(409).body(Map.of("status",409,"error","Conflict","message",e.getMessage()));}
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<Map<String,Object>> forbidden(AccessDeniedException e){return ResponseEntity.status(403).body(Map.of("status",403,"error","Forbidden","message",e.getMessage()));}
}
