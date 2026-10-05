package com.societycentral.config;

import com.societycentral.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;

/**
 * Core Spring Security configuration.
 * <p>
 * - Stateless (no server-side sessions) - every request must carry a JWT.
 * - Password hashing via BCrypt.
 * - JwtAuthFilter runs before Spring's default
 *   UsernamePasswordAuthenticationFilter to populate the SecurityContext
 *   from a Bearer token.
 * endpoint-level authorisation beyond "authenticated or not" (e.g.
 * "only SDOs can approve events", "only executives of society X can create
 * events for that society") is handled via @PreAuthorize at the
 * controller/service layer using the ROLE_STUDENT / ROLE_SDO authorities
 * from UserPrincipal, plus service-layer checks for
 * society-specific/executive-specific rules (see ExecutiveService,
 * SocietyService) - NOT solely via this URL-pattern based config.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Autowired
    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                           CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable()) // not needed for stateless JWT APIs
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "You do not have permission to perform this action.")))
                .authorizeHttpRequests(auth -> auth
                        // Logout requires a valid JWT - must come before the /api/auth/** permitAll rule below
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/auth/register-sdo").hasRole("ADMIN")
                        // Public endpoints - registration and login
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reference/**").permitAll()
                        // Stored event images are read-only public resources;
                        // upload remains protected by the rules below.
                        .requestMatchers(HttpMethod.GET, "/media/events/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/media/societies/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // Test endpoint for triggering demo report email
                        .requestMatchers("/api/test/**").permitAll()
                        // WebSocket handshake is open; the STOMP CONNECT frame
                        // authenticates the JWT (see WebSocketConfig).
                        .requestMatchers("/ws/**").permitAll()
                        // TODO: consider permitting GET on public browse when done implmenetong browse society and events
                        // endpoints (e.g. /api/societies, /api/events) for
                        // unauthenticated browsing, depending on whether the
                        // app requires login to view content at all.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeSecurityError(
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"type\":\"ERROR\",\"message\":\""
                        + message
                        + "\",\"data\":null}");
    }
}
