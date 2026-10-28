package com.spatialstay.hoteloperations;

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

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/hotel-operations/auth")
public class HotelOperationsAuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserService userService;
    private final HotelOperationsAuthService hotelOperationsAuthService;

    public HotelOperationsAuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            UserService userService,
            HotelOperationsAuthService hotelOperationsAuthService
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userService = userService;
        this.hotelOperationsAuthService = hotelOperationsAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email(),
                            request.password()
                    )
            );

            SpatialStayUserPrincipal principal =
                    (SpatialStayUserPrincipal) authentication.getPrincipal();

            if (principal.getRole() != UserRole.HOTEL_STAFF
                    && principal.getRole() != UserRole.HOTEL_HEAD) {
                SecurityContextHolder.clearContext();
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "HOTEL_OPERATIONS_ACCESS_REQUIRED",
                        "message", "This account is not authorized for Hotel Operations."
                ));
            }

            HttpSession existingSession = httpRequest.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            userService.recordSuccessfulLogin(principal.getPublicId());

            return ResponseEntity.ok(hotelOperationsAuthService.currentOperator());

        } catch (AuthenticationException exception) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "INVALID_CREDENTIALS");
            body.put("message", "Invalid email or password.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
    }

    @GetMapping("/me")
    public HotelOperationsLoginResponse me() {
        return hotelOperationsAuthService.currentOperator();
    }
}
