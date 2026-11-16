package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.segmentation.filters.FlowFilters;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class Flows {

    private static final String NO_ASN = "noasn";
    private static final String NO_COUNTRY = "-";

    public static final String BROADCAST_PREFIX = "bc:";
    public static final String BROADCAST_KEYS = BROADCAST_PREFIX + "%";

    public record Crossing(long sourceGroup,
                           long destinationGroup,
                           String l4Type,
                           int port,
                           String protocol,
                           Outcome outcome,
                           long sessions,
                           long continued,
                           DateTime firstSeen,
                           DateTime lastSeen,
                           List<UUID> taps) { }

    public record Prefilter(String sql, Map<String, Object> bindings) { }

    public enum OrderColumn {

        SESSIONS(FlowFilters.SESSIONS),
        LAST_SEEN("last_seen"),
        FIRST_SEEN("first_seen"),
        SOURCE("source_inet"),
        DESTINATION("destination_inet"),
        PORT("destination_port"),
        OUTCOME("outcome");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record Page(long total, List<Flow> flows) { }

    private static final String FLOW_IDENTITY = "source_key, destination_key, l4_type, destination_port, protocol, outcome";

    private final NzymeNode nzyme;

    public Flows(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public static DateTime bucketOf(DateTime timestamp) {
        return timestamp.withZone(DateTimeZone.UTC).hourOfDay().roundFloorCopy();
    }

    public static String key(Endpoint endpoint) {
        if (endpoint.broadcast()) {
            return BROADCAST_PREFIX + (endpoint.hasAddress() ? InetAddresses.toAddrString(endpoint.address()) : "fold");
        }

        if (endpoint.internal() && endpoint.hasAddress()) {
            return "ip:" + InetAddresses.toAddrString(endpoint.address());
        }

        if (endpoint.internal()) {
            return foldedKey(endpoint.segment());
        }

        StringBuilder key = new StringBuilder();
        if (endpoint.segment() != null) {
            key.append("seg:").append(endpoint.segment().id()).append("/");
        }

        key.append(endpoint.asn() == null ? NO_ASN : "asn:" + endpoint.asn())
                .append("/")
                .append(endpoint.countryCode() == null ? NO_COUNTRY : endpoint.countryCode().toUpperCase());

        return key.toString();
    }

    public static String foldedKey(@Nullable Segment segment) {
        return "fold:" + (segment == null ? "none" : segment.id());
    }

    private static final String UPSERT_FLOWS = "INSERT INTO segmentation_flows(organization_id, tenant_id, bucket, " +
            "tap_uuid, source_key, source_address, source_internal, source_segment_id, source_asn, " +
            "source_country_code, destination_key, destination_address, destination_internal, " +
            "destination_segment_id, destination_asn, destination_country_code, l4_type, destination_port, " +
            "protocol, outcome, sessions, continued, first_seen, last_seen) VALUES(:organization_id, :tenant_id, " +
            ":bucket, :tap_uuid, :source_key, :source_address::inet, :source_internal, :source_segment_id, " +
            ":source_asn, :source_country_code, :destination_key, :destination_address::inet, " +
            ":destination_internal, :destination_segment_id, :destination_asn, :destination_country_code, " +
            ":l4_type, :destination_port, :protocol, :outcome, :sessions, :continued, :first_seen, :last_seen) " +
            "ON CONFLICT (organization_id, tenant_id, bucket, tap_uuid, source_key, destination_key, l4_type, " +
            "destination_port, protocol, outcome) DO UPDATE SET " +
            "sessions = segmentation_flows.sessions + EXCLUDED.sessions, " +
            "continued = segmentation_flows.continued + EXCLUDED.continued, " +
            "source_internal = EXCLUDED.source_internal, " +
            "source_segment_id = EXCLUDED.source_segment_id, " +
            "destination_internal = EXCLUDED.destination_internal, " +
            "destination_segment_id = EXCLUDED.destination_segment_id, " +
            "first_seen = LEAST(segmentation_flows.first_seen, EXCLUDED.first_seen), " +
            "last_seen = GREATEST(segmentation_flows.last_seen, EXCLUDED.last_seen)";

    private static final String UPSERT_CROSSINGS = "INSERT INTO segmentation_crossings(organization_id, tenant_id, " +
            "bucket, tap_uuid, source_group, destination_group, l4_type, destination_port, protocol, outcome, " +
            "sessions, continued, first_seen, last_seen) VALUES(:organization_id, :tenant_id, :bucket, :tap_uuid, " +
            ":source_group, :destination_group, :l4_type, :destination_port, :protocol, :outcome, :sessions, " +
            ":continued, :first_seen, :last_seen) " +
            "ON CONFLICT (organization_id, tenant_id, bucket, tap_uuid, source_group, destination_group, l4_type, " +
            "destination_port, protocol, outcome) DO UPDATE SET " +
            "sessions = segmentation_crossings.sessions + EXCLUDED.sessions, " +
            "continued = segmentation_crossings.continued + EXCLUDED.continued, " +
            "first_seen = LEAST(segmentation_crossings.first_seen, EXCLUDED.first_seen), " +
            "last_seen = GREATEST(segmentation_crossings.last_seen, EXCLUDED.last_seen)";

    // Rows are written in key order so that two writers can never lock the same rows in a different order.
    public void write(Handle handle, UUID organizationId, UUID tenantId, Collection<Flow> flows) {
        if (flows.isEmpty()) {
            return;
        }

        TreeMap<String, Flow> ordered = Maps.newTreeMap();
        TreeMap<String, Flow> crossings = Maps.newTreeMap();
        for (Flow flow : flows) {
            String shared = "|" + flow.l4Type() + "|" + flow.port() + "|" + flow.protocol() + "|" + flow.outcome();
            String prefix = flow.bucket().getMillis() + "|" + flow.tap() + "|";

            ordered.merge(prefix + flow.sourceKey() + "|" + flow.destinationKey() + shared, flow, Flows::add);
            crossings.merge(prefix + flow.sourceGroup() + "|" + flow.destinationGroup() + shared, flow, Flows::add);
        }

        PreparedBatch flowBatch = handle.prepareBatch(UPSERT_FLOWS);
        for (Flow flow : ordered.values()) {
            flowBatch.bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("bucket", flow.bucket())
                    .bind("tap_uuid", flow.tap())
                    .bind("source_key", flow.sourceKey())
                    .bind("source_address", address(flow.source()))
                    .bind("source_internal", flow.source().internal())
                    .bind("source_segment_id", flow.source().segment() == null ? null : flow.source().segment().id())
                    .bind("source_asn", flow.source().asn())
                    .bind("source_country_code", flow.source().countryCode())
                    .bind("destination_key", flow.destinationKey())
                    .bind("destination_address", address(flow.destination()))
                    .bind("destination_internal", flow.destination().internal())
                    .bind("destination_segment_id", flow.destination().segment() == null ? null : flow.destination().segment().id())
                    .bind("destination_asn", flow.destination().asn())
                    .bind("destination_country_code", flow.destination().countryCode())
                    .bind("l4_type", flow.l4Type())
                    .bind("destination_port", flow.port())
                    .bind("protocol", flow.protocol())
                    .bind("outcome", flow.outcome().name())
                    .bind("sessions", flow.sessions())
                    .bind("continued", flow.continued())
                    .bind("first_seen", flow.firstSeen())
                    .bind("last_seen", flow.lastSeen())
                    .add();
        }
        flowBatch.execute();

        PreparedBatch crossingBatch = handle.prepareBatch(UPSERT_CROSSINGS);
        for (Flow flow : crossings.values()) {
            crossingBatch.bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("bucket", flow.bucket())
                    .bind("tap_uuid", flow.tap())
                    .bind("source_group", flow.sourceGroup())
                    .bind("destination_group", flow.destinationGroup())
                    .bind("l4_type", flow.l4Type())
                    .bind("destination_port", flow.port())
                    .bind("protocol", flow.protocol())
                    .bind("outcome", flow.outcome().name())
                    .bind("sessions", flow.sessions())
                    .bind("continued", flow.continued())
                    .bind("first_seen", flow.firstSeen())
                    .bind("last_seen", flow.lastSeen())
                    .add();
        }
        crossingBatch.execute();
    }

    private static Flow add(Flow a, Flow b) {
        return a.withSessions(
                a.sessions() + b.sessions(),
                a.continued() + b.continued(),
                a.firstSeen().isBefore(b.firstSeen()) ? a.firstSeen() : b.firstSeen(),
                a.lastSeen().isAfter(b.lastSeen()) ? a.lastSeen() : b.lastSeen()
        );
    }

    @Nullable
    private static String address(Endpoint endpoint) {
        return (endpoint.internal() || endpoint.broadcast()) && endpoint.hasAddress()
                ? InetAddresses.toAddrString(endpoint.address()) : null;
    }

    // Where several taps recorded the same crossing, the tap that saw the most of it counts. Adding them up would
    // count a connection once per tap on its path.
    public List<Crossing> crossings(UUID organizationId, UUID tenantId, TimeRange timeRange, List<UUID> readableTaps) {
        if (readableTaps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT source_group, destination_group, l4_type, destination_port, protocol, " +
                                "outcome, MAX(sessions) AS sessions, MAX(continued) AS continued, " +
                                "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                                "string_agg(DISTINCT tap_uuid::text, ',') AS taps FROM (" +
                                "SELECT tap_uuid, source_group, destination_group, l4_type, destination_port, " +
                                "protocol, outcome, SUM(sessions) AS sessions, SUM(continued) AS continued, " +
                                "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                                "FROM segmentation_crossings WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND tap_uuid IN (<taps>) " +
                                "AND bucket >= date_trunc('hour', :tr_from::timestamptz, 'UTC') " +
                                "AND bucket <= :tr_to GROUP BY 1, 2, 3, 4, 5, 6, 7) AS per_tap " +
                                "GROUP BY 1, 2, 3, 4, 5, 6")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("taps", readableTaps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .map((rs, ctx) -> {
                            List<UUID> taps = Lists.newArrayList();
                            for (String tap : rs.getString("taps").split(",")) {
                                taps.add(UUID.fromString(tap));
                            }

                            return new Crossing(
                                    rs.getLong("source_group"),
                                    rs.getLong("destination_group"),
                                    rs.getString("l4_type"),
                                    rs.getInt("destination_port"),
                                    rs.getString("protocol"),
                                    Outcome.valueOf(rs.getString("outcome")),
                                    rs.getLong("sessions"),
                                    rs.getLong("continued"),
                                    new DateTime(rs.getTimestamp("first_seen")),
                                    new DateTime(rs.getTimestamp("last_seen")),
                                    taps
                            );
                        })
                        .list()
        );
    }

    // The sides are resolved against the passed definitions and not taken from what was stored with the flow, so that
    // a segment that was just edited is judged as it is now.
    public List<Flow> read(UUID organizationId,
                           UUID tenantId,
                           TimeRange timeRange,
                           List<UUID> taps,
                           Prefilter prefilter,
                           MembershipResolver resolver,
                           int limit) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT tap_uuid, source_key, host(source_address) AS source_address, " +
                                "bool_or(source_internal) AS source_internal, " +
                                "MAX(source_segment_id) AS source_segment_id, MAX(source_asn) AS source_asn, " +
                                "MAX(source_country_code) AS source_country_code, destination_key, " +
                                "host(destination_address) AS destination_address, " +
                                "bool_or(destination_internal) AS destination_internal, " +
                                "MAX(destination_segment_id) AS destination_segment_id, " +
                                "MAX(destination_asn) AS destination_asn, " +
                                "MAX(destination_country_code) AS destination_country_code, l4_type, " +
                                "destination_port, protocol, outcome, SUM(sessions) AS sessions, " +
                                "SUM(continued) AS continued, MIN(first_seen) AS first_seen, " +
                                "MAX(last_seen) AS last_seen FROM segmentation_flows " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND tap_uuid IN (<taps>) " +
                                "AND bucket >= date_trunc('hour', :tr_from::timestamptz, 'UTC') AND bucket <= :tr_to " +
                                prefilter.sql() +
                                "GROUP BY tap_uuid, source_key, source_address, destination_key, " +
                                "destination_address, l4_type, destination_port, protocol, outcome " +
                                "ORDER BY SUM(sessions) + SUM(continued) DESC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bindList("taps", taps)
                        .bindMap(prefilter.bindings())
                        .map((rs, ctx) -> map(rs, resolver, timeRange))
                        .list()
        );
    }

    // Like in the crossings, the tap that saw the most of a flow counts where several taps recorded it.
    public Page page(UUID organizationId,
                     UUID tenantId,
                     TimeRange timeRange,
                     List<UUID> taps,
                     Prefilter prefilter,
                     Filters filters,
                     OrderColumn orderColumn,
                     OrderDirection orderDirection,
                     int limit,
                     int offset,
                     MembershipResolver resolver) {
        if (taps.isEmpty()) {
            return new Page(0, Collections.emptyList());
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new FlowFilters());

        String merged = "WITH per_tap AS (SELECT tap_uuid, source_key, source_address, destination_key, " +
                "destination_address, l4_type, destination_port, protocol, outcome, " +
                "bool_or(source_internal) AS source_internal, MAX(source_segment_id) AS source_segment_id, " +
                "MAX(source_asn) AS source_asn, MAX(source_country_code) AS source_country_code, " +
                "bool_or(destination_internal) AS destination_internal, " +
                "MAX(destination_segment_id) AS destination_segment_id, MAX(destination_asn) AS destination_asn, " +
                "MAX(destination_country_code) AS destination_country_code, SUM(sessions) AS sessions, " +
                "SUM(continued) AS continued, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                "FROM segmentation_flows WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                "AND tap_uuid IN (<taps>) " +
                "AND bucket >= date_trunc('hour', :tr_from::timestamptz, 'UTC') AND bucket <= :tr_to " +
                prefilter.sql() + filterFragment.whereSql() +
                "GROUP BY tap_uuid, source_key, source_address, destination_key, destination_address, l4_type, " +
                "destination_port, protocol, outcome), " +
                "merged AS (SELECT DISTINCT ON (" + FLOW_IDENTITY + ") tap_uuid, source_key, " +
                "source_address AS source_inet, source_internal, source_segment_id, source_asn, " +
                "source_country_code, destination_key, destination_address AS destination_inet, " +
                "destination_internal, destination_segment_id, destination_asn, destination_country_code, " +
                "l4_type, destination_port, protocol, outcome, sessions, continued, " +
                "MIN(first_seen) OVER flow AS first_seen, MAX(last_seen) OVER flow AS last_seen FROM per_tap " +
                "WINDOW flow AS (PARTITION BY " + FLOW_IDENTITY + ") " +
                "ORDER BY " + FLOW_IDENTITY + ", sessions + continued DESC, tap_uuid ASC) ";

        return nzyme.getDatabase().withHandle(handle -> {
            long[] total = new long[]{0};

            List<Flow> flows = handle.createQuery(merged + "SELECT *, host(source_inet) AS source_address, " +
                            "host(destination_inet) AS destination_address, COUNT(*) OVER () AS total " +
                            "FROM merged WHERE TRUE " + filterFragment.havingSql() +
                            "ORDER BY <order_column> <order_direction> NULLS LAST, source_key ASC, " +
                            "destination_key ASC, destination_port ASC, l4_type ASC, protocol ASC, outcome ASC " +
                            "LIMIT :limit OFFSET :offset")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to())
                    .bindList("taps", taps)
                    .bindMap(prefilter.bindings())
                    .bindMap(filterFragment.bindings())
                    .bind("limit", limit)
                    .bind("offset", offset)
                    .define("order_column", orderColumn.getColumnName())
                    .define("order_direction", orderDirection)
                    .map((rs, ctx) -> {
                        total[0] = rs.getLong("total");
                        return map(rs, resolver, timeRange);
                    })
                    .list();

            // A page behind the last one has no row to carry the total.
            if (flows.isEmpty() && offset > 0) {
                total[0] = handle.createQuery(merged + "SELECT COUNT(*) FROM merged WHERE TRUE " +
                                filterFragment.havingSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(prefilter.bindings())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one();
            }

            return new Page(total[0], flows);
        });
    }

    private static Flow map(ResultSet rs, MembershipResolver resolver, TimeRange timeRange) throws SQLException {
        UUID tap = UUID.fromString(rs.getString("tap_uuid"));

        return new Flow(
                tap,
                Flows.bucketOf(timeRange.from()),
                endpoint(rs, "source", tap, resolver),
                endpoint(rs, "destination", tap, resolver),
                rs.getString("source_key"),
                rs.getString("destination_key"),
                rs.getString("l4_type"),
                rs.getInt("destination_port"),
                rs.getString("protocol"),
                Outcome.valueOf(rs.getString("outcome")),
                rs.getLong("sessions"),
                rs.getLong("continued"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

    private static Endpoint endpoint(ResultSet rs, String side, UUID tap, MembershipResolver resolver) throws SQLException {
        String address = rs.getString(side + "_address");
        boolean internal = rs.getBoolean(side + "_internal");

        if (rs.getString(side + "_key").startsWith(BROADCAST_PREFIX)) {
            return Endpoint.broadcast(address == null ? null : InetAddresses.forString(address));
        }

        long asnValue = rs.getLong(side + "_asn");
        Long asn = rs.wasNull() ? null : asnValue;
        String countryCode = rs.getString(side + "_country_code");

        if (address != null) {
            InetAddress parsed = InetAddresses.forString(address);
            return resolver.resolve(tap, parsed, internal, asn, countryCode);
        }

        long segmentValue = rs.getLong(side + "_segment_id");
        Long segmentId = rs.wasNull() ? null : segmentValue;

        return resolver.stored(internal, segmentId, asn, countryCode);
    }

}
