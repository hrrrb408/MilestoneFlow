package com.milestoneflow.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Spring Security configuration for cookie-based opaque token authentication.
 *
 * <p>Per B1 Authentication Baseline §18:
 * <ul>
 *   <li>Stateless session management (no HTTP sessions)</li>
 *   <li>Custom filter reads MF_ACCESS cookie, validates against DB</li>
 *   <li>CSRF uses CookieCsrfTokenRepository (XSRF-TOKEN cookie)</li>
 *   <li>Public endpoints: register, verify, resend, login, actuator</li>
 *   <li>Protected endpoints: /auth/me (and future business endpoints)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final OpaqueAccessTokenAuthenticationFilter accessTokenFilter;
    private final AuthenticationEntryPointImpl authenticationEntryPoint;

    public SecurityConfiguration(OpaqueAccessTokenAuthenticationFilter accessTokenFilter,
                                  AuthenticationEntryPointImpl authenticationEntryPoint) {
        this.accessTokenFilter = accessTokenFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .path("/api/v1")
                .httpOnly(false)
                .sameSite("Lax"));

        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(
                                "/auth/register",
                                "/auth/email-verification/resend",
                                "/auth/email-verification/confirm",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/logout-all",
                                "/auth/password/change",
                                "/auth/password/forgot",
                                "/auth/password/reset",
                                "/actuator/health",
                                "/actuator/info"
                        ))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .xssProtection(xss -> xss.disable())
                        .frameOptions(fo -> fo.deny())
                        .referrerPolicy(rp -> rp.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.disable())
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/register",
                                "/auth/email-verification/resend",
                                "/auth/email-verification/confirm",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/password/forgot",
                                "/auth/password/reset",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(accessTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
