package com.finsight.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CorsConfigTest {
    @Test
    void onlyAllowsExplicitOriginsAndDoesNotEnableCookieCredentials() {
        var source = new CorsConfig("https://app.example.com,http://localhost:5173").corsConfigurationSource();
        var configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/API/accounts"));

        assertEquals(2, configuration.getAllowedOrigins().size());
        assertFalse(configuration.getAllowCredentials());
        assertEquals(java.util.List.of("Authorization", "Content-Type", "Accept"), configuration.getAllowedHeaders());
    }

    @Test
    void rejectsWildcardAndNonOriginValues() {
        assertThrows(IllegalArgumentException.class, () -> new CorsConfig("*"));
        assertThrows(IllegalArgumentException.class, () -> new CorsConfig("https://example.com/app"));
        assertThrows(IllegalArgumentException.class, () -> new CorsConfig(" , "));
    }
}
