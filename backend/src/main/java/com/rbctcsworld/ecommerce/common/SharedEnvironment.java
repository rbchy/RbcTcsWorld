package com.rbctcsworld.ecommerce.common;

import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * "Shared" = anything other people can reach: the 'prod' profile or the public demo (DEMO_MODE=true).
 * There the public development defaults (JWT secret, admin password) must never be used.
 */
public final class SharedEnvironment {

    public static final String DEMO_PROPERTY = "app.demo.enabled";

    private SharedEnvironment() {
    }

    public static boolean isDemo(Environment env) {
        return env.getProperty(DEMO_PROPERTY, Boolean.class, false);
    }

    public static boolean isShared(Environment env) {
        return isDemo(env) || Arrays.asList(env.getActiveProfiles()).contains("prod");
    }
}
