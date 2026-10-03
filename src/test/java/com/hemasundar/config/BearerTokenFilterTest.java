package com.hemasundar.config;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.hemasundar.config.properties.SecurityConfig;
import com.hemasundar.config.properties.SupabaseConfig;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * Unit tests for {@link BearerTokenFilter}.
 */
public class BearerTokenFilterTest {

    private BearerTokenFilter filter;
    private Environment mockEnv;

    private ECPublicKey  publicKey;
    private ECPrivateKey privateKey;
    private JwkProvider  mockJwkProvider;
    private SupabaseConfig mockSupabaseConfig;
    private SecurityConfig mockSecurityConfig;
    private RolesConfigLoader mockRolesConfigLoader;

    @BeforeMethod
    public void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = kpg.generateKeyPair();
        publicKey  = (ECPublicKey)  keyPair.getPublic();
        privateKey = (ECPrivateKey) keyPair.getPrivate();

        Jwk mockJwk = mock(Jwk.class);
        when(mockJwk.getPublicKey()).thenReturn(publicKey);

        mockJwkProvider = mock(JwkProvider.class);
        when(mockJwkProvider.get(any())).thenReturn(mockJwk);

        mockEnv = mock(Environment.class);
        when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"dev"});

        mockSupabaseConfig = mock(SupabaseConfig.class);
        mockSecurityConfig = mock(SecurityConfig.class);
        when(mockSecurityConfig.getAllowedEmails()).thenReturn("");
        when(mockSecurityConfig.getReadonlyEmails()).thenReturn("");

        mockRolesConfigLoader = mock(RolesConfigLoader.class);
        when(mockRolesConfigLoader.getReadonlyAllowedPages()).thenReturn(List.of("/", "/index.html", "/screeners.html"));

        filter = new BearerTokenFilter(mockEnv, mockSupabaseConfig, mockSecurityConfig, mockRolesConfigLoader);
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

    @Test
    public void testValidToken_allowed() throws Exception {
        MockHttpServletRequest  request  = apiRequest("GET", "/api/strategies");
        request.addHeader("Authorization", "Bearer " + validToken("user@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testExpiredToken_returns401() throws Exception {
        MockHttpServletRequest  request  = apiRequest("GET", "/api/strategies");
        request.addHeader("Authorization", "Bearer " + expiredToken("user@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testInvalidToken_returns401() throws Exception {
        MockHttpServletRequest  request  = apiRequest("GET", "/api/strategies");
        request.addHeader("Authorization", "Bearer not.a.real.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testMissingHeader_returns401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(apiRequest("GET", "/api/strategies"), response, new MockFilterChain());
        assertEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testAllowedEmail_passes() throws Exception {
        when(mockSecurityConfig.getAllowedEmails()).thenReturn("admin@gmail.com,user@gmail.com");

        MockHttpServletRequest  request  = apiRequest("GET", "/api/strategies");
        request.addHeader("Authorization", "Bearer " + validToken("admin@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testBlockedEmail_returns403() throws Exception {
        when(mockSecurityConfig.getAllowedEmails()).thenReturn("admin@gmail.com");

        MockHttpServletRequest  request  = apiRequest("GET", "/api/strategies");
        request.addHeader("Authorization", "Bearer " + validToken("hacker@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testReadOnlyUser_readEndpoint_allowed() throws Exception {
        when(mockSecurityConfig.getAllowedEmails()).thenReturn("viewer@gmail.com");
        when(mockSecurityConfig.getReadonlyEmails()).thenReturn("viewer@gmail.com");

        MockHttpServletRequest request = apiRequest("GET", "/api/results");
        request.addHeader("Authorization", "Bearer " + validToken("viewer@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
        assertEquals(request.getAttribute("userRole"), UserRole.READONLY.name());
    }

    @Test
    public void testReadOnlyUser_onlyInReadonlyConfig_allowed() throws Exception {
        when(mockSecurityConfig.getAllowedEmails()).thenReturn("admin@gmail.com");
        when(mockSecurityConfig.getReadonlyEmails()).thenReturn("viewer@gmail.com");

        MockHttpServletRequest request = apiRequest("GET", "/api/results");
        request.addHeader("Authorization", "Bearer " + validToken("viewer@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
        assertEquals(request.getAttribute("userRole"), UserRole.READONLY.name());
    }

    @Test
    public void testReadOnlyUser_writeEndpoint_blockedWith403() throws Exception {
        when(mockSecurityConfig.getAllowedEmails()).thenReturn("viewer@gmail.com");
        when(mockSecurityConfig.getReadonlyEmails()).thenReturn("viewer@gmail.com");

        MockHttpServletRequest request = apiRequest("POST", "/api/execute");
        request.addHeader("Authorization", "Bearer " + validToken("viewer@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testReadOnlyUser_deleteEndpoint_blockedWith403() throws Exception {
        when(mockSecurityConfig.getAllowedEmails()).thenReturn("viewer@gmail.com");
        when(mockSecurityConfig.getReadonlyEmails()).thenReturn("viewer@gmail.com");

        MockHttpServletRequest request = apiRequest("DELETE", "/api/results/custom/123");
        request.addHeader("Authorization", "Bearer " + validToken("viewer@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testReadOnlyUser_batchExecuteEndpoints_blockedWith403() throws Exception {
        when(mockSecurityConfig.getAllowedEmails()).thenReturn("viewer@gmail.com");
        when(mockSecurityConfig.getReadonlyEmails()).thenReturn("viewer@gmail.com");

        MockHttpServletRequest request1 = apiRequest("POST", "/api/execute/custom/batch");
        request1.addHeader("Authorization", "Bearer " + validToken("viewer@gmail.com"));
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        filter.doFilter(request1, response1, new MockFilterChain());
        assertEquals(response1.getStatus(), HttpServletResponse.SC_FORBIDDEN);

        MockHttpServletRequest request2 = apiRequest("POST", "/api/execute/custom-screener/batch");
        request2.addHeader("Authorization", "Bearer " + validToken("viewer@gmail.com"));
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request2, response2, new MockFilterChain());
        assertEquals(response2.getStatus(), HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testAdminUser_writeEndpoint_allowed() throws Exception {
        when(mockSecurityConfig.getAllowedEmails()).thenReturn("admin@gmail.com");
        when(mockSecurityConfig.getReadonlyEmails()).thenReturn("viewer@gmail.com");

        MockHttpServletRequest request = apiRequest("POST", "/api/execute");
        request.addHeader("Authorization", "Bearer " + validToken("admin@gmail.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
        assertEquals(request.getAttribute("userRole"), UserRole.ADMIN.name());
    }

    @Test
    public void testPublicAuthConfigEndpoint_noTokenRequired() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(apiRequest("GET", "/api/auth/config"), response, new MockFilterChain());
        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testStaticFile_noTokenRequired() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(apiRequest("GET", "/index.html"), response, new MockFilterChain());
        assertNotEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testNoJwksProvider_devMode_bypassesAuth() throws Exception {
        BearerTokenFilter devFilter = new BearerTokenFilter(mockEnv, mockSupabaseConfig, mockSecurityConfig, mockRolesConfigLoader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        devFilter.doFilter(apiRequest("GET", "/api/strategies"), response, new MockFilterChain());
        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testNoJwksProvider_productionMode_returns503() throws Exception {
        when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"production"});
        BearerTokenFilter prodFilter = new BearerTokenFilter(mockEnv, mockSupabaseConfig, mockSecurityConfig, mockRolesConfigLoader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        prodFilter.doFilter(apiRequest("GET", "/api/strategies"), response, new MockFilterChain());
        assertEquals(response.getStatus(), HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    private MockHttpServletRequest apiRequest(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        return req;
    }
}
