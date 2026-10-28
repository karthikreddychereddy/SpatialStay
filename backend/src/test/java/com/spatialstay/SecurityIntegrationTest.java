package com.spatialstay;

import com.spatialstay.booking.BookingRepository;
import com.spatialstay.booking.InventoryHoldRepository;
import com.spatialstay.user.AccountSecurityService;
import com.spatialstay.user.AccountTokenRepository;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;
import com.spatialstay.user.UserStatus;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.MediaType;

import org.springframework.mock.web.MockHttpSession;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

import org.springframework.test.context.ActiveProfiles;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.spatialstay.payment.PaymentAttemptRepository;


@SpringBootTest
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final String TEST_PASSWORD =
            "SpatialStay2026";


    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountSecurityService accountSecurityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountTokenRepository accountTokenRepository;

    /*
     * Step 9 repositories are required here because the security
     * integration tests share the same PostgreSQL test database.
     *
     * Bookings and inventory holds reference records that ultimately
     * belong to users, so they must be removed before users.
     */
    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private InventoryHoldRepository inventoryHoldRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;


    private MockMvc mockMvc;


    @BeforeEach
    void setUp() {

        mockMvc =
                MockMvcBuilders
                        .webAppContextSetup(
                                applicationContext
                        )
                        .apply(
                                SecurityMockMvcConfigurers
                                        .springSecurity()
                        )
                        .build();

        /*
         * IMPORTANT:
         *
         * Delete records in foreign-key dependency order.
         *
         * Booking -> InventoryHold -> User
         *
         * AccountToken also references User.
         *
         * PostgreSQL correctly prevents deleting a parent row while
         * child rows still reference it, so the cleanup order must
         * remain child-first.
         */

        paymentAttemptRepository.deleteAll();

        bookingRepository.deleteAll();

        inventoryHoldRepository.deleteAll();

        accountTokenRepository.deleteAll();

        userRepository.deleteAll();
    }


    /*
     * PUBLIC / AUTHENTICATION BOUNDARIES
     */

    @Test
    void unauthenticatedMeRequestReturns401()
            throws Exception {

        mockMvc.perform(
                        get("/api/auth/me")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }


    @Test
    void publicHotelEndpointDoesNotRequireAuthentication()
            throws Exception {

        mockMvc.perform(
                        get("/api/hotels")
                )
                .andExpect(
                        status().isOk()
                );
    }


    /*
     * REGISTRATION
     */

    @Test
    void registrationCreatesCustomerAccount()
            throws Exception {

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "firstName",
                                                        "Security",

                                                        "lastName",
                                                        "Tester",

                                                        "email",
                                                        "customer@example.com",

                                                        "password",
                                                        TEST_PASSWORD
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                );

        User user =
                userRepository
                        .findByEmail(
                                "customer@example.com"
                        )
                        .orElseThrow();

        assertThat(
                user.getRole().name()
        ).isEqualTo(
                "CUSTOMER"
        );

        assertThat(
                user.getStatus()
        ).isEqualTo(
                UserStatus.ACTIVE
        );

        assertThat(
                user.isEmailVerified()
        ).isFalse();

        assertThat(
                passwordEncoder.matches(
                        TEST_PASSWORD,
                        user.getPasswordHash()
                )
        ).isTrue();
    }


    @Test
    void weakRegistrationPasswordIsRejected()
            throws Exception {

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "firstName",
                                                        "Security",

                                                        "lastName",
                                                        "Tester",

                                                        "email",
                                                        "weak@example.com",

                                                        "password",
                                                        "abcdefghij"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        assertThat(
                userRepository.existsByEmail(
                        "weak@example.com"
                )
        ).isFalse();
    }


    @Test
    void duplicateEmailRegistrationIsRejected()
            throws Exception {

        createCustomer(
                "duplicate@example.com",
                TEST_PASSWORD
        );

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "firstName",
                                                        "Another",

                                                        "lastName",
                                                        "Customer",

                                                        "email",
                                                        "duplicate@example.com",

                                                        "password",
                                                        "Another2026"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );
    }


    /*
     * LOGIN
     */

    @Test
    void validCredentialsCreateAuthenticatedSession()
            throws Exception {

        createCustomer(
                "login@example.com",
                TEST_PASSWORD
        );

        MvcResult loginResult =
                login(
                        "login@example.com",
                        TEST_PASSWORD
                );

        MockHttpSession session =
                (MockHttpSession)
                        loginResult
                                .getRequest()
                                .getSession(false);

        assertThat(
                session
        ).isNotNull();

        mockMvc.perform(
                        get("/api/auth/me")
                                .session(session)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.email")
                                .value(
                                        "login@example.com"
                                )
                );
    }


    @Test
    void invalidPasswordReturns401()
            throws Exception {

        createCustomer(
                "wrong-password@example.com",
                TEST_PASSWORD
        );

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "email",
                                                        "wrong-password@example.com",

                                                        "password",
                                                        "WrongPassword2026"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value(
                                        "INVALID_CREDENTIALS"
                                )
                );
    }


    @Test
    void lockedAccountCannotLogin()
            throws Exception {

        User user =
                createCustomer(
                        "locked@example.com",
                        TEST_PASSWORD
                );

        user.changeStatus(
                UserStatus.LOCKED
        );

        userRepository.saveAndFlush(
                user
        );

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "email",
                                                        "locked@example.com",

                                                        "password",
                                                        TEST_PASSWORD
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }


    @Test
    void disabledAccountCannotLogin()
            throws Exception {

        User user =
                createCustomer(
                        "disabled@example.com",
                        TEST_PASSWORD
                );

        user.changeStatus(
                UserStatus.DISABLED
        );

        userRepository.saveAndFlush(
                user
        );

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "email",
                                                        "disabled@example.com",

                                                        "password",
                                                        TEST_PASSWORD
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }


    /*
     * AUTHORIZATION BOUNDARIES
     */

    @Test
    void customerCannotAccessAdminBoundary()
            throws Exception {

        createCustomer(
                "customer-admin@example.com",
                TEST_PASSWORD
        );

        MockHttpSession session =
                loginSession(
                        "customer-admin@example.com",
                        TEST_PASSWORD
                );

        mockMvc.perform(
                        get(
                                "/api/admin/security-test"
                        )
                                .session(session)
                )
                .andExpect(
                        status().isForbidden()
                );
    }


    @Test
    void customerCannotAccessHotelOperationsBoundary()
            throws Exception {

        createCustomer(
                "customer-hotel@example.com",
                TEST_PASSWORD
        );

        MockHttpSession session =
                loginSession(
                        "customer-hotel@example.com",
                        TEST_PASSWORD
                );

        mockMvc.perform(
                        get(
                                "/api/hotel-operations/security-test"
                        )
                                .session(session)
                )
                .andExpect(
                        status().isForbidden()
                );
    }


    /*
     * CSRF
     */

    @Test
    void protectedStateChangingRequestWithoutCsrfIsRejected()
            throws Exception {

        createCustomer(
                "csrf@example.com",
                TEST_PASSWORD
        );

        MockHttpSession session =
                loginSession(
                        "csrf@example.com",
                        TEST_PASSWORD
                );

        mockMvc.perform(
                        post(
                                "/api/auth/email-verification/request"
                        )
                                .session(session)
                )
                .andExpect(
                        status().isForbidden()
                );
    }


    @Test
    void protectedRequestWithValidCsrfTokenIsAccepted()
            throws Exception {

        createCustomer(
                "csrf-valid@example.com",
                TEST_PASSWORD
        );

        MockHttpSession session =
                loginSession(
                        "csrf-valid@example.com",
                        TEST_PASSWORD
                );

        mockMvc.perform(
                        post(
                                "/api/auth/email-verification/request"
                        )
                                .session(session)
                                .with(csrf())
                )
                .andExpect(
                        status().isOk()
                );
    }


    /*
     * PASSWORD RESET
     */

    @Test
    void passwordResetTokenIsSingleUse()
            throws Exception {

        createCustomer(
                "reset@example.com",
                TEST_PASSWORD
        );

        String token =
                accountSecurityService
                        .requestPasswordReset(
                                "reset@example.com"
                        )
                        .orElseThrow();

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "token",
                                                        token,

                                                        "newPassword",
                                                        "NewSpatial2026"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "token",
                                                        token,

                                                        "newPassword",
                                                        "AnotherSpatial2026"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );
    }


    @Test
    void weakPasswordResetDoesNotConsumeToken()
            throws Exception {

        createCustomer(
                "weak-reset@example.com",
                TEST_PASSWORD
        );

        String token =
                accountSecurityService
                        .requestPasswordReset(
                                "weak-reset@example.com"
                        )
                        .orElseThrow();

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "token",
                                                        token,

                                                        "newPassword",
                                                        "abcdefghij"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "token",
                                                        token,

                                                        "newPassword",
                                                        "ValidReset2026"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }


    /*
     * EMAIL VERIFICATION
     */

    @Test
    void emailVerificationTokenIsSingleUse() {

        User user =
                createCustomer(
                        "verify@example.com",
                        TEST_PASSWORD
                );

        String token =
                authenticatedVerificationToken(
                        user
                );

        accountSecurityService.verifyEmail(
                token
        );

        User verifiedUser =
                userRepository
                        .findByEmail(
                                "verify@example.com"
                        )
                        .orElseThrow();

        assertThat(
                verifiedUser.isEmailVerified()
        ).isTrue();

        assertThatThrownBy(
                () ->
                        accountSecurityService
                                .verifyEmail(token)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }


    /*
     * TOKEN REQUEST COOLDOWN
     */

    @Test
    void passwordResetRequestIsRateLimitedByCooldown() {

        createCustomer(
                "cooldown@example.com",
                TEST_PASSWORD
        );

        assertThat(
                accountSecurityService
                        .requestPasswordReset(
                                "cooldown@example.com"
                        )
        ).isPresent();

        assertThat(
                accountSecurityService
                        .requestPasswordReset(
                                "cooldown@example.com"
                        )
        ).isEmpty();
    }


    /*
     * HELPERS
     */

    private User createCustomer(
            String email,
            String rawPassword
    ) {

        User user =
                new User(
                        email,
                        passwordEncoder.encode(
                                rawPassword
                        ),
                        "Security",
                        "Tester"
                );

        return userRepository
                .saveAndFlush(
                        user
                );
    }


    private MvcResult login(
            String email,
            String password
    ) throws Exception {

        return mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        json(
                                                Map.of(
                                                        "email",
                                                        email,

                                                        "password",
                                                        password
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andReturn();
    }


    private MockHttpSession loginSession(
            String email,
            String password
    ) throws Exception {

        MvcResult result =
                login(
                        email,
                        password
                );

        MockHttpSession session =
                (MockHttpSession)
                        result
                                .getRequest()
                                .getSession(false);

        assertThat(
                session
        ).isNotNull();

        return session;
    }


    /*
     * AccountSecurityService obtains the current customer through
     * SecurityContext.
     *
     * Establish the same principal used by the real application,
     * execute the service operation, and always clear the context.
     */
    private String authenticatedVerificationToken(
            User user
    ) {

        var principal =
                new com.spatialstay.user
                        .SpatialStayUserPrincipal(
                                user
                        );

        var authentication =
                new org.springframework.security
                        .authentication
                        .UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );

        org.springframework.security.core
                .context.SecurityContextHolder
                .getContext()
                .setAuthentication(
                        authentication
                );

        try {

            return accountSecurityService
                    .createEmailVerificationToken()
                    .orElseThrow();

        } finally {

            org.springframework.security.core
                    .context.SecurityContextHolder
                    .clearContext();
        }
    }


    private String json(
            Object value
    ) throws Exception {

        return objectMapper
                .writeValueAsString(
                        value
                );
    }
}