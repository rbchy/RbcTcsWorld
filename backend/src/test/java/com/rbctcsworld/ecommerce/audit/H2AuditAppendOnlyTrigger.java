package com.rbctcsworld.ecommerce.audit;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

/** H2 twin of the PostgreSQL trigger in db/vendor/postgresql/V15: audit rows are append-only. */
public class H2AuditAppendOnlyTrigger implements Trigger {

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("audit_log is append-only: " + (newRow == null ? "DELETE" : "UPDATE") + " is not allowed");
    }
}
