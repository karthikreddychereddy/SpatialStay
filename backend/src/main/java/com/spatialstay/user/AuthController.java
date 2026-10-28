package com.spatialstay.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.security.web.csrf.CsrfToken;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AccountSecurityService accountSecurityService;

    public AuthController(
            UserService userService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            AccountSecurityService accountSecurityService
    ) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository =
                securityContextRepository;
        this.accountSecurityService =
                accountSecurityService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        UserResponse user =
                userService.registerCustomer(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(user);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {

            Authentication authentication =
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken
                                    .unauthenticated(
                                            request.email(),
                                            request.password()
                                    )
                    );

            /*
             * Prevent session fixation by invalidating an
             * already existing anonymous/old session before
             * establishing the authenticated one.
             */
            HttpSession existingSession =
                    httpRequest.getSession(false);

            if (existingSession != null) {
                existingSession.invalidate();
            }

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);

            SecurityContextHolder.setContext(context);

            securityContextRepository.saveContext(
                    context,
                    httpRequest,
                    httpResponse
            );

            SpatialStayUserPrincipal principal =
                    (SpatialStayUserPrincipal)
                            authentication.getPrincipal();

            userService.recordSuccessfulLogin(
                    principal.getPublicId()
            );

            UserResponse user =
                    userService.getUserByPublicId(
                            principal.getPublicId()
                    );

            return ResponseEntity.ok(user);

        } catch (AuthenticationException exception) {

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "error",
                    "INVALID_CREDENTIALS"
            );

            body.put(
                    "message",
                    "Invalid email or password."
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(body);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            Authentication authentication
    ) {
        SpatialStayUserPrincipal principal =
                (SpatialStayUserPrincipal)
                        authentication.getPrincipal();

        return ResponseEntity.ok(
                userService.getUserByPublicId(
                        principal.getPublicId()
                )
        );
    }

    /*
     * Accessing this endpoint causes Spring Security's
     * deferred CSRF token to be resolved and returned to
     * the SPA.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(
            CsrfToken token
    ) {
        return Map.of(
                "headerName",
                token.getHeaderName(),
                "parameterName",
                token.getParameterName(),
                "token",
                token.getToken()
        );
    }
    @PostMapping("/email-verification/request")
    public ResponseEntity<Map<String, Object>>
    requestEmailVerification() {

        var token =
                accountSecurityService
                        .createEmailVerificationToken();

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "message",
                "Verification request accepted."
        );

        if (accountSecurityService.exposeDevelopmentTokens()
                && token.isPresent()) {

            body.put(
                    "developmentToken",
                    token.get()
            );
        }

        return ResponseEntity.ok(body);
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<Map<String, Object>>
    verifyEmail(
            @RequestParam String token
    ) {
        accountSecurityService.verifyEmail(token);

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Email verified successfully."
                )
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>>
    forgotPassword(
            @Valid @RequestBody
            ForgotPasswordRequest request
    ) {
        var token =
                accountSecurityService
                        .requestPasswordReset(
                                request.email()
                        );

        Map<String, Object> body =
                new LinkedHashMap<>();

        /*
        * Deliberately generic so callers cannot determine
        * whether an account exists for this email.
        */
        body.put(
                "message",
                "If an account exists for that email, password reset instructions will be sent."
        );

        if (accountSecurityService.exposeDevelopmentTokens()
                && token.isPresent()) {

            body.put(
                    "developmentToken",
                    token.get()
            );
        }

        return ResponseEntity.ok(body);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>>
    resetPassword(
            @Valid @RequestBody
            ResetPasswordRequest request
    ) {
        accountSecurityService.resetPassword(
                request.token(),
                request.newPassword()
        );

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Password reset successfully."
                )
        );
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>>
    changePassword(
            @Valid @RequestBody
            ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        accountSecurityService.changePassword(
                request.currentPassword(),
                request.newPassword()
        );
        HttpSession session =
                httpRequest.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Password changed successfully. Please sign in again."
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fields =
                new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        fields.putIfAbsent(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "error",
                "VALIDATION_FAILED"
        );

        body.put(
                "message",
                "Please correct the invalid fields."
        );

        body.put(
                "fields",
                fields
        );

        return ResponseEntity
                .badRequest()
                .body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleRequestError(
            IllegalArgumentException exception
    ) {
        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "error",
                "REQUEST_FAILED"
        );

        body.put(
                "message",
                exception.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(body);
    }
}