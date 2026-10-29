package com.rbctcsworld.ecommerce.common;

import com.rbctcsworld.ecommerce.auth.JwtService;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Access rules:
 *  - anyone may register/login, browse products (GET), track a parcel by tracking number and read the store's legal details;
 *  - Stripe may post webhooks without a login: CardPaymentController checks their signature;
 *  - Shippo may post tracking webhooks without a login: ShippoWebhookController checks the secret token;
 *  - Amazon SNS may post SES bounce/complaint reports without a login: SesEventController checks SNS's signature;
 *  - only ADMIN may create, update or delete products and use /api/admin/**;
 *  - everything else (cart, orders...) needs a valid JWT.
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication is JWT only (JwtFilter). Declaring this bean stops Spring Boot from creating an in-memory
     * user with a generated password (printed on every start). Nothing may log in through it.
     */
    @Bean
    UserDetailsService noFormLogin() {
        return username -> { throw new UsernameNotFoundException("Form/basic login is disabled; use /api/auth/login"); };
    }

    @Bean
    SecurityFilterChain filter(HttpSecurity http, JwtService jwt, UserRepository users,
                               RestSecurityHandlers handlers) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
            // Security headers on every response. Spring already adds X-Content-Type-Options: nosniff,
            // X-Frame-Options: DENY and Cache-Control: no-store; these add the rest. The API returns
            // only JSON, so the strictest Content-Security-Policy is possible.
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin")))   // found by OWASP ZAP (rule 90004)
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/actuator/health", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**").permitAll()   // OpenAPI spec (used by OWASP ZAP)
                .requestMatchers(HttpMethod.GET, "/api/tracking/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/store/legal").permitAll()   // who runs the store, for the policy pages
                .requestMatchers("/api/contact").permitAll()   // the contact form: visitors too (ADR-0018)
                .requestMatchers(HttpMethod.GET, "/api/seo/robots.txt", "/api/seo/sitemap.xml").permitAll()   // search engines (ADR-0019)
                .requestMatchers(HttpMethod.POST, "/api/payments/stripe/webhook").permitAll()   // Stripe proves itself by signature
                .requestMatchers(HttpMethod.POST, "/api/shipping/shippo/webhook").permitAll()   // Shippo: secret token in the URL
                .requestMatchers(HttpMethod.POST, "/api/notifications/ses/events").permitAll()  // Amazon SNS: signed messages
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/products", "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(new JwtFilter(jwt, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
