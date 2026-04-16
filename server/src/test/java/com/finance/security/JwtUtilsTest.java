package com.finance.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilsTest {

    @Test
    void generateAndValidateToken_fromEmail() {
        JwtUtils jwt = new JwtUtils();

        // minimal 32-char secret for HS256
        String secret = "01234567890123456789012345678901";
        ReflectionTestUtils.setField(jwt, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwt, "jwtExpirationMs", 3600000);

        String email = "alice@example.com";
        String token = jwt.generateTokenFromEmail(email);
        assertNotNull(token);

        assertTrue(jwt.validateToken(token));
        String parsed = jwt.getEmailFromToken(token);
        assertEquals(email, parsed);
    }
}
