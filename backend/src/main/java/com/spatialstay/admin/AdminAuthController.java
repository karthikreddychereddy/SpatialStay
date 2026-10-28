package com.spatialstay.admin;

import com.spatialstay.user.LoginRequest;
import com.spatialstay.user.SpatialStayUserPrincipal;
import com.spatialstay.user.UserRole;
import com.spatialstay.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserService userService;
    private final AdminAuthService adminAuthService;

    public AdminAuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository, UserService userService, AdminAuthService adminAuthService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userService = userService;
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
            SpatialStayUserPrincipal principal = (SpatialStayUserPrincipal) authentication.getPrincipal();
            if (principal.getRole() != UserRole.ADMIN) {
                SecurityContextHolder.clearContext();
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "ADMIN_ACCESS_REQUIRED",
                        "message", "This account is not authorized for SpatialStay Admin."
                ));
            }
            HttpSession existing = httpRequest.getSession(false);
            if (existing != null) existing.invalidate();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            userService.recordSuccessfulLogin(principal.getPublicId());
            return ResponseEntity.ok(adminAuthService.currentAdmin());
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "INVALID_CREDENTIALS",
                    "message", "Invalid email or password."
            ));
        }
    }

    @GetMapping("/me")
    public AdminAuthResponse me() {
        return adminAuthService.currentAdmin();
    }
}
