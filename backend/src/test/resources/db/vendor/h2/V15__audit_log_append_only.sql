-- H2 (backend tests): same rule as PostgreSQL's V15 - audit rows can be inserted, never changed or deleted.
CREATE TRIGGER audit_log_no_change BEFORE UPDATE, DELETE ON audit_log
    FOR EACH ROW CALL 'com.rbctcsworld.ecommerce.audit.H2AuditAppendOnlyTrigger';
