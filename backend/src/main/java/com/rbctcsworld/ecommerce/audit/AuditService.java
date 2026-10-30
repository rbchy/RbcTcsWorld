package com.rbctcsworld.ecommerce.audit;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes and reads the audit trail (audit_log).
 *
 * Writing: services publish an AuditEvent inside their transaction. Just before that transaction commits,
 * this listener first flushes the pending entity changes (so every row lock the transaction needs is already
 * taken), then locks the chain head, appends one row per changed field and moves the head. The head is the
 * LAST lock any transaction takes, so audited transactions cannot deadlock on it. If writing fails, the
 * whole transaction rolls back: no change without its audit row.
 *
 * Reading (store admin): filter, verify the hash chain, export as CSV.
 */
@Service
public class AuditService {

    public static final int DEFAULT_LIMIT = 200;
    public static final int MAX_LIMIT = 1000;

    private static final RowMapper<AuditRecord> ROW = (rs, i) -> new AuditRecord(rs.getLong("seq"),
            rs.getTimestamp("occurred_at").toLocalDateTime(), rs.getString("actor"), rs.getString("entity_type"),
            rs.getLong("entity_id"), rs.getString("action"), rs.getString("field"), rs.getString("old_value"),
            rs.getString("new_value"), rs.getString("prev_hash"), rs.getString("hash"));
    private static final String COLUMNS =
            "seq, occurred_at, actor, entity_type, entity_id, action, field, old_value, new_value, prev_hash, hash";

    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbc, EntityManager em, Clock clock) {
        this.jdbc = jdbc;
        this.em = em;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AuditEvent event) {
        if (event.changes().isEmpty()) return;
        em.flush();
        append(event.actor(), event.changes());
    }

    void append(String actor, List<AuditChange> changes) {
        long[] seq = new long[1];
        String[] prev = new String[1];
        jdbc.query("select last_seq, last_hash from audit_head where id = 1 for update", rs -> {
            seq[0] = rs.getLong(1);
            prev[0] = rs.getString(2);
        });
        if (prev[0] == null) throw new IllegalStateException("audit_head row is missing");
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        for (AuditChange c : changes) {
            seq[0]++;
            String hash = AuditChain.hash(prev[0], seq[0], now, actor, c.entityType(), c.entityId(), c.action(),
                    c.field(), c.oldValue(), c.newValue());
            jdbc.update("insert into audit_log (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    seq[0], now, actor, c.entityType(), c.entityId(), c.action(), c.field(), c.oldValue(),
                    c.newValue(), prev[0], hash);
            prev[0] = hash;
        }
        jdbc.update("update audit_head set last_seq = ?, last_hash = ? where id = 1", seq[0], prev[0]);
    }

    /** Newest first. Every filter is optional; limit 1..MAX_LIMIT (400 otherwise). */
    @Transactional(readOnly = true)
    public List<AuditRecord> find(String entityType, Long entityId, String actor, Integer limit) {
        int n = limit == null ? DEFAULT_LIMIT : limit;
        if (n < 1 || n > MAX_LIMIT) throw new BusinessRuleException("limit must be between 1 and " + MAX_LIMIT);
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from audit_log where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (entityType != null && !entityType.isBlank()) {
            sql.append(" and entity_type = ?");
            args.add(entityType.trim().toUpperCase(Locale.ROOT));
        }
        if (entityId != null) {
            sql.append(" and entity_id = ?");
            args.add(entityId);
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" and lower(actor) = ?");
            args.add(actor.trim().toLowerCase(Locale.ROOT));
        }
        sql.append(" order by seq desc limit ").append(n);
        return jdbc.query(sql.toString(), ROW, args.toArray());
    }

    /**
     * Recomputes the whole chain and compares its end with the head.
     *
     * DEF-030: the rows and the head are read in ONE statement. Read in two, a change committed in between (any
     * admin edit, an order) left the head one step ahead of the rows read a moment earlier, and an intact trail was
     * reported as "the newest rows are missing". PostgreSQL gives each statement of a READ COMMITTED transaction its
     * own snapshot, so only a single statement sees the rows and the head at the same moment.
     */
    @Transactional(readOnly = true)
    public AuditChain.Verification verify() {
        List<AuditRecord> rows = new ArrayList<>();
        long[] headSeq = new long[1];
        String[] headHash = new String[1];
        jdbc.query(VERIFY_SQL, rs -> {
            headSeq[0] = rs.getLong("head_seq");                      // 0 / null when the head row is missing
            headHash[0] = rs.getString("head_hash");
            if (rs.getObject("seq") != null) rows.add(ROW.mapRow(rs, rows.size()));
        });
        return AuditChain.verify(rows, headSeq[0], headHash[0]);
    }

    /** Every row with the head beside it; at least one result row (all log columns null) when the trail is empty. */
    static final String VERIFY_SQL = "select (select last_seq from audit_head where id = 1) as head_seq, "
            + "(select last_hash from audit_head where id = 1) as head_hash, "
            + "l.seq, l.occurred_at, l.actor, l.entity_type, l.entity_id, l.action, l.field, l.old_value, l.new_value, "
            + "l.prev_hash, l.hash "
            + "from (select 1 as one) anchor left join audit_log l on 1 = 1 order by l.seq";

    /** The filtered rows as CSV (oldest first), safe to open in a spreadsheet. */
    @Transactional(readOnly = true)
    public String csv(String entityType, Long entityId, String actor) {
        List<AuditRecord> rows = new ArrayList<>(find(entityType, entityId, actor, MAX_LIMIT));
        java.util.Collections.reverse(rows);
        StringBuilder s = new StringBuilder("seq,occurred_at,actor,entity_type,entity_id,action,field,old_value,new_value,hash\r\n");
        for (AuditRecord r : rows) {
            s.append(r.seq()).append(',').append(cell(r.occurredAt().toString())).append(',').append(cell(r.actor()))
                    .append(',').append(cell(r.entityType())).append(',').append(r.entityId()).append(',')
                    .append(cell(r.action())).append(',').append(cell(r.field())).append(',')
                    .append(cell(r.oldValue())).append(',').append(cell(r.newValue())).append(',')
                    .append(cell(r.hash())).append("\r\n");
        }
        return s.toString();
    }

    /**
     * One CSV cell: quoted, inner quotes doubled. A value that a spreadsheet would run as a formula
     * (=, +, -, @, tab, carriage return at the start) gets a leading apostrophe - CSV injection (OWASP).
     */
    static String cell(String value) {
        return com.rbctcsworld.ecommerce.common.Csv.cell(value);
    }
}
