package com.centneo.fintech.supportDeskSvc.security;

import io.jsonwebtoken.*;
import org.springframework.stereotype.Component;

import java.security.PublicKey;

@Component
public class JwtTokenValidator {

    private final PublicKey publicKey;

    public JwtTokenValidator(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
