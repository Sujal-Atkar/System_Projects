package com.system_project.distributedpaymentsystem.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.system_project.distributedpaymentsystem.service.auth.ApiCredentialService;

import jakarta.servlet.FilterChain;

class ApiKeyAuthenticationFilterTest {

    private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(mock(ApiCredentialService.class));

    @Test
    void merchantRegistrationShouldBypassAuthentication() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/merchants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        assertTrue(filter.shouldNotFilter(request));
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void authenticatedMerchantEndpointsShouldRemainProtected(String method, String path) throws Exception {
        MockHttpServletRequest request = request(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        assertFalse(filter.shouldNotFilter(request));
        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    private static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/merchants/00000000-0000-0000-0000-000000000001"),
                Arguments.of("POST", "/api/v1/payments"),
                Arguments.of("GET", "/api/v1/payments/00000000-0000-0000-0000-000000000001"));
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}