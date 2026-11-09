package app.nzyme.core.database.usage;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.DataCategory;
import app.nzyme.core.database.DataTableInformation;
import app.nzyme.core.database.DatabaseImpl;
import app.nzyme.core.security.authentication.db.OrganizationEntry;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.core.util.Tools;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DatabaseUsage {

    private static final Logger LOG = LogManager.getLogger(DatabaseUsage.class);

    public enum Scope {
        GLOBAL, ORGANIZATION, TENANT
    }

    public record Entry(Scope scope, @Nullable UUID organizationId, @Nullable UUID tenantId, DataCategory category,
                        @Nullable Long bytes, @Nullable Long rows, boolean rowsEstimated) {

        // Only the global scope is measured. Organization and tenant sizes are apportioned by row share.
        public boolean bytesEstimated() {
            return scope != Scope.GLOBAL;
        }

    }

    public record Snapshot(DateTime calculatedAt, List<Entry> entries, boolean estimated) {

        public Optional<Entry> find(Scope scope, @Nullable UUID organizationId, @Nullable UUID tenantId, DataCategory category) {
            for (Entry e : entries) {
                if (e.scope() == scope && e.category() == category
                        && java.util.Objects.equals(e.organizationId(), organizationId)
                        && java.util.Objects.equals(e.tenantId(), tenantId)) {
                    return Optional.of(e);
                }
            }

            return Optional.empty();
        }

    }

    private final NzymeNode nzyme;

    public DatabaseUsage(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public Snapshot getSnapshot() {
        List<Entry> stored = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT scope, organization_id, tenant_id, category, bytes, rows, calculated_at " +
                                "FROM database_usage_snapshots")
                        .map((rs, ctx) -> new Entry(
                                Scope.valueOf(rs.getString("scope")),
                                rs.getObject("organization_id", UUID.class),
                                rs.getObject("tenant_id", UUID.class),
                                DataCategory.valueOf(rs.getString("category")),
                                rs.getObject("bytes", Long.class),
                                rs.getObject("rows", Long.class),
                                false
                        ))
                        .list()
        );

        if (!stored.isEmpty()) {
            Timestamp calculatedAt = nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT MIN(calculated_at) FROM database_usage_snapshots")
                            .mapTo(Timestamp.class)
                            .one()
            );

            return new Snapshot(new DateTime(calculatedAt), stored, false);
        }

        return estimate();
    }

    private Snapshot estimate() {
        DatabaseImpl database = (DatabaseImpl) nzyme.getDatabase();

        Map<String, Long> reltuples = Maps.newHashMap();
        database.useHandle(handle -> handle.createQuery("SELECT c.relname, c.reltuples::bigint AS reltuples " +
                        "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
                        "WHERE n.nspname = 'public' AND c.relkind = 'r'")
                .map((rs, ctx) -> reltuples.put(rs.getString("relname"), Math.max(0, rs.getLong("reltuples"))))
                .list());

        List<Entry> entries = Lists.newArrayList();
        for (DataCategory category : DataCategory.values()) {
            long bytes = 0;
            long rows = 0;
            for (DataTableInformation table : database.getTablesOfDataCategory(category)) {
                bytes += database.getTableSize(table.getTableName());
                rows += reltuples.getOrDefault(table.getTableName(), 0L);
            }

            entries.add(new Entry(Scope.GLOBAL, null, null, category, bytes, rows, true));
        }

        return new Snapshot(DateTime.now(), entries, true);
    }

    public int calculate(Handle handle) {
        DatabaseImpl database = (DatabaseImpl) nzyme.getDatabase();
        DateTime now = DateTime.now();
        List<Entry> entries = Lists.newArrayList();

        List<OrganizationEntry> organizations = nzyme.getAuthenticationService().findAllOrganizations();

        for (DataCategory category : DataCategory.values()) {
            List<DataTableInformation> tables = database.getTablesOfDataCategory(category);

            Map<String, Long> tableBytes = Maps.newHashMap();
            for (DataTableInformation table : tables) {
                tableBytes.put(table.getTableName(), database.getTableSize(table.getTableName()));
            }

            Map<String, Long> globalRows = countRows(handle, tables, null, null);
            entries.add(new Entry(Scope.GLOBAL, null, null, category, sum(tableBytes), sum(globalRows), false));

            for (OrganizationEntry org : organizations) {
                Map<String, Long> orgRows = countRows(handle, tables, org.uuid(), null);
                entries.add(new Entry(Scope.ORGANIZATION, org.uuid(), null, category,
                        apportion(tableBytes, globalRows, orgRows), sum(orgRows), false));

                for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfOrganization(org.uuid())) {
                    Map<String, Long> tenantRows = countRows(handle, tables, org.uuid(), tenant.uuid());
                    entries.add(new Entry(Scope.TENANT, org.uuid(), tenant.uuid(), category,
                            apportion(tableBytes, globalRows, tenantRows), sum(tenantRows), false));
                }
            }
        }

        handle.useTransaction(tx -> {
            tx.createUpdate("DELETE FROM database_usage_snapshots").execute();

            for (Entry e : entries) {
                tx.createUpdate("INSERT INTO database_usage_snapshots(scope, organization_id, tenant_id, category, " +
                                "bytes, rows, calculated_at) VALUES(:scope, :organization_id, :tenant_id, :category, " +
                                ":bytes, :rows, :calculated_at)")
                        .bind("scope", e.scope().name())
                        .bind("organization_id", e.organizationId())
                        .bind("tenant_id", e.tenantId())
                        .bind("category", e.category().name())
                        .bind("bytes", e.bytes())
                        .bind("rows", e.rows())
                        .bind("calculated_at", now)
                        .execute();
            }
        });

        LOG.debug("Calculated database usage snapshot with <{}> entries.", entries.size());
        return entries.size();
    }

    private Map<String, Long> countRows(Handle handle, List<DataTableInformation> tables, @Nullable UUID organizationId, @Nullable UUID tenantId) {
        List<UUID> tapUuids = Tools.getTapUuids(nzyme, organizationId, tenantId);

        Map<String, Long> rows = Maps.newHashMap();
        for (DataTableInformation table : tables) {
            if (table.isTenantScoped()) {
                rows.put(table.getTableName(), DataTableInformation.bindTenantScope(handle.createQuery(table.getRowCountQuery()),
                                organizationId, tenantId)
                        .mapTo(Long.class)
                        .one());
                continue;
            }

            if (tapUuids.isEmpty()) {
                rows.put(table.getTableName(), 0L);
                continue;
            }

            rows.put(table.getTableName(), handle.createQuery(table.getRowCountQuery())
                    .bindList("taps", tapUuids)
                    .mapTo(Long.class)
                    .one());
        }

        return rows;
    }

    private static long apportion(Map<String, Long> tableBytes, Map<String, Long> globalRows, Map<String, Long> scopeRows) {
        double bytes = 0;
        for (Map.Entry<String, Long> table : tableBytes.entrySet()) {
            long total = globalRows.getOrDefault(table.getKey(), 0L);
            if (total <= 0) {
                continue;
            }

            bytes += table.getValue() * ((double) scopeRows.getOrDefault(table.getKey(), 0L) / total);
        }

        return Math.round(bytes);
    }

    private static long sum(Map<String, Long> values) {
        long sum = 0;
        for (Long v : values.values()) {
            sum += v;
        }
        return sum;
    }

}
