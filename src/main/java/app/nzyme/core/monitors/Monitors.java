package app.nzyme.core.monitors;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.monitoring.TimerEntryAverage;
import app.nzyme.core.monitors.db.MonitorEntry;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import org.joda.time.DateTime;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;

import java.sql.Types;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class Monitors {

    public enum OrderColumn {

        NAME("name"),
        TYPE("type"),
        // Monitors that cover all taps (NULL) sort after any explicit tap count.
        TAPS("COALESCE(cardinality(taps), 2147483647)"),
        INTERVAL("interval"),
        LOOKBACK("lookback"),
        TRIGGER_CONDITION("trigger_condition"),
        ENABLED("enabled"),
        ALERTED(MonitorFilters.ALERTED_EXPRESSION),
        LAST_EVENT("last_event"),
        CREATED_AT("created_at"),
        UPDATED_AT("updated_at");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    /*
     * Sorting of the performance list. The execution timers come from the node metrics tables, combined over all
     * nodes, so the list is sorted in memory after the join. Monitors without a timer sort last in both
     * directions. The list of monitors of a tenant is small because every monitor is executed on its interval.
     */
    public enum PerformanceOrderColumn {

        NAME(p -> p.monitor().name().toLowerCase()),
        TYPE(p -> p.monitor().type()),
        ENABLED(p -> p.monitor().enabled()),
        ALERTED(p -> p.monitor().alerted()),
        INTERVAL(p -> p.monitor().interval()),
        LAST_RUN(p -> p.monitor().lastRun() == null ? null : p.monitor().lastRun().getMillis()),
        LAST_EVENT(p -> p.monitor().lastEvent() == null ? null : p.monitor().lastEvent().getMillis()),
        TIMER_MAX(p -> p.executionTimer() == null ? null : p.executionTimer().max()),
        TIMER_MIN(p -> p.executionTimer() == null ? null : p.executionTimer().min()),
        TIMER_MEAN(p -> p.executionTimer() == null ? null : p.executionTimer().mean()),
        TIMER_P99(p -> p.executionTimer() == null ? null : p.executionTimer().p99()),
        TIMER_STDDEV(p -> p.executionTimer() == null ? null : p.executionTimer().stddev()),
        EXECUTIONS(p -> p.executionTimer() == null ? null : p.executionTimer().counter());

        private final Function<MonitorPerformanceEntry, Comparable> key;

        PerformanceOrderColumn(Function<MonitorPerformanceEntry, Comparable> key) {
            this.key = key;
        }

        @SuppressWarnings("unchecked")
        public Comparator<MonitorPerformanceEntry> comparator(OrderDirection direction) {
            Comparator<Comparable> valueOrder = direction == OrderDirection.DESC
                    ? Comparator.reverseOrder() : Comparator.naturalOrder();

            return Comparator.comparing(key, Comparator.nullsLast(valueOrder))
                    .thenComparing(p -> p.monitor().name().toLowerCase());
        }

    }

    private final NzymeNode nzyme;

    public Monitors(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public Optional<MonitorEntry> find(UUID id) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT *, " +
                                "COALESCE(last_event > NOW() - (interval * INTERVAL '1 minute'), false) AS alerted " +
                                "FROM monitors WHERE uuid = :uuid")
                        .bind("uuid", id)
                        .mapTo(MonitorEntry.class)
                        .findOne()
        );
    }

    public List<MonitorEntry> findAllMonitorsOfAllTenants() {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT *, " +
                                "COALESCE(last_event > NOW() - (interval * INTERVAL '1 minute'), false) AS alerted " +
                                "FROM monitors")
                        .mapTo(MonitorEntry.class)
                        .list()
        );
    }

    public long countAllMonitorsOfTenant(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM monitors WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<MonitorEntry> findAllMonitorsOfTenant(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT *, " + MonitorFilters.ALERTED_EXPRESSION + " AS alerted " +
                                "FROM monitors WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "ORDER BY name ASC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(MonitorEntry.class)
                        .list()
        );
    }

    public List<MonitorPerformanceEntry> findAllMonitorsOfTenantWithPerformance(UUID organizationId,
                                                                                UUID tenantId,
                                                                                PerformanceOrderColumn orderColumn,
                                                                                OrderDirection orderDirection,
                                                                                int offset,
                                                                                int limit) {
        Map<String, TimerEntryAverage> timers = nzyme.getClusterManager()
                .findLatestClusterTimersWithPrefix(MetricNames.MONITOR_EXECUTION_TIMER_DATABASE_PREFIX);

        return findAllMonitorsOfTenant(organizationId, tenantId).stream()
                .map(m -> MonitorPerformanceEntry.create(
                        m, timers.get(MetricNames.MONITOR_EXECUTION_TIMER_DATABASE_PREFIX + m.uuid())
                ))
                .sorted(orderColumn.comparator(orderDirection))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    public long countAllMonitorsOfType(MonitorType type, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM monitors WHERE type = :type AND " +
                                "organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("type", type)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<MonitorEntry> findAllMonitorsOfType(MonitorType type,
                                                    UUID organizationId,
                                                    UUID tenantId,
                                                    int offset,
                                                    int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT *, " +
                                "COALESCE(last_event > NOW() - (interval * INTERVAL '1 minute'), false) AS alerted " +
                                "FROM monitors WHERE type = :type AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id ORDER BY name ASC LIMIT :limit OFFSET :offset")
                        .bind("type", type)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("offset", offset)
                        .bind("limit", limit)
                        .mapTo(MonitorEntry.class)
                        .list()
        );
    }

    public long countAllMonitorsOfTypes(List<MonitorType> types,
                                        Filters filters,
                                        UUID organizationId,
                                        UUID tenantId) {
        if (types.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new MonitorFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM monitors WHERE type IN (<types>) AND " +
                                "organization_id = :organization_id AND tenant_id = :tenant_id " +
                                filterFragment.whereSql())
                        .bindList("types", types.stream().map(MonitorType::name).toList())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<MonitorEntry> findAllMonitorsOfTypes(List<MonitorType> types,
                                                     Filters filters,
                                                     OrderColumn orderColumn,
                                                     OrderDirection orderDirection,
                                                     UUID organizationId,
                                                     UUID tenantId,
                                                     int offset,
                                                     int limit) {
        if (types.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new MonitorFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT *, " + MonitorFilters.ALERTED_EXPRESSION + " AS alerted " +
                                "FROM monitors WHERE type IN (<types>) AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction>, name ASC LIMIT :limit OFFSET :offset")
                        .bindList("types", types.stream().map(MonitorType::name).toList())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("offset", offset)
                        .bind("limit", limit)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(MonitorEntry.class)
                        .list()
        );
    }

    public void createMonitor(MonitorType type,
                              String name,
                              String description,
                              @Nullable List<UUID> taps,
                              @Nullable UUID locationId,
                              int triggerCondition,
                              int interval,
                              int lookback,
                              Filters filters,
                              UUID organizationId,
                              UUID tenantId) {
        String[] tapsArray;
        if (taps != null) {
            tapsArray = taps.stream()
                    .map(UUID::toString)
                    .toArray(String[]::new);
        } else {
            tapsArray = null;
        }

        ObjectMapper om = new ObjectMapper();
        String filtersString;
        try {
            filtersString = om.writeValueAsString(filters.filters());
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO monitors(uuid, organization_id, tenant_id, enabled, type, " +
                                "name, description, taps, location_id, trigger_condition, interval, lookback, " +
                                "filters, created_at, updated_at) VALUES(:uuid, :organization_id, :tenant_id, " +
                                "true, :type, :name, :description, :taps, :location_id, :trigger_condition, " +
                                ":interval, :lookback, :filters::jsonb, NOW(), NOW())")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("type", type)
                        .bind("name", name)
                        .bind("description", description)
                        .bindBySqlType("taps", tapsArray, Types.ARRAY)
                        .bindByType("location_id", locationId, UUID.class)
                        .bind("trigger_condition", triggerCondition)
                        .bind("interval", interval)
                        .bind("lookback", lookback)
                        .bind("filters", filtersString)
                        .execute()
        );

    }

    public void updateMonitorMetaInformation(UUID id,
                                             String name,
                                             String description,
                                             @Nullable UUID locationId,
                                             int triggerCondition,
                                             int interval,
                                             int lookback) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE MONITORS set name = :name, description = :description, " +
                                "location_id = :location_id, trigger_condition = :trigger_condition, " +
                                "interval = :interval, lookback = :lookback WHERE uuid = :uuid")
                        .bind("uuid", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bindByType("location_id", locationId, UUID.class)
                        .bind("trigger_condition", triggerCondition)
                        .bind("interval", interval)
                        .bind("lookback", lookback)
                        .execute()
        );

    }

    public void updateMonitorFilterInformation(UUID id, @Nullable List<UUID> taps, Filters filters) {
        String[] tapsArray;
        if (taps != null) {
            tapsArray = taps.stream()
                    .map(UUID::toString)
                    .toArray(String[]::new);
        } else {
            tapsArray = null;
        }

        ObjectMapper om = new ObjectMapper();
        String filtersString;
        try {
            filtersString = om.writeValueAsString(filters.filters());
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE MONITORS SET taps = :taps, filters = :filters::jsonb " +
                                "WHERE uuid = :uuid")
                        .bind("uuid", id)
                        .bindBySqlType("taps", tapsArray, Types.ARRAY)
                        .bind("filters", filtersString)
                        .execute()
        );

    }

    public void deleteMonitor(UUID id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM monitors WHERE uuid = :uuid")
                        .bind("uuid", id)
                        .execute()
        );
    }

    public void setLastEventOfMonitor(UUID id, DateTime lastEvent) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE monitors SET last_event = :last_event WHERE uuid = :uuid")
                        .bind("uuid", id)
                        .bind("last_event", lastEvent)
                        .execute()
        );
    }

    public void setLastExecutionTimeOfMonitor(UUID id, DateTime lastRun) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE monitors SET last_run = :last_run WHERE uuid = :uuid")
                        .bind("uuid", id)
                        .bind("last_run", lastRun)
                        .execute()
        );
    }

    public void setMonitorStatus(UUID id, MonitorStatus status) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE monitors SET status = :status, status_changed_at = NOW() " +
                                "WHERE uuid = :uuid")
                        .bind("uuid", id)
                        .bind("status", status)
                        .execute()
        );
    }

    public void onTapDeleted(UUID tapId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE monitors SET taps = ARRAY_REMOVE(taps, :tap_id)")
                        .bind("tap_id", tapId.toString())
                        .execute()
        );
    }

}
