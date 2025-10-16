package com.centneo.fintech.supportDeskSvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtKeyConfig {

    // supports values like: classpath:keys/jwt_public_key.pem  or file:/etc/secrets/jwt_public_key.pem
    @Value("${jwt.public-key-file:classpath:keys/jwt_public_key.pem}")
    private Resource publicKeyResource;

    @Bean
    public PublicKey jwtPublicKey() throws Exception {
        if (publicKeyResource == null || !publicKeyResource.exists()) {
            throw new IllegalStateException("Public key resource not found: " + publicKeyResource);
        }

        try (InputStream is = publicKeyResource.getInputStream()) {
            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String base64 = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        }
    }
}
