package com.spatialstay.config;

import com.spatialstay.user.GoogleAuthenticationService;
import com.spatialstay.user.SpatialStayUserDetailsService;
import com.spatialstay.user.SpatialStayUserPrincipal;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            GoogleAuthenticationService googleAuthenticationService,
            UserRepository userRepository
    ) throws Exception {

        http
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                )

                .csrf(csrf -> {
                    CookieCsrfTokenRepository repository =
                            CookieCsrfTokenRepository.withHttpOnlyFalse();

                    repository.setCookiePath("/");

                    XorCsrfTokenRequestAttributeHandler requestHandler =
                            new XorCsrfTokenRequestAttributeHandler();

                    requestHandler.setCsrfRequestAttributeName(
                            null
                    );

                    csrf
                            .csrfTokenRepository(repository)
                            .csrfTokenRequestHandler(
                                    requestHandler
                            )

                            /*
                             * Development-stage exemptions.
                             *
                             * Logout remains temporarily exempt
                             * until the frontend CSRF integration
                             * in 6J. We remove that exemption
                             * before production.
                             */
                                .ignoringRequestMatchers(
                                        "/api/auth/register",
                                        "/api/hotel-applications",
                                        "/api/public/media/hotel-application",
                                        "/api/auth/login",
                                        "/api/hotel-operations/auth/login",
                                        "/api/admin/auth/login",
                                        "/api/auth/forgot-password",
                                        "/api/auth/reset-password",
                                        "/api/auth/email-verification/confirm",
                                        "/api/internal/ai/voice",
                                        "/api/internal/ai/voice/**",
                                        "/api/payments/webhooks/razorpay"
                                );
                })

                .securityContext(context ->
                        context
                                .securityContextRepository(
                                        securityContextRepository
                                )
                                .requireExplicitSave(true)
                )

                .formLogin(form ->
                        form.disable()
                )

                .httpBasic(basic ->
                        basic.disable()
                )

                /*
                 * Google OpenID Connect authentication.
                 *
                 * Google identity validation/provisioning is
                 * handled by GoogleAuthenticationService.
                 */
                .oauth2Login(oauth2 ->
                        oauth2
                                .securityContextRepository(
                                        securityContextRepository
                                )

                                .userInfoEndpoint(userInfo ->
                                        userInfo.oidcUserService(
                                                googleAuthenticationService
                                        )
                                )

                                .successHandler(
                                        (request,
                                         response,
                                         authentication) -> {

                                            if (!(authentication
                                                    .getPrincipal()
                                                    instanceof OidcUser oidcUser)) {

                                                response.sendRedirect(
                                                        frontendUrl
                                                                + "/?auth=error"
                                                );

                                                return;
                                            }

                                            String subject =
                                                    oidcUser.getSubject();

                                            User user =
                                                    userRepository
                                                            .findByGoogleSubject(
                                                                    subject
                                                            )
                                                            .orElse(null);

                                            if (user == null) {
                                                response.sendRedirect(
                                                        frontendUrl
                                                                + "/?auth=error"
                                                );

                                                return;
                                            }

                                            SpatialStayUserPrincipal principal =
                                                    new SpatialStayUserPrincipal(
                                                            user
                                                    );

                                            UsernamePasswordAuthenticationToken
                                                    spatialAuthentication =
                                                    UsernamePasswordAuthenticationToken
                                                            .authenticated(
                                                                    principal,
                                                                    null,
                                                                    principal
                                                                            .getAuthorities()
                                                            );

                                            SecurityContext context =
                                                    SecurityContextHolder
                                                            .createEmptyContext();

                                            context.setAuthentication(
                                                    spatialAuthentication
                                            );

                                            SecurityContextHolder
                                                    .setContext(
                                                            context
                                                    );

                                            securityContextRepository
                                                    .saveContext(
                                                            context,
                                                            request,
                                                            response
                                                    );

                                            response.sendRedirect(
                                                    frontendUrl
                                                            + "/?auth=success"
                                            );
                                        }
                                )

                                .failureHandler(
                                        (request,
                                         response,
                                         exception) ->
                                                response.sendRedirect(
                                                        frontendUrl
                                                                + "/?auth=error"
                                                )
                                )
                )

                .logout(logout ->
                        logout
                                .logoutUrl(
                                        "/api/auth/logout"
                                )

                                .invalidateHttpSession(
                                        true
                                )

                                .clearAuthentication(
                                        true
                                )

                                .deleteCookies(
                                        "JSESSIONID",
                                        "XSRF-TOKEN"
                                )

                                .logoutSuccessHandler(
                                        (request,
                                         response,
                                         authentication) ->
                                                response.setStatus(
                                                        HttpServletResponse
                                                                .SC_NO_CONTENT
                                                )
                                )
                )

                .exceptionHandling(exceptions ->
                        exceptions

                                .authenticationEntryPoint(
                                        (request,
                                         response,
                                         exception) ->
                                                response.sendError(
                                                        HttpServletResponse
                                                                .SC_UNAUTHORIZED
                                                )
                                )

                                .accessDeniedHandler(
                                        (request,
                                         response,
                                         exception) ->
                                                response.sendError(
                                                        HttpServletResponse
                                                                .SC_FORBIDDEN
                                                )
                                )
                )

                .authorizeHttpRequests(authorize ->
                        authorize

                                /*
                                 * OAuth authorization initiation
                                 * and callback endpoints must be
                                 * reachable before authentication.
                                 */
                                .requestMatchers(
                                        "/oauth2/**",
                                        "/login/oauth2/**"
                                )
                                .permitAll()

                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/hotels",
                                        "/api/hotels/**"
                                )
                                .permitAll()

                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/auth/register",
                                        "/api/hotel-applications",
                                        "/api/auth/login",
                                        "/api/hotel-operations/auth/login",
                                        "/api/auth/forgot-password",
                                        "/api/auth/reset-password",
                                        "/api/auth/email-verification/confirm"
                                )
                                .permitAll()

                                // Razorpay authenticates with the webhook HMAC,
                                // not a SpatialStay customer session.
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/payments/webhooks/razorpay"
                                )
                                .permitAll()

                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/auth/csrf"
                                )
                                .permitAll()

                                .requestMatchers(HttpMethod.GET, "/uploads/**")
                                .permitAll()

                                .requestMatchers(HttpMethod.POST, "/api/public/media/hotel-application")
                                .permitAll()

                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/admin/auth/login"
                                )
                                .permitAll()

                                .requestMatchers(
                                        "/api/admin/**"
                                )
                                .hasRole(
                                        "ADMIN"
                                )

                                .requestMatchers(
                                        "/api/hotel-operations/**"
                                )
                                .hasAnyRole(
                                        "HOTEL_STAFF",
                                        "HOTEL_HEAD",
                                        "ADMIN"
                                )



                                /*
                                * Internal LiveKit voice-agent endpoint.
                                *
                                * Spring browser/session authentication is not used here.
                                * AiVoiceController performs server-to-server authentication
                                * using X-SpatialStay-Voice-Key.
                                */
                                .requestMatchers(
                                        "/api/internal/ai/voice",
                                        "/api/internal/ai/voice/**"
                                )
                                .permitAll()

                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/payments/webhooks/razorpay"
                                )
                                .permitAll()



                                .anyRequest()
                                .authenticated()
                );

        return http.build();
    }

    @Bean
    public SecurityContextRepository
    securityContextRepository() {

        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            SpatialStayUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(
                        userDetailsService
                );

        provider.setPasswordEncoder(
                passwordEncoder
        );

        return new ProviderManager(
                provider
        );
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(frontendUrl)
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Content-Type",
                        "Accept",
                        "X-XSRF-TOKEN"
                )
        );

        configuration.setExposedHeaders(
                List.of("X-XSRF-TOKEN")
        );

        configuration.setAllowCredentials(true);

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}