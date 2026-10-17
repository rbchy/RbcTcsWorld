package com.rbctcsworld.ecommerce.qa.database;

import com.rbctcsworld.ecommerce.qa.config.TestConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only JDBC helper for database validation. Tests NEVER write to the database directly (refusedWrite
 * only proves a write is refused, and rolls back):
 * all changes go through the API, the database is only used to verify what the API did.
 */
public final class DatabaseUtils {

    private DatabaseUtils() {
    }

    public static List<Map<String, Object>> query(String sql, Object... params) {
        try (Connection c = DriverManager.getConnection(TestConfig.dbUrl(), TestConfig.dbUser(), TestConfig.dbPassword());
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int col = 1; col <= md.getColumnCount(); col++) {
                        row.put(md.getColumnLabel(col), rs.getObject(col));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB query failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    /** Single value of the first column of the first row (or null). */
    public static Object scalar(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0).values().iterator().next();
    }

    /**
     * Tries a write that the database must refuse (e.g. editing the audit trail) and returns the database's
     * error message. The attempt runs in a transaction that is always rolled back, so even if the database
     * wrongly accepted it, nothing would change. Fails when the write is NOT refused.
     */
    public static String refusedWrite(String sql) {
        try (Connection c = DriverManager.getConnection(TestConfig.dbUrl(), TestConfig.dbUser(), TestConfig.dbPassword())) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.executeUpdate();
            } catch (SQLException refused) {
                return refused.getMessage();
            } finally {
                c.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB connection failed: " + e.getMessage(), e);
        }
        throw new AssertionError("the database accepted: " + sql);
    }

    public static boolean isAvailable() {
        try (Connection ignored = DriverManager.getConnection(TestConfig.dbUrl(), TestConfig.dbUser(), TestConfig.dbPassword())) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
