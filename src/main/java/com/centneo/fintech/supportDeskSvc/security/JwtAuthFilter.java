package com.centneo.fintech.supportDeskSvc.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtTokenValidator jwtTokenValidator;

    @Value("${jwt.cookie-name:jwt_token}")
    private String cookieName;

    public JwtAuthFilter(JwtTokenValidator jwtTokenValidator) {
        this.jwtTokenValidator = jwtTokenValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractTokenFromCookieOrHeader(request);
            if (token != null) {
                log.debug("JwtAuthFilter: token found (len={})", token.length());
            }

            if (token != null && jwtTokenValidator.validateToken(token)) {
                Claims claims = jwtTokenValidator.getClaims(token);
                String username = claims.getSubject();
                if (username == null) {
                    log.debug("JwtAuthFilter: subject is null in token");
                } else {
                    // roles can be either: ["ROLE_USER"] or [{"authority":"ROLE_USER"}]
                    List<SimpleGrantedAuthority> authorities = extractAuthoritiesFromClaims(claims);

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("JwtAuthFilter: authentication set for user={} authorities={}", username, authorities);
                }
            } else {
                if (token != null) log.debug("JwtAuthFilter: token invalid or expired");
            }
        } catch (Exception e) {
            log.error("JwtAuthFilter exception", e);
            // don't throw — allow filter chain to continue as anonymous if token parsing fails
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookieOrHeader(HttpServletRequest request) {
        // 1. Cookie (HttpOnly cookies are readable server-side via request.getCookies())
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (cookieName.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }

        // 2. Authorization header fallback
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<SimpleGrantedAuthority> extractAuthoritiesFromClaims(Claims claims) {
        Object rolesObj = claims.get("roles");
        if (rolesObj == null) {
            rolesObj = claims.get("role"); // some token producers use "role"
        }
        if (rolesObj == null) {
            return List.of();
        }

        // If it's a list of strings
        if (rolesObj instanceof List<?> rolesList) {
            return rolesList.stream()
                    .map(this::mapRoleObjectToAuthority)
                    .filter(Objects::nonNull)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }

        // If single string
        if (rolesObj instanceof String single) {
            return List.of(new SimpleGrantedAuthority(single));
        }

        return List.of();
    }

    private String mapRoleObjectToAuthority(Object obj) {
        if (obj instanceof String s) {
            return s;
        }
        if (obj instanceof Map<?, ?> m) {
            Object auth = m.get("authority");
            if (auth != null) return auth.toString();
            // some token shapes may use "name" or "role"
            Object name = m.get("name");
            if (name != null) return name.toString();
        }
        return null;
    }
}
