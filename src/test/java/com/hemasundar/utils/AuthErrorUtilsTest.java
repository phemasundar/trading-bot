package com.hemasundar.utils;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class AuthErrorUtilsTest {

    private AuthErrorUtils authErrorUtils;

    @BeforeMethod
    public void setUp() {
        authErrorUtils = new AuthErrorUtils();
    }

    @Test
    public void testIsAuthError_NullOrEmpty() {
        assertFalse(authErrorUtils.isAuthError(null));
        assertFalse(authErrorUtils.isAuthError(new RuntimeException((String) null)));
    }

    @Test
    public void testIsAuthError_GenericException() {
        assertFalse(authErrorUtils.isAuthError(new RuntimeException("Connection reset by peer")));
        assertFalse(authErrorUtils.isAuthError(new RuntimeException("Null pointer exception")));
    }

    @Test
    public void testIsAuthError_HttpStatusCodes() {
        assertTrue(authErrorUtils.isAuthError(new RuntimeException("HTTP 401 Unauthorized")));
        assertTrue(authErrorUtils.isAuthError(new RuntimeException("Server returned 403 Forbidden")));
    }

    @Test
    public void testIsAuthError_TokenAndOAuthErrors() {
        assertTrue(authErrorUtils.isAuthError(new RuntimeException("invalid_grant")));
        assertTrue(authErrorUtils.isAuthError(new RuntimeException("missing token in headers")));
        assertTrue(authErrorUtils.isAuthError(new RuntimeException("access token expired")));
        assertTrue(authErrorUtils.isAuthError(new RuntimeException("invalid_client configuration")));
        assertTrue(authErrorUtils.isAuthError(new RuntimeException("unauthorized user")));
        assertTrue(authErrorUtils.isAuthError(new RuntimeException("forbidden resource")));
    }
}
