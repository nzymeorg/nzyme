package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.segmentation.filters.FlowFilters;
import app.nzyme.core.ethernet.segmentation.filters.GroupAddressFilters;
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

    private static final String UNANSWERED = "'" + Outcome.REJECTED + "', '" + Outcome.NO_REPLY + "'";

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

    public enum AddressOrderColumn {

        ADDRESS("address"),
        SESSIONS_FROM("sessions_from"),
        SESSIONS_TO("sessions_to"),
        ANSWERED("answered"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        AddressOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record GroupAddress(String address,
                               long sessionsFrom,
                               long sessionsTo,
                               long answered,
                               DateTime firstSeen,
                               DateTime lastSeen,
                               List<UUID> taps) { }

    // foldedSessions went to addresses of the group that a sweep was folded over and that are not stored.
    public record AddressPage(long total, List<GroupAddress> addresses, long foldedSessions) { }

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
            "protocol, outcome, sessions, continued, first_seen, last_seen, source_asn_name, source_asn_domain, " +
            "destination_asn_name, destination_asn_domain) VALUES(:organization_id, :tenant_id, " +
            ":bucket, :tap_uuid, :source_key, :source_address::inet, :source_internal, :source_segment_id, " +
            ":source_asn, :source_country_code, :destination_key, :destination_address::inet, " +
            ":destination_internal, :destination_segment_id, :destination_asn, :destination_country_code, " +
            ":l4_type, :destination_port, :protocol, :outcome, :sessions, :continued, :first_seen, :last_seen, " +
            ":source_asn_name, :source_asn_domain, :destination_asn_name, :destination_asn_domain) " +
            "ON CONFLICT (organization_id, tenant_id, bucket, tap_uuid, source_key, destination_key, l4_type, " +
            "destination_port, protocol, outcome) DO UPDATE SET " +
            "sessions = segmentation_flows.sessions + EXCLUDED.sessions, " +
            "continued = segmentation_flows.continued + EXCLUDED.continued, " +
            "source_internal = EXCLUDED.source_internal, " +
            "source_segment_id = EXCLUDED.source_segment_id, " +
            "destination_internal = EXCLUDED.destination_internal, " +
            "destination_segment_id = EXCLUDED.destination_segment_id, " +
            "source_asn_name = COALESCE(EXCLUDED.source_asn_name, segmentation_flows.source_asn_name), " +
            "source_asn_domain = COALESCE(EXCLUDED.source_asn_domain, segmentation_flows.source_asn_domain), " +
            "destination_asn_name = COALESCE(EXCLUDED.destination_asn_name, " +
            "segmentation_flows.destination_asn_name), " +
            "destination_asn_domain = COALESCE(EXCLUDED.destination_asn_domain, " +
            "segmentation_flows.destination_asn_domain), " +
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
                    .bind("source_asn_name", flow.source().asnName())
                    .bind("source_asn_domain", flow.source().asnDomain())
                    .bind("destination_key", flow.destinationKey())
                    .bind("destination_address", address(flow.destination()))
                    .bind("destination_internal", flow.destination().internal())
                    .bind("destination_segment_id", flow.destination().segment() == null ? null : flow.destination().segment().id())
                    .bind("destination_asn", flow.destination().asn())
                    .bind("destination_country_code", flow.destination().countryCode())
                    .bind("destination_asn_name", flow.destination().asnName())
                    .bind("destination_asn_domain", flow.destination().asnDomain())
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

    public void rebuildCrossings(Handle handle, UUID organizationId, UUID tenantId) {
        handle.createUpdate("DELETE FROM segmentation_crossings WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .execute();

        handle.createUpdate("INSERT INTO segmentation_crossings(organization_id, tenant_id, bucket, " +
                        "tap_uuid, source_group, destination_group, l4_type, destination_port, protocol, " +
                        "outcome, sessions, continued, first_seen, last_seen) " +
                        "SELECT organization_id, tenant_id, bucket, tap_uuid, " +
                        "CASE WHEN source_segment_id IS NOT NULL THEN source_segment_id " +
                        "WHEN source_internal THEN 0 ELSE -1 END, " +
                        "CASE WHEN destination_key LIKE '" + BROADCAST_KEYS + "' THEN " +
                        Flow.GROUP_BROADCAST + " " +
                        "WHEN destination_segment_id IS NOT NULL THEN destination_segment_id " +
                        "WHEN destination_internal THEN 0 ELSE -1 END, " +
                        "l4_type, destination_port, protocol, outcome, SUM(sessions), SUM(continued), " +
                        "MIN(first_seen), MAX(last_seen) FROM segmentation_flows " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "GROUP BY 1, 2, 3, 4, 5, 6, 7, 8, 9, 10")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .execute();
    }

    private static final String UNANSWERED_TO = "organization_id = :organization_id AND tenant_id = :tenant_id " +
            "AND source_segment_id = :segment_id AND destination_internal " +
            "AND destination_address <<= :destination::cidr AND outcome IN (" + UNANSWERED + ")";

    // The crossings that the flows that are removed are part of.
    private static final String CROSSINGS_OF_UNANSWERED = "SELECT DISTINCT bucket, tap_uuid, " +
            "COALESCE(destination_segment_id, " + Flow.GROUP_UNASSIGNED + ") AS destination_group, l4_type, " +
            "destination_port, protocol, outcome FROM segmentation_flows WHERE " + UNANSWERED_TO;

    // Only the flows. The crossings have to be rebuilt by the caller.
    public long deleteUnanswered(Handle handle, UUID organizationId, UUID tenantId, long sourceSegmentId, CIDR destination) {
        return handle.createUpdate("DELETE FROM segmentation_flows WHERE " + UNANSWERED_TO)
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("segment_id", sourceSegmentId)
                .bind("destination", destination.toString())
                .execute();
    }

    // Removes what is stored of what a segment tried to reach at a destination without an answer. Only the crossings
    // those flows are part of are built again, from the flows that stay: a segment can have started most of what is
    // stored, and this runs in a request. Folded destinations have no address and stay.
    public long removeUnanswered(Handle handle, UUID organizationId, UUID tenantId, long sourceSegmentId, CIDR destination) {
        handle.createUpdate("DELETE FROM segmentation_crossings AS c USING (" + CROSSINGS_OF_UNANSWERED + ") AS d " +
                        "WHERE c.organization_id = :organization_id AND c.tenant_id = :tenant_id " +
                        "AND c.source_group = :segment_id AND c.bucket = d.bucket AND c.tap_uuid = d.tap_uuid " +
                        "AND c.destination_group = d.destination_group AND c.l4_type = d.l4_type " +
                        "AND c.destination_port = d.destination_port AND c.protocol = d.protocol " +
                        "AND c.outcome = d.outcome")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("segment_id", sourceSegmentId)
                .bind("destination", destination.toString())
                .execute();

        handle.createUpdate("INSERT INTO segmentation_crossings(organization_id, tenant_id, bucket, tap_uuid, " +
                        "source_group, destination_group, l4_type, destination_port, protocol, outcome, sessions, " +
                        "continued, first_seen, last_seen) " +
                        "SELECT :organization_id, :tenant_id, f.bucket, f.tap_uuid, :segment_id, " +
                        "d.destination_group, f.l4_type, f.destination_port, f.protocol, f.outcome, " +
                        "SUM(f.sessions), SUM(f.continued), MIN(f.first_seen), MAX(f.last_seen) " +
                        "FROM segmentation_flows AS f JOIN (" + CROSSINGS_OF_UNANSWERED + ") AS d " +
                        "ON f.bucket = d.bucket AND f.tap_uuid = d.tap_uuid " +
                        "AND COALESCE(f.destination_segment_id, " + Flow.GROUP_UNASSIGNED + ") = d.destination_group " +
                        "AND f.l4_type = d.l4_type AND f.destination_port = d.destination_port " +
                        "AND f.protocol = d.protocol AND f.outcome = d.outcome " +
                        "WHERE f.organization_id = :organization_id AND f.tenant_id = :tenant_id " +
                        "AND f.source_segment_id = :segment_id AND f.destination_internal " +
                        "AND f.destination_key NOT LIKE '" + BROADCAST_KEYS + "' " +
                        "AND (f.destination_address IS NULL OR NOT f.destination_address <<= :destination::cidr) " +
                        "GROUP BY f.bucket, f.tap_uuid, d.destination_group, f.l4_type, f.destination_port, " +
                        "f.protocol, f.outcome")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("segment_id", sourceSegmentId)
                .bind("destination", destination.toString())
                .execute();

        return deleteUnanswered(handle, organizationId, tenantId, sourceSegmentId, destination);
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
                                "MAX(destination_country_code) AS destination_country_code, " +
                                "MAX(source_asn_name) AS source_asn_name, " +
                                "MAX(source_asn_domain) AS source_asn_domain, " +
                                "MAX(destination_asn_name) AS destination_asn_name, " +
                                "MAX(destination_asn_domain) AS destination_asn_domain, l4_type, " +
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
                "MAX(destination_country_code) AS destination_country_code, " +
                "MAX(source_asn_name) AS source_asn_name, MAX(source_asn_domain) AS source_asn_domain, " +
                "MAX(destination_asn_name) AS destination_asn_name, " +
                "MAX(destination_asn_domain) AS destination_asn_domain, SUM(sessions) AS sessions, " +
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
                "source_asn_name, source_asn_domain, destination_asn_name, destination_asn_domain, " +
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

    // The prefilters say which side belongs to the group. Per direction, the tap that saw the most of an address
    // counts where several taps recorded it.
    public AddressPage addresses(UUID organizationId,
                                 UUID tenantId,
                                 TimeRange timeRange,
                                 List<UUID> taps,
                                 Prefilter source,
                                 Prefilter destination,
                                 Filters filters,
                                 AddressOrderColumn orderColumn,
                                 OrderDirection orderDirection,
                                 int limit,
                                 int offset) {
        if (taps.isEmpty()) {
            return new AddressPage(0, Collections.emptyList(), 0);
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new GroupAddressFilters());

        String scope = "FROM segmentation_flows WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                "AND tap_uuid IN (<taps>) " +
                "AND bucket >= date_trunc('hour', :tr_from::timestamptz, 'UTC') AND bucket <= :tr_to ";

        // The destinations without an address are what was folded. One row comes back even if no address is found,
        // so that the total and the folded sessions never need a query of their own.
        String sql = "WITH sources AS (SELECT tap_uuid, source_address AS address, " +
                "SUM(sessions + continued) AS sessions_from, MIN(first_seen) AS first_seen, " +
                "MAX(last_seen) AS last_seen " + scope + source.sql() +
                "AND source_address IS NOT NULL GROUP BY tap_uuid, source_address), " +
                "destinations AS (SELECT tap_uuid, destination_address AS address, " +
                "SUM(sessions + continued) AS sessions_to, " +
                "COALESCE(SUM(sessions + continued) FILTER (WHERE outcome = 'CONNECTED'), 0) AS answered, " +
                "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " + scope + destination.sql() +
                "GROUP BY tap_uuid, destination_address), " +
                "per_tap AS (SELECT tap_uuid, address, SUM(sessions_from) AS sessions_from, " +
                "SUM(sessions_to) AS sessions_to, SUM(answered) AS answered, MIN(first_seen) AS first_seen, " +
                "MAX(last_seen) AS last_seen FROM (" +
                "SELECT tap_uuid, address, sessions_from, 0 AS sessions_to, 0 AS answered, first_seen, last_seen " +
                "FROM sources UNION ALL " +
                "SELECT tap_uuid, address, 0, sessions_to, answered, first_seen, last_seen " +
                "FROM destinations WHERE address IS NOT NULL) AS sides GROUP BY tap_uuid, address), " +
                "merged AS (SELECT address, MAX(sessions_from) AS sessions_from, MAX(sessions_to) AS sessions_to, " +
                "MAX(answered) AS answered, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                "string_agg(tap_uuid::text, ',' ORDER BY tap_uuid) AS taps FROM per_tap GROUP BY address), " +
                "matching AS (SELECT * FROM merged WHERE TRUE " + filterFragment.whereSql() + "), " +
                "page AS (SELECT *, ROW_NUMBER() OVER (ORDER BY <order_column> <order_direction> NULLS LAST, " +
                "address ASC) AS position FROM matching ORDER BY position LIMIT :limit OFFSET :offset) " +
                "SELECT p.*, host(p.address) AS host, t.total, f.folded " +
                "FROM (SELECT COUNT(*) AS total FROM matching) AS t " +
                "CROSS JOIN (SELECT COALESCE(MAX(sessions_to), 0) AS folded FROM destinations " +
                "WHERE address IS NULL) AS f " +
                "LEFT JOIN page AS p ON TRUE ORDER BY p.position";

        return nzyme.getDatabase().withHandle(handle -> {
            long[] total = new long[]{0};
            long[] folded = new long[]{0};
            List<GroupAddress> addresses = Lists.newArrayList();

            handle.createQuery(sql)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to())
                    .bindList("taps", taps)
                    .bindMap(source.bindings())
                    .bindMap(destination.bindings())
                    .bindMap(filterFragment.bindings())
                    .bind("limit", limit)
                    .bind("offset", offset)
                    .define("order_column", orderColumn.getColumnName())
                    .define("order_direction", orderDirection)
                    .map((rs, ctx) -> {
                        total[0] = rs.getLong("total");
                        folded[0] = rs.getLong("folded");

                        String address = rs.getString("host");
                        if (address == null) {
                            return false;
                        }

                        List<UUID> recordedBy = Lists.newArrayList();
                        for (String tap : rs.getString("taps").split(",")) {
                            recordedBy.add(UUID.fromString(tap));
                        }

                        addresses.add(new GroupAddress(
                                address,
                                rs.getLong("sessions_from"),
                                rs.getLong("sessions_to"),
                                rs.getLong("answered"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen")),
                                recordedBy
                        ));

                        return true;
                    })
                    .list();

            return new AddressPage(total[0], addresses, folded[0]);
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

        return resolver.stored(internal, segmentId, asn, countryCode)
                .withAsnDetails(rs.getString(side + "_asn_name"), rs.getString(side + "_asn_domain"));
    }

}
