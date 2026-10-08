package com.finsight.config;

import java.util.Arrays;
import java.util.List;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class CorsConfig {

    private final List<String> origins;

    public CorsConfig(@Value("${finsight.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isBlank()).toList();
        if (origins.isEmpty()) throw new IllegalArgumentException("At least one CORS origin must be configured");
        for (String origin : origins) {
            try {
                URI uri = new URI(origin);
                String scheme = uri.getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)
                        || uri.getHost() == null
                        || uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                        || uri.getRawQuery() != null || uri.getRawFragment() != null || "*".equals(origin)) {
                    throw new IllegalArgumentException("CORS origins must be exact http(s) origins without paths or wildcards");
                }
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("CORS origin is invalid", exception);
            }
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(origins);

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

        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));

        // The API uses Authorization bearer headers and does not use browser cookies.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
