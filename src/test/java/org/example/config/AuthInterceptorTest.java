package org.example.config;

import jakarta.servlet.http.Cookie;
import org.example.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthInterceptorTest {
    @Test
    void stateChangingApiRequiresBearerEvenWhenCookieIsValid() throws Exception {
        JwtService jwt = mock(JwtService.class);
        when(jwt.isValid("signed-token")).thenReturn(true);
        AuthInterceptor interceptor = new AuthInterceptor(jwt);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/quizzes/7/copy");
        request.setCookies(new Cookie("authToken", "signed-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());

        request.addHeader("Authorization", "Bearer signed-token");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/api/quizzes/7");
        read.setCookies(new Cookie("authToken", "signed-token"));
        assertTrue(interceptor.preHandle(read, new MockHttpServletResponse(), new Object()));
    }
}
