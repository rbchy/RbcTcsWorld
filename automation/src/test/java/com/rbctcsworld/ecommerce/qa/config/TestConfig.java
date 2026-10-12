package com.rbctcsworld.ecommerce.qa.config;

/**
 * One place for environment settings. Every value can be overridden with -Dkey=value
 * (Maven command line, Eclipse run configuration or CI).
 */
public final class TestConfig {

    private TestConfig() {
    }

    public static String baseUrl() {
        return get("baseUrl", "http://localhost:8081");
    }

    /**
     * A second backend instance on the same database and Redis (CI starts one on 8082). Empty = not
     * available; tests that need it are skipped.
     */
    public static String secondBaseUrl() {
        return get("secondBaseUrl", "");
    }

    public static String uiUrl() {
        return get("uiUrl", "http://localhost:5173");
    }

    public static String adminEmail() {
        return get("adminEmail", "admin@rbctcsworld.com");
    }

    public static String adminPassword() {
        return get("adminPassword", "Admin@12345");
    }

    /** Same database the backend uses (docker-compose.yml). */
    public static String dbUrl() {
        return get("dbUrl", "jdbc:postgresql://localhost:5432/ecommerce");
    }

    public static String dbUser() {
        return get("dbUser", "ecommerce");
    }

    public static String dbPassword() {
        return get("dbPassword", "ecommerce");
    }

    public static boolean headless() {
        return Boolean.parseBoolean(get("headless", "true"));
    }

    public static String browser() {
        return get("browser", "chrome");
    }

    private static String get(String key, String defaultValue) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key.toUpperCase());
        }
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
