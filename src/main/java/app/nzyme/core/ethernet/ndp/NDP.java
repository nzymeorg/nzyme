package app.nzyme.core.ethernet.ndp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class NDP {

    public static final String ROUTER_SOLICITATION = "ROUTER_SOLICITATION";
    public static final String NEIGHBOR_SOLICITATION = "NEIGHBOR_SOLICITATION";
    public static final String NEIGHBOR_ADVERTISEMENT = "NEIGHBOR_ADVERTISEMENT";

    public static final List<String> MESSAGE_TYPES = List.of(
            ROUTER_SOLICITATION, NEIGHBOR_SOLICITATION, NEIGHBOR_ADVERTISEMENT
    );

    // A router is at the MAC address its advertisement names, which is not always the one the frame came from.
    public static final String ROUTER_MAC = "COALESCE(source_link_layer_address, source_mac)";

    public enum MessageOrderColumn {

        LAST_SEEN("last_seen"),
        MESSAGE_TYPE("message_type"),
        SOURCE_MAC("source_mac"),
        SOURCE_ADDRESS("source_address"),
        DESTINATION_ADDRESS("destination_address"),
        TARGET_ADDRESS("target_address"),
        HOP_LIMIT("hop_limit"),
        PACKET_COUNT("packet_count");

        private final String columnName;

        MessageOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum RouterAdvertisementOrderColumn {

        LAST_SEEN("last_seen"),
        SOURCE_MAC(ROUTER_MAC),
        SOURCE_ADDRESS("source_address"),
        PREFERENCE("preference"),
        ROUTER_LIFETIME("router_lifetime"),
        HOP_LIMIT("hop_limit"),
        PACKET_COUNT("packet_count");

        private final String columnName;

        RouterAdvertisementOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public record Message(UUID tapUuid, String messageType, @Nullable String sourceMac,
                          @Nullable String destinationMac, String sourceAddress, String destinationAddress,
                          int hopLimit, @Nullable String targetAddress, @Nullable String sourceLinkLayerAddress,
                          @Nullable String targetLinkLayerAddress, @Nullable Boolean router,
                          @Nullable Boolean solicited, @Nullable Boolean overrides, long packetCount,
                          DateTime firstSeen, DateTime lastSeen) { }

    public record RouterAdvertisement(UUID tapUuid, @Nullable String sourceMac, @Nullable String destinationMac,
                                      String sourceAddress, String destinationAddress, int hopLimit,
                                      @Nullable String sourceLinkLayerAddress, int currentHopLimit, boolean managed,
                                      boolean otherConfiguration, String preference, int routerLifetime,
                                      long reachableTime, long retransmitTimer, @Nullable Long mtu, String prefixes,
                                      String routes, String dnsServers, @Nullable Long dnsServersLifetime,
                                      String dnsSearchList, long packetCount, DateTime firstSeen,
                                      DateTime lastSeen) { }

    public record StatisticsBucket(DateTime bucket, long neighborSolicitations, long neighborAdvertisements,
                                   long routerSolicitations) { }

    private final NzymeNode nzyme;

    public NDP(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllMessages(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NdpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM ndp_messages " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Message> findAllMessages(TimeRange timeRange,
                                         Filters filters,
                                         int limit,
                                         int offset,
                                         MessageOrderColumn orderColumn,
                                         OrderDirection orderDirection,
                                         List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NdpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT tap_uuid, message_type, source_mac, destination_mac, " +
                                "host(source_address) AS source_address, " +
                                "host(destination_address) AS destination_address, hop_limit, " +
                                "host(target_address) AS target_address, source_link_layer_address, " +
                                "target_link_layer_address, router, solicited, overrides, packet_count, " +
                                "first_seen, last_seen FROM ndp_messages " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "ORDER BY <order_column> <order_direction>, id DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .map((rs, ctx) -> new Message(
                                UUID.fromString(rs.getString("tap_uuid")),
                                rs.getString("message_type"),
                                rs.getString("source_mac"),
                                rs.getString("destination_mac"),
                                rs.getString("source_address"),
                                rs.getString("destination_address"),
                                rs.getInt("hop_limit"),
                                rs.getString("target_address"),
                                rs.getString("source_link_layer_address"),
                                rs.getString("target_link_layer_address"),
                                nullableBoolean(rs, "router"),
                                nullableBoolean(rs, "solicited"),
                                nullableBoolean(rs, "overrides"),
                                rs.getLong("packet_count"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))
                        ))
                        .list()
        );
    }

    public List<StatisticsBucket> getStatistics(TimeRange timeRange,
                                                Bucketing.BucketingConfiguration bucketing,
                                                Filters filters,
                                                List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NdpFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, last_seen) AS bucket, " +
                                "COALESCE(SUM(packet_count) FILTER (WHERE message_type = '" + NEIGHBOR_SOLICITATION + "'), 0) " +
                                "AS neighbor_solicitations, " +
                                "COALESCE(SUM(packet_count) FILTER (WHERE message_type = '" + NEIGHBOR_ADVERTISEMENT + "'), 0) " +
                                "AS neighbor_advertisements, " +
                                "COALESCE(SUM(packet_count) FILTER (WHERE message_type = '" + ROUTER_SOLICITATION + "'), 0) " +
                                "AS router_solicitations " +
                                "FROM ndp_messages WHERE last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .map((rs, ctx) -> new StatisticsBucket(
                                new DateTime(rs.getTimestamp("bucket")),
                                rs.getLong("neighbor_solicitations"),
                                rs.getLong("neighbor_advertisements"),
                                rs.getLong("router_solicitations")
                        ))
                        .list()
        );
    }

    public long countAllRouterAdvertisements(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NdpRouterAdvertisementFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM ndp_router_advertisements " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<RouterAdvertisement> findAllRouterAdvertisements(TimeRange timeRange,
                                                                 Filters filters,
                                                                 int limit,
                                                                 int offset,
                                                                 RouterAdvertisementOrderColumn orderColumn,
                                                                 OrderDirection orderDirection,
                                                                 List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NdpRouterAdvertisementFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT tap_uuid, source_mac, destination_mac, " +
                                "host(source_address) AS source_address, " +
                                "host(destination_address) AS destination_address, hop_limit, " +
                                "source_link_layer_address, current_hop_limit, managed, other_configuration, " +
                                "preference, router_lifetime, reachable_time, retransmit_timer, mtu, " +
                                "prefixes::text AS prefixes, routes::text AS routes, " +
                                "dns_servers::text AS dns_servers, dns_servers_lifetime, " +
                                "dns_search_list::text AS dns_search_list, packet_count, first_seen, last_seen " +
                                "FROM ndp_router_advertisements " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "ORDER BY <order_column> <order_direction>, id DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .map((rs, ctx) -> new RouterAdvertisement(
                                UUID.fromString(rs.getString("tap_uuid")),
                                rs.getString("source_mac"),
                                rs.getString("destination_mac"),
                                rs.getString("source_address"),
                                rs.getString("destination_address"),
                                rs.getInt("hop_limit"),
                                rs.getString("source_link_layer_address"),
                                rs.getInt("current_hop_limit"),
                                rs.getBoolean("managed"),
                                rs.getBoolean("other_configuration"),
                                rs.getString("preference"),
                                rs.getInt("router_lifetime"),
                                rs.getLong("reachable_time"),
                                rs.getLong("retransmit_timer"),
                                nullableLong(rs, "mtu"),
                                rs.getString("prefixes"),
                                rs.getString("routes"),
                                rs.getString("dns_servers"),
                                nullableLong(rs, "dns_servers_lifetime"),
                                rs.getString("dns_search_list"),
                                rs.getLong("packet_count"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))
                        ))
                        .list()
        );
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

}
