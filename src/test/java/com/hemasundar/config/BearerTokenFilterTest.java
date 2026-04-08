package com.hemasundar.config;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * Unit tests for {@link BearerTokenFilter}.
 *
 * <p>Uses a locally generated EC P-256 key pair and a mocked {@link JwkProvider}
 * so no real JWKS network calls are made during testing.
 */
public class BearerTokenFilterTest {

    private BearerTokenFilter filter;
    private Environment mockEnv;

    // EC P-256 key pair generated fresh per test method
    private ECPublicKey  publicKey;
    private ECPrivateKey privateKey;
    private JwkProvider  mockJwkProvider;

    @BeforeMethod
    public void setUp() throws Exception {
        // Generate a real P-256 key pair (matches Supabase's ECC P-256 signing key)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = kpg.generateKeyPair();
        publicKey  = (ECPublicKey)  keyPair.getPublic();
        privateKey = (ECPrivateKey) keyPair.getPrivate();

        // Mock JwkProvider to return our test public key
        Jwk mockJwk = mock(Jwk.class);
        when(mockJwk.getPublicKey()).thenReturn(publicKey);

        mockJwkProvider = mock(JwkProvider.class);
        when(mockJwkProvider.get(any())).thenReturn(mockJwk);

        // Create filter in dev mode (no production profile)
        mockEnv = mock(Environment.class);
        when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"dev"});

        filter = new BearerTokenFilter(mockEnv);
        filter.setJwkProvider(mockJwkProvider);
    }

    private String validToken(String email) {
        return JWT.create()
                .withKeyId("test-kid")
                .withClaim("email", email)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(Algorithm.ECDSA256(publicKey, privateKey));
    }

    private String expiredToken(String email) {
        return JWT.create()
                .withKeyId("test-kid")
                .withClaim("email", email)
                .withExpiresAt(Date.from(Instant.now().minusSeconds(3600)))
                .sign(Algorithm.ECDSA256(publicKey, privateKey));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    public void testValidToken_allowed() throws Exception {
        MockHttpServletRequest  request  = apiRequest("/api/strategies");
        request.addHeader("Authorization", "Bearer " + validToken("user@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }

    // ── Token errors ──────────────────────────────────────────────────────

    @Test
    public void testExpiredToken_returns401() throws Exception {
        MockHttpServletRequest  request  = apiRequest("/api/strategies");
        request.addHeader("Authorization", "Bearer " + expiredToken("user@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testInvalidToken_returns401() throws Exception {
        MockHttpServletRequest  request  = apiRequest("/api/strategies");
        request.addHeader("Authorization", "Bearer not.a.real.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testMissingHeader_returns401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(apiRequest("/api/strategies"), response, new MockFilterChain());
        assertEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    // ── Email allowlist ───────────────────────────────────────────────────

    @Test
    public void testAllowedEmail_passes() throws Exception {
        filter.allowedEmailsConfig = "admin@gmail.com,user@gmail.com";

        MockHttpServletRequest  request  = apiRequest("/api/strategies");
        request.addHeader("Authorization", "Bearer " + validToken("admin@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testBlockedEmail_returns403() throws Exception {
        filter.allowedEmailsConfig = "admin@gmail.com";

        MockHttpServletRequest  request  = apiRequest("/api/strategies");
        request.addHeader("Authorization", "Bearer " + validToken("hacker@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_FORBIDDEN);
    }

    // ── Public / non-API paths ────────────────────────────────────────────

    @Test
    public void testPublicAuthConfigEndpoint_noTokenRequired() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(apiRequest("/api/auth/config"), response, new MockFilterChain());
        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testStaticFile_noTokenRequired() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(apiRequest("/index.html"), response, new MockFilterChain());
        assertNotEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    // ── JWKS provider not configured ──────────────────────────────────────

    @Test
    public void testNoJwksProvider_devMode_bypassesAuth() throws Exception {
        BearerTokenFilter devFilter = new BearerTokenFilter(mockEnv);
        // No setJwkProvider() call — simulates local dev with no SUPABASE_URL

        MockHttpServletResponse response = new MockHttpServletResponse();
        devFilter.doFilter(apiRequest("/api/strategies"), response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testNoJwksProvider_productionMode_returns503() throws Exception {
        when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"production"});
        BearerTokenFilter prodFilter = new BearerTokenFilter(mockEnv);
        // No setJwkProvider() — simulates misconfigured production

        MockHttpServletResponse response = new MockHttpServletResponse();
        prodFilter.doFilter(apiRequest("/api/strategies"), response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private MockHttpServletRequest apiRequest(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        return req;
    }
}
