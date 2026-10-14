package com.rbctcsworld.ecommerce.common;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.Role;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN account on startup if it does not exist.
 * Credentials come from app.seed.admin-* (override with env vars ADMIN_EMAIL / ADMIN_PASSWORD).
 * In a shared environment ('prod' profile or the public demo) the published development password is refused,
 * so the application does not start with an admin account everybody can look up on GitHub.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminEmail;
    private final String adminPassword;

    /** The development password from application.yml and the README. */
    static final String DEV_DEFAULT_PASSWORD = "Admin@12345";

    public DataSeeder(UserRepository users, PasswordEncoder encoder,
                      @Value("${app.seed.admin-email}") String adminEmail,
                      @Value("${app.seed.admin-password}") String adminPassword,
                      Environment env) {
        if (SharedEnvironment.isShared(env) && DEV_DEFAULT_PASSWORD.equals(adminPassword)) {
            throw new IllegalStateException("Refusing to start: the public development admin password is used in a shared"
                    + " environment. Set the ADMIN_PASSWORD environment variable.");
        }
        this.users = users;
        this.encoder = encoder;
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (users.findByEmail(adminEmail).isEmpty()) {
            users.save(new AppUser(adminEmail, encoder.encode(adminPassword), Role.ADMIN));
            log.info("Seeded admin user {}", adminEmail);
        }
    }
}
