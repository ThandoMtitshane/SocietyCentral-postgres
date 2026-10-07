package com.societycentral.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS configuration allowing the React frontend (running on a different
 * origin) to call this Spring Boot API.
 * <p>
 * Local dev origins covered:
 * - http://localhost:3000  (Create React App default)
 * - http://localhost:5173  (Vite default - common with React+Vite setups)
 * - http://localhost:5174  (Vite secondary port if 5173 is taken)
 * <p>
 * Production origins covered:
 * - https://societycentral.uk      (primary domain)
 * - https://www.societycentral.uk  (www is a distinct browser origin)
 * - https://societycentral-nmu.netlify.app (Netlify deployment)
 */
@Configuration
public class CorsConfig {

    /**
     * Single source of truth for allowed browser origins, shared by both beans
     * below so the two lists can never drift apart.
     */
    public static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:5174",
            "https://societycentral.uk",
            "https://www.societycentral.uk",
            "https://societycentral-nmu.netlify.app"
    );

    private static final List<String> ALLOWED_METHODS = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    );

    /** Builds the shared CORS policy applied to every endpoint. */
    private static CorsConfiguration buildConfig() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(ALLOWED_ORIGINS);
        config.setAllowedMethods(ALLOWED_METHODS);

        // Allow all headers including Authorization (needed for JWT Bearer token)
        config.setAllowedHeaders(List.of("*"));

        // Expose Authorization header to the frontend if needed
        config.setExposedHeaders(List.of("Authorization"));

        // Allow cookies/credentials if needed later
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour (reduces OPTIONS requests)
        config.setMaxAge(3600L);

        return config;
    }

    private static UrlBasedCorsConfigurationSource buildSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildConfig());
        return source;
    }

    @Bean
    public CorsFilter corsFilter() {
        return new CorsFilter(buildSource());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return buildSource();
    }
}
