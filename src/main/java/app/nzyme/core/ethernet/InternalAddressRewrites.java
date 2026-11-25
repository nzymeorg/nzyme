package app.nzyme.core.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// Not rewritten: dns_log, whose flags only the reconnaissance detector reads, for the records of the last minutes,
// and the aggregated tables (assets_communication, l4_statistics), whose counts cannot be split afterwards.
public class InternalAddressRewrites {

    private static final Logger LOG = LogManager.getLogger(InternalAddressRewrites.class);

    private record Column(String address, String flag) { }

    private enum Scope {
        TAPS("t.tap_uuid IN (<taps>)"),
        TENANT("t.organization_id = :organization_id AND t.tenant_id = :tenant_id"),
        PORTAL_INTEGRITY_HOPS("t.report_uuid IN (SELECT uuid FROM portal_integrity_reports WHERE tap_uuid IN (<taps>))");

        private final String sql;

        Scope(String sql) {
            this.sql = sql;
        }
    }

    private record Table(String name, String key, Scope scope, List<Column> columns) { }

    private static final List<Table> TABLES = List.of(
            new Table("dns_pairs", "id", Scope.TAPS, List.of(
                    new Column("server_address", "server_address_is_site_local"))),
            new Table("tcp_probes", "id", Scope.TAPS, List.of(
                    new Column("source_address", "source_address_is_site_local"),
                    new Column("destination_address", "destination_address_is_site_local"))),
            new Table("vpn_tunnels", "id", Scope.TAPS, List.of(
                    new Column("source_address", "source_address_is_site_local"),
                    new Column("destination_address", "destination_address_is_site_local"))),
            new Table("ipv6_transition_tunnels", "id", Scope.TAPS, List.of(
                    new Column("source_address", "source_address_is_site_local"),
                    new Column("destination_address", "destination_address_is_site_local"))),
            new Table("portal_integrity_hops", "uuid", Scope.PORTAL_INTEGRITY_HOPS, List.of(
                    new Column("resolved_address", "resolved_address_is_site_local"))),
            new Table("beacons", "id", Scope.TENANT, List.of(
                    new Column("destination_address", "destination_is_site_local")))
    );

    // Too large to read whole. Batches follow the index of each address column: the address, then the time of the
    // last segment.
    private static final String SESSIONS = "l4_sessions";
    private static final List<Column> SESSION_COLUMNS = List.of(
            new Column("source_address", "source_address_is_site_local"),
            new Column("destination_address", "destination_address_is_site_local")
    );

    // Other nodes stamp new records by the old rules until their cached copy expires.
    private static final int WAIT_SECONDS = 30;

    private static final int BATCH_SIZE = 2_000;
    private static final int CLAIM_MINUTES = 5;
    private static final int KEEP_COMPLETED_DAYS = 7;

    private record Request(long id, UUID organizationId, UUID tenantId, String network, String requestedAt) { }

    private final NzymeNode nzyme;
    private final Timer timer;

    public InternalAddressRewrites(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.timer = nzyme.getMetrics().timer(MetricNames.INTERNAL_ADDRESS_REWRITE_TIMER);
    }

    public void request(Handle handle, UUID organizationId, UUID tenantId, CIDR network) {
        handle.createUpdate("INSERT INTO internal_address_rewrites(organization_id, tenant_id, network, " +
                        "requested_at) VALUES(:organization_id, :tenant_id, :network::cidr, clock_timestamp()) " +
                        "ON CONFLICT (organization_id, tenant_id, network) WHERE completed_at IS NULL " +
                        "DO UPDATE SET requested_at = clock_timestamp(), claimed_until = NULL")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("network", network.toString())
                .execute();
    }

    public long countPending(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM internal_address_rewrites " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND completed_at IS NULL")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public void run(long budgetSeconds) {
        Stopwatch elapsed = Stopwatch.createStarted();

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM internal_address_rewrites WHERE completed_at < NOW() - " +
                                "make_interval(days => :days)")
                        .bind("days", KEEP_COMPLETED_DAYS)
                        .execute()
        );

        while (elapsed.elapsed(TimeUnit.SECONDS) < budgetSeconds) {
            List<Request> requests = claim();
            if (requests.isEmpty()) {
                return;
            }

            try (Timer.Context ignored = timer.time()) {
                work(requests, elapsed, budgetSeconds);
            } catch (Exception e) {
                LOG.error("Could not rewrite stored addresses for tenant [{}/{}].",
                        requests.get(0).organizationId(), requests.get(0).tenantId(), e);
                return;
            }
        }
    }

    private List<Request> claim() {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH next AS (SELECT organization_id, tenant_id FROM internal_address_rewrites " +
                                "WHERE completed_at IS NULL " +
                                "AND requested_at < NOW() - make_interval(secs => :wait_seconds) " +
                                "AND (claimed_until IS NULL OR claimed_until < NOW()) " +
                                "ORDER BY requested_at ASC, id ASC LIMIT 1) " +
                                "UPDATE internal_address_rewrites r SET claimed_until = NOW() + " +
                                "make_interval(mins => :claim_minutes) FROM next " +
                                "WHERE r.organization_id = next.organization_id AND r.tenant_id = next.tenant_id " +
                                "AND r.completed_at IS NULL " +
                                "AND r.requested_at < NOW() - make_interval(secs => :wait_seconds) " +
                                "AND (r.claimed_until IS NULL OR r.claimed_until < NOW()) " +
                                "RETURNING r.id, r.organization_id, r.tenant_id, r.network::text AS network, " +
                                "r.requested_at::text AS requested_at")
                        .bind("claim_minutes", CLAIM_MINUTES)
                        .bind("wait_seconds", WAIT_SECONDS)
                        .map((rs, ctx) -> new Request(
                                rs.getLong("id"),
                                UUID.fromString(rs.getString("organization_id")),
                                UUID.fromString(rs.getString("tenant_id")),
                                rs.getString("network"),
                                rs.getString("requested_at")
                        ))
                        .list()
        );
    }

    private void work(List<Request> requests, Stopwatch elapsed, long budgetSeconds) {
        UUID organizationId = requests.get(0).organizationId();
        UUID tenantId = requests.get(0).tenantId();

        // Read from the database and through the parser again before they become part of a statement.
        List<String> networks = requests.stream().map(r -> CIDR.parse(r.network()).toString()).toList();

        // Not the cached rules: the records have to end up as the rules are now.
        InternalAddresses.Rules rules = nzyme.getEthernet().internalAddresses().read(organizationId, tenantId);

        // Deleted taps included: their records are still shown.
        List<UUID> taps = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT uuid FROM taps WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(UUID.class)
                        .list()
        );

        long changed = 0;
        boolean finished = true;

        try {
            if (!taps.isEmpty()) {
                for (String network : networks) {
                    for (Column column : SESSION_COLUMNS) {
                        changed += rewriteSessions(network, column, rules, taps, elapsed, budgetSeconds);

                        // A record that another transaction held at that moment was skipped, not waited for.
                        if (sessionsDisagree(network, column, rules, taps)) {
                            finished = false;
                        }
                    }
                }
            }

            for (Table table : TABLES) {
                if (table.scope() != Scope.TENANT && taps.isEmpty()) {
                    continue;
                }

                changed += rewriteTable(table, networks, rules, organizationId, tenantId, taps, elapsed, budgetSeconds);

                if (tableDisagrees(table, networks, rules, organizationId, tenantId, taps)) {
                    finished = false;
                }
            }
        } catch (OutOfTime e) {
            changed += e.changed;
            finished = false;
        }

        boolean complete = finished;
        for (Request request : requests) {
            nzyme.getDatabase().useHandle(handle ->
                    handle.createUpdate("UPDATE internal_address_rewrites SET claimed_until = NULL, " +
                                    // A request that was made again in the meantime is not done.
                                    "completed_at = CASE WHEN :complete AND requested_at::text = :requested_at " +
                                    "THEN NOW() ELSE NULL END WHERE id = :id AND completed_at IS NULL")
                            .bind("complete", complete)
                            .bind("requested_at", request.requestedAt())
                            .bind("id", request.id())
                            .execute()
            );
        }

        if (changed > 0 || complete) {
            LOG.info("Rewrote the internal flag of [{}] stored addresses in [{}] changed networks of tenant [{}/{}]{}.",
                    changed, networks.size(), organizationId, tenantId, complete ? "" : ", more to do");
        }
    }

    private static final class OutOfTime extends RuntimeException {
        private final long changed;

        private OutOfTime(long changed) {
            super(null, null, false, false);
            this.changed = changed;
        }
    }

    private long rewriteSessions(String network,
                                 Column column,
                                 InternalAddresses.Rules rules,
                                 List<UUID> taps,
                                 Stopwatch elapsed,
                                 long budgetSeconds) {
        String internal = "(" + rules.sqlCondition("t." + column.address()) + ")";

        /*
         * Locked records are skipped so that this never waits for, or deadlocks with, a tap report being written.
         * The batch starts where the last one ended, including it: records with the same address and time may be
         * left there, and the ones that were corrected no longer match.
         */
        String sql = "WITH batch AS (SELECT t.id, t." + column.address() + " AS address, " +
                "t.most_recent_segment_time AS seen FROM " + SESSIONS + " t " +
                "WHERE t.tap_uuid IN (<taps>) AND t." + column.address() + " <<= '" + network + "'::cidr " +
                "AND t." + column.flag() + " IS DISTINCT FROM " + internal + " " +
                "AND (t." + column.address() + ", t.most_recent_segment_time) >= (:address::inet, :seen) " +
                "ORDER BY t." + column.address() + " ASC, t.most_recent_segment_time ASC " +
                "LIMIT :limit FOR UPDATE SKIP LOCKED), " +
                "updated AS (UPDATE " + SESSIONS + " u SET " + column.flag() + " = " +
                internal.replace("t." + column.address(), "u." + column.address()) + " " +
                "FROM batch WHERE u.id = batch.id RETURNING 1) " +
                "SELECT (SELECT COUNT(*) FROM updated) AS changed, last.address::text AS address, last.seen " +
                "FROM (SELECT address, seen FROM batch ORDER BY address DESC, seen DESC LIMIT 1) AS last";

        long changed = 0;
        String address = network.substring(0, network.indexOf('/'));
        DateTime seen = new DateTime(0);

        while (true) {
            if (elapsed.elapsed(TimeUnit.SECONDS) >= budgetSeconds) {
                throw new OutOfTime(changed);
            }

            String from = address;
            DateTime fromSeen = seen;
            List<Object[]> last = nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery(sql)
                            .bindList("taps", taps)
                            .bind("address", from)
                            .bind("seen", fromSeen)
                            .bind("limit", BATCH_SIZE)
                            .map((rs, ctx) -> new Object[]{
                                    rs.getLong("changed"), rs.getString("address"), new DateTime(rs.getTimestamp("seen"))
                            })
                            .list()
            );

            if (last.isEmpty()) {
                return changed;
            }

            changed += (Long) last.get(0)[0];
            address = (String) last.get(0)[1];
            seen = (DateTime) last.get(0)[2];

            if ((Long) last.get(0)[0] < BATCH_SIZE) {
                return changed;
            }
        }
    }

    private boolean sessionsDisagree(String network, Column column, InternalAddresses.Rules rules, List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT EXISTS(SELECT 1 FROM " + SESSIONS + " t WHERE t.tap_uuid IN (<taps>) " +
                                "AND t." + column.address() + " <<= '" + network + "'::cidr " +
                                "AND t." + column.flag() + " IS DISTINCT FROM (" +
                                rules.sqlCondition("t." + column.address()) + "))")
                        .bindList("taps", taps)
                        .mapTo(Boolean.class)
                        .one()
        );
    }

    private long rewriteTable(Table table,
                              List<String> networks,
                              InternalAddresses.Rules rules,
                              UUID organizationId,
                              UUID tenantId,
                              List<UUID> taps,
                              Stopwatch elapsed,
                              long budgetSeconds) {
        String assignments = table.columns().stream()
                .map(c -> c.flag() + " = (" + rules.sqlCondition("u." + c.address()) + ")")
                .collect(Collectors.joining(", "));

        long changed = 0;
        Object last = null;

        while (true) {
            if (elapsed.elapsed(TimeUnit.SECONDS) >= budgetSeconds) {
                throw new OutOfTime(changed);
            }

            String sql = "WITH batch AS (SELECT t." + table.key() + " AS k FROM " + table.name() + " t " +
                    "WHERE " + table.scope().sql + " AND (" + disagreement(table, networks, rules) + ") " +
                    (last == null ? "" : "AND t." + table.key() + " > :last ") +
                    "ORDER BY t." + table.key() + " ASC LIMIT :limit FOR UPDATE SKIP LOCKED), " +
                    "updated AS (UPDATE " + table.name() + " u SET " + assignments + " " +
                    "FROM batch WHERE u." + table.key() + " = batch.k RETURNING 1) " +
                    "SELECT (SELECT COUNT(*) FROM updated) AS changed, " +
                    "(SELECT k FROM batch ORDER BY k DESC LIMIT 1) AS last";

            Object from = last;
            Object[] batch = nzyme.getDatabase().withHandle(handle -> {
                Query query = bindScope(handle.createQuery(sql), table, organizationId, tenantId, taps)
                        .bind("limit", BATCH_SIZE);
                if (from != null) {
                    query.bind("last", from);
                }

                return query.map((rs, ctx) -> new Object[]{rs.getLong("changed"), rs.getObject("last")}).one();
            });

            changed += (Long) batch[0];
            if (batch[1] == null) {
                return changed;
            }

            last = batch[1];
        }
    }

    private boolean tableDisagrees(Table table,
                                   List<String> networks,
                                   InternalAddresses.Rules rules,
                                   UUID organizationId,
                                   UUID tenantId,
                                   List<UUID> taps) {
        String sql = "SELECT EXISTS(SELECT 1 FROM " + table.name() + " t WHERE " + table.scope().sql + " " +
                "AND (" + disagreement(table, networks, rules) + "))";

        return nzyme.getDatabase().withHandle(handle ->
                bindScope(handle.createQuery(sql), table, organizationId, tenantId, taps).mapTo(Boolean.class).one());
    }

    private static String disagreement(Table table, List<String> networks, InternalAddresses.Rules rules) {
        String changed = "ANY('{" + String.join(",", networks) + "}'::cidr[])";

        return table.columns().stream()
                .map(c -> "(t." + c.address() + " <<= " + changed + " AND t." + c.flag() + " IS DISTINCT FROM (" +
                        rules.sqlCondition("t." + c.address()) + "))")
                .collect(Collectors.joining(" OR "));
    }

    private static Query bindScope(Query query, Table table, UUID organizationId, UUID tenantId, List<UUID> taps) {
        if (table.scope() == Scope.TENANT) {
            return query.bind("organization_id", organizationId).bind("tenant_id", tenantId);
        }

        return query.bindList("taps", taps);
    }

}
