package com.collaborative.editing.user.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Utility Tests")
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    private String testSecret = "mySecretKeyThatIsAtLeast32CharactersLong";
    private Long testExpiration = 3600000L; // 1 hour

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", testSecret);
        ReflectionTestUtils.setField(jwtUtil, "expiration", testExpiration);
    }

    @Test
    @DisplayName("Generate token successfully")
    void testGenerateToken_Success() {
        Long userId = 1L;
        String username = "testuser";

        String token = jwtUtil.generateToken(userId, username);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("Extract user ID from token")
    void testGetUserIdFromToken() {
        Long userId = 1L;
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        Long extractedUserId = jwtUtil.getUserIdFromToken(token);

        assertEquals(userId, extractedUserId);
    }

    @Test
    @DisplayName("Extract username from token")
    void testGetUsernameFromToken() {
        Long userId = 1L;
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        String extractedUsername = jwtUtil.getUsernameFromToken(token);

        assertEquals(username, extractedUsername);
    }

    @Test
    @DisplayName("Get expiration date from token")
    void testGetExpirationDateFromToken() {
        Long userId = 1L;
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        Date expirationDate = jwtUtil.getExpirationDateFromToken(token);

        assertNotNull(expirationDate);
        assertTrue(expirationDate.after(new Date()));
    }

    @Test
    @DisplayName("Validate token with correct user ID")
    void testValidateToken_WithUserId_Success() {
        Long userId = 1L;
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        Boolean isValid = jwtUtil.validateToken(token, userId);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Validate token with incorrect user ID")
    void testValidateToken_WithWrongUserId_Failure() {
        Long userId = 1L;
        Long wrongUserId = 2L;
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        Boolean isValid = jwtUtil.validateToken(token, wrongUserId);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Validate token without user ID")
    void testValidateToken_WithoutUserId_Success() {
        Long userId = 1L;
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        Boolean isValid = jwtUtil.validateToken(token);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Check if token is expired - should be false for new token")
    void testIsTokenExpired_NewToken() {
        Long userId = 1L;
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        Boolean isExpired = jwtUtil.isTokenExpired(token);

        assertFalse(isExpired);
    }

    @Test
    @DisplayName("Handle invalid token gracefully")
    void testValidateToken_InvalidToken() {
        String invalidToken = "invalid.token.here";

        Boolean isValid = jwtUtil.validateToken(invalidToken);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Handle null token gracefully")
    void testValidateToken_NullToken() {
        Boolean isValid = jwtUtil.validateToken(null);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Handle empty token gracefully")
    void testValidateToken_EmptyToken() {
        Boolean isValid = jwtUtil.validateToken("");

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Generate different tokens for different users")
    void testGenerateToken_DifferentUsers() {
        String token1 = jwtUtil.generateToken(1L, "user1");
        String token2 = jwtUtil.generateToken(2L, "user2");

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Generate different tokens for same user at different times")
    void testGenerateToken_SameUserDifferentTimes() {
        String token1 = jwtUtil.generateToken(1L, "user1");
        try {
            Thread.sleep(10); // Small delay to ensure different timestamps
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String token2 = jwtUtil.generateToken(1L, "user1");

        // Tokens should be different due to different issuedAt times
        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Handle short secret key - should pad to minimum length")
    void testGenerateToken_ShortSecret() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "short");
        Long userId = 1L;
        String username = "testuser";

        // Should not throw exception
        assertDoesNotThrow(() -> {
            String token = jwtUtil.generateToken(userId, username);
            assertNotNull(token);
        });
    }
}
