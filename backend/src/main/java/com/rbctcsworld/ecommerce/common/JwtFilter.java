package com.rbctcsworld.ecommerce.common;

import com.rbctcsworld.ecommerce.auth.AppUser;

import com.rbctcsworld.ecommerce.auth.JwtService;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads "Authorization: Bearer <token>", validates it and puts the user (with ROLE_CUSTOMER
 * or ROLE_ADMIN) into the security context. An invalid token simply leaves the request
 * unauthenticated, so protected endpoints answer 401.
 */
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserRepository users;

    public JwtFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String email = jwt.email(header.substring(7));
                users.findByEmail(email).filter(AppUser::isActive).ifPresent(u -> SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(u.getEmail(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())))));
            } catch (Exception invalidToken) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
