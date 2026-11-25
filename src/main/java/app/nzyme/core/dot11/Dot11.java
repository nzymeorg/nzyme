package app.nzyme.core.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchAnomaly;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchMonitorRule;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchOutcome;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchRules;
import app.nzyme.core.dot11.db.*;
import app.nzyme.core.dot11.db.filters.Dot11BSSIDFilters;
import app.nzyme.core.dot11.db.filters.Dot11ChannelSwitchFilters;
import app.nzyme.core.dot11.db.filters.Dot11ConnectedClientFilters;
import app.nzyme.core.dot11.db.filters.Dot11DisconnectedClientFilters;
import app.nzyme.core.database.generic.ThreeColumnHistogramOrderColumn;
import app.nzyme.core.database.generic.TwoColumnHistogramOrderColumn;
import app.nzyme.core.dot11.db.filters.Dot11DiscoFilters;
import app.nzyme.core.dot11.db.monitoring.*;
import app.nzyme.core.dot11.db.monitoring.probereq.MonitoredProbeRequestEntry;
import app.nzyme.core.dot11.monitoring.disco.db.Dot11DiscoMonitorMethodConfiguration;
import app.nzyme.core.dot11.tracks.db.TrackDetectorConfig;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.resources.taps.reports.tables.dot11.Dot11SecurityInformationReport;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressContextResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressResponse;
import app.nzyme.core.rest.responses.dot11.clients.ConnectedBSSID;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.TimeRangeFactory;
import app.nzyme.core.util.Tools;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.SqlStatement;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.sql.Array;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Dot11 {

    /*
     * JDBI bindList() does not work with empty lists. In some cases, we must expect an empty list and will pass
     * this list instead to avoid maintaining multiple queries. The random value in this list will not match, just
     * like an empty list. Computers!
     */
    private static final List<String> noValuesBindList = new ArrayList<>(){{
        add("GYGzDTnSDLgJs9rMY8ZXj0EVwDBw2lZl");
    }};

    private final NzymeNode nzyme;

    public enum BssidOrderColumn {

        BSSID("bssid"),
        SIGNAL_STRENGTH_AVERAGE("signal_strength_average"),
        CLIENT_COUNT("client_count"),
        LAST_SEEN("last_seen");

        private final String columnName;

        BssidOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum ClientOrderColumn {

        CLIENT_MAC("client_mac"),
        SIGNAL_STRENGTH_AVERAGE("signal_strength_average"),
        LAST_SEEN("last_seen");

        private final String columnName;

        ClientOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum KnownSSIDOrderColumn {

        SSID("ssid"),
        STATUS("(is_approved, is_ignored)"),
        LAST_SEEN("last_seen");

        private final String columnName;

        KnownSSIDOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum KnownNetworkBulkAction {

        APPROVE("UPDATE dot11_known_networks SET is_approved = true", "AND NOT is_approved AND NOT is_ignored"),
        REVOKE("UPDATE dot11_known_networks SET is_approved = false", "AND is_approved"),
        IGNORE("UPDATE dot11_known_networks SET is_ignored = true", "AND NOT is_ignored AND NOT is_approved"),
        UNIGNORE("UPDATE dot11_known_networks SET is_ignored = false", "AND is_ignored"),
        DELETE("DELETE FROM dot11_known_networks", "");

        private final String statement;
        private final String condition;

        KnownNetworkBulkAction(String statement, String condition) {
            this.statement = statement;
            this.condition = condition;
        }

    }

    public enum MonitorActiveStatusTypeColumn {

        UNEXPECTED_BSSID("enabled_unexpected_bssid"),
        UNEXPECTED_CHANNEL("enabled_unexpected_channel"),
        UNEXPECTED_SECURITY_SUITES("enabled_unexpected_security_suites"),
        UNEXPECTED_FINGERPRINT("enabled_unexpected_fingerprint"),
        UNEXPECTED_SIGNAL_TRACKS("enabled_unexpected_signal_tracks"),
        DISCO_MONITOR("enabled_disco_monitor"),
        CHANNEL_SWITCH_MONITOR("enabled_channel_switch_monitor"),
        MULTIPLE_CHANNELS("enabled_multiple_channels"),
        SIMILAR_SSIDS("enabled_similar_looking_ssid"),
        RESTRICTED_SSID_SUBSTRINGS("enabled_ssid_substring"),
        CLIENT_MONITORING("enabled_client_monitoring"),
        CLIENT_EVENTING("enabled_client_eventing");

        private final String columnName;

        MonitorActiveStatusTypeColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum DiscoType {
        DEAUTHENTICATION,
        DISASSOCIATION
    }

    public enum DiscoFrameOrderColumn {

        CREATED_AT("created_at"),
        DISCO_TYPE("disco_type"),
        TRANSMITTER("transmitter"),
        RECEIVER("receiver"),
        BSSID("bssid"),
        REASON_CODE("reason_code"),
        FREQUENCY("frequency"),
        SIGNAL_STRENGTH("signal_strength_average"),
        FRAME_COUNT("frame_count");

        private final String columnName;

        DiscoFrameOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum ChannelSwitchOrderColumn {

        CREATED_AT("created_at"),
        BSSID("bssid"),
        FRAME_TYPE("frame_type"),
        SWITCH_MODE("switch_mode"),
        FREQUENCY("frequency"),
        NEW_CHANNEL("new_channel"),
        COUNT("count_min"),
        FRAME_COUNT("frame_count"),
        PEAK_RATE("peak_frames_per_second"),
        SIGNAL_STRENGTH("signal_strength_average"),
        ANOMALIES("cardinality(anomalies)"),
        OUTCOME("outcome");

        private final String columnName;

        ChannelSwitchOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

        // The index on the time only serves an order without a placement of nulls.
        public String nullsSql() {
            return this == CREATED_AT ? "" : "NULLS LAST";
        }

    }

    public enum DiscoMacDimension {

        TRANSMITTER("transmitter"),
        RECEIVER("receiver"),
        BSSID("bssid");

        private final String columnName;

        DiscoMacDimension(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    LoadingCache<Dot11MacAddressLookupCompositeKey, Dot11MacAddressMetadata> macAddressMetadata = CacheBuilder.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<>() {
                // TODO MOVE TO PRIVATE FETCH()
                @Override
                public Dot11MacAddressMetadata load(Dot11MacAddressLookupCompositeKey lookup){
                    return fetchMacAddressMetadataNoCache(lookup);
                }
            });

    public Dot11(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public Dot11MacAddressMetadata getMacAddressMetadata(String macAddress, List<UUID> taps) {
        try {
            return macAddressMetadata.get(Dot11MacAddressLookupCompositeKey.create(macAddress, taps));
        } catch(Exception e) {
            throw new RuntimeException("Could not fetch 802.11 MAC address metadata.", e);
        }
    }

    private Dot11MacAddressMetadata fetchMacAddressMetadataNoCache(Dot11MacAddressLookupCompositeKey lookup) {
        // Is this a access point?
        boolean isAccessPoint = bssidExist(lookup.mac(), TimeRangeFactory.allTime(), lookup.taps());

        // Is this a client?
        boolean isClient = clientExist(lookup.mac(), Integer.MAX_VALUE, lookup.taps());

        // Is it both?
        if (isAccessPoint && isClient) {
            return Dot11MacAddressMetadata.create(Dot11MacAddressType.MULTIPLE);
        } else {
            // Not both. Good.
            if (isAccessPoint) {
                return Dot11MacAddressMetadata.create(Dot11MacAddressType.ACCESS_POINT);
            } else if (isClient) {
                return Dot11MacAddressMetadata.create(Dot11MacAddressType.CLIENT);
            } else {
                // None. Shouldn't happen.
                return Dot11MacAddressMetadata.create(Dot11MacAddressType.UNKNOWN);
            }
        }
    }

    public List<String> findAllSSIDNames(List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT(ssid) FROM dot11_ssids " +
                                "WHERE tap_uuid IN (<taps>) ORDER BY ssid ASC")
                        .bindList("taps", taps)
                        .mapTo(String.class)
                        .list()
        );
    }

    public List<String> findAllRecentSSIDNames(List<UUID> taps, int minutes) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT(ssid) FROM dot11_ssids " +
                                "WHERE tap_uuid IN (<taps>) AND created_at > :cutoff " +
                                "ORDER BY ssid ASC")
                        .bindList("taps", taps)
                        .bind("cutoff", DateTime.now().minusMinutes(minutes))
                        .mapTo(String.class)
                        .list()
        );
    }

    public List<SSIDWithOrganizationAndTenant> findAllSSIDsAndOwner(DateTime since) {
        List<SSIDWithOrganizationAndTenant> result = Lists.newArrayList();

        /*
         * One row for each location an SSID was seen at, followed by one row for the SSID as a whole. Streamed,
         * because the number of SSIDs is not bounded. The fetch size only applies inside a transaction.
         */
        nzyme.getDatabase().useTransaction(handle -> {
            List<SSIDWithOrganizationAndTenant.Sighting> sightings = Lists.newArrayList();

            try (Stream<Map.Entry<Boolean, SSIDWithOrganizationAndTenant>> rows = handle.createQuery(
                            "SELECT s.ssid, t.organization_id, t.tenant_id, GROUPING(t.location_uuid) = 1 AS is_total, " +
                                    "COUNT(DISTINCT date_trunc('minute', s.created_at)) AS active_minutes, " +
                                    "MAX(s.created_at) AS last_seen, " +
                                    "string_agg(DISTINCT s.tap_uuid::text, ',') AS taps FROM dot11_ssids AS s " +
                                    "JOIN taps AS t ON s.tap_uuid = t.uuid " +
                                    "WHERE s.created_at >= :since " +
                                    "GROUP BY GROUPING SETS ((s.ssid, t.organization_id, t.tenant_id), " +
                                    "(s.ssid, t.organization_id, t.tenant_id, t.location_uuid)) " +
                                    "ORDER BY s.ssid, t.organization_id, t.tenant_id, GROUPING(t.location_uuid)")
                    .bind("since", since)
                    .setFetchSize(1000)
                    .map((rs, ctx) -> Map.entry(rs.getBoolean("is_total"), SSIDWithOrganizationAndTenant.create(
                            rs.getString("ssid"),
                            UUID.fromString(rs.getString("organization_id")),
                            UUID.fromString(rs.getString("tenant_id")),
                            new DateTime(rs.getTimestamp("last_seen")),
                            rs.getInt("active_minutes"),
                            Arrays.stream(rs.getString("taps").split(",")).map(UUID::fromString).toList(),
                            Collections.emptyList()
                    )))
                    .stream()) {
                rows.forEach(row -> {
                    SSIDWithOrganizationAndTenant ssid = row.getValue();
                    if (row.getKey()) {
                        result.add(ssid.toBuilder().sightings(Lists.newArrayList(sightings)).build());
                        sightings.clear();
                    } else {
                        sightings.add(new SSIDWithOrganizationAndTenant.Sighting(ssid.activeMinutes(), ssid.taps()));
                    }
                });
            }
        });

        return result;
    }

    public Optional<BSSIDSummary> findBSSID(String bssid, TimeRange timeRange, List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bssid, AVG(b.signal_strength_average) AS signal_strength_average, " +
                                "MIN(b.created_at) AS first_seen, MAX(b.created_at) AS last_seen, " +
                                "SUM(b.hidden_ssid_frames) as hidden_ssid_frames, " +
                                "COALESCE(ARRAY_AGG(DISTINCT protocol_elem) FILTER (WHERE protocol_elem IS NOT NULL), " +
                                "ARRAY[]::text[]) AS security_protocols, " +
                                "COALESCE(ARRAY_AGG(DISTINCT fingerprints_elem) FILTER (WHERE fingerprints_elem IS NOT NULL), " +
                                "ARRAY[]::text[]) AS fingerprints, " +
                                "ARRAY_AGG(DISTINCT(s.ssid)) AS ssids, " +
                                "ARRAY_AGG(DISTINCT infratypes_elem) AS infrastructure_types, " +
                                "ARRAY_AGG(DISTINCT(ch.frequency)) AS frequencies, " +
                                "COUNT(DISTINCT(c.client_mac)) AS client_count " +
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_ssids AS s ON b.id = s.bssid_id " +
                                "LEFT JOIN dot11_channels AS ch ON s.id = ch.ssid_id " +
                                "LEFT JOIN LATERAL UNNEST(s.fingerprints) AS fingerprints_elem ON TRUE " +
                                "LEFT JOIN LATERAL UNNEST(s.infrastructure_types) AS infratypes_elem ON TRUE " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS(s.security_settings) AS security_elem ON TRUE " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS_TEXT(security_elem->'protocols') AS protocol_elem ON TRUE " +
                                "LEFT JOIN dot11_bssid_clients AS c on b.id = c.bssid_id " +
                                "WHERE b.bssid = :bssid AND b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "AND b.tap_uuid IN (<taps>) GROUP BY b.bssid")
                        .bind("bssid", bssid)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(BSSIDSummary.class)
                        .findOne()
        );
    }

    public List<Dot11AdvertisementHistogramEntry> getBSSIDAdvertisementHistogram(String bssid,
                                                                                 TimeRange timeRange,
                                                                                 Bucketing.BucketingConfiguration bucketing,
                                                                                 List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT SUM(beacon_advertisements) AS beacons, " +
                                "SUM(proberesp_advertisements) AS proberesponses, " +
                                "DATE_TRUNC(:date_trunc, created_at) AS bucket FROM dot11_ssids " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) AND bssid = :bssid " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bindList("taps", taps)
                        .bind("bssid", bssid)
                        .mapTo(Dot11AdvertisementHistogramEntry.class)
                        .list()
        );
    }


    public List<ActiveChannel> getBSSIDChannelUsageHistogram(String bssid, TimeRange timeRange, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT c.frequency, sum(c.stats_frames) AS frames, sum(c.stats_bytes) AS bytes " +
                                "FROM dot11_ssids AS s " +
                                "LEFT JOIN dot11_channels c on s.id = c.ssid_id " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) AND s.bssid = :bssid " +
                                "GROUP BY c.frequency")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bind("bssid", bssid)
                        .mapTo(ActiveChannel.class)
                        .list()
        );
    }

    public long countBSSIDs(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11BSSIDFilters());

        // We need the entire query, including SELECTs here to make the HAVING filters work.
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT b.bssid, AVG(b.signal_strength_average) AS signal_strength_average, " +
                                "MIN(b.created_at) AS first_seen, MAX(b.created_at) AS last_seen, " +
                                "SUM(b.hidden_ssid_frames) as hidden_ssid_frames, " +
                                "ARRAY_AGG(DISTINCT security_elem) FILTER (WHERE security_elem IS NOT NULL) AS security_settings, " +
                                "ARRAY_AGG(DISTINCT fingerprints_elem) AS fingerprints, " +
                                "ARRAY_AGG(DISTINCT(s.ssid)) AS ssids, " +
                                "COUNT(DISTINCT(c.client_mac)) AS client_count, " +
                                "ARRAY[]::integer[] AS frequencies " +
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_ssids AS s ON b.id = s.bssid_id " +
                                "LEFT JOIN LATERAL UNNEST(s.fingerprints) AS fingerprints_elem ON TRUE " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS(s.security_settings) AS security_elem ON TRUE " +
                                "LEFT JOIN dot11_bssid_clients AS c on b.id = c.bssid_id " +
                                "WHERE b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "AND b.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                "GROUP BY b.bssid HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .first()
        );
    }

    public List<BSSIDSummary> findBSSIDs(TimeRange timeRange,
                                         Filters filters,
                                         BssidOrderColumn orderColumn,
                                         OrderDirection orderDirection,
                                         int limit,
                                         int offset,
                                         List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11BSSIDFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bssid, AVG(b.signal_strength_average) AS signal_strength_average, " +
                                "MIN(b.created_at) AS first_seen, MAX(b.created_at) AS last_seen, " +
                                "SUM(b.hidden_ssid_frames) as hidden_ssid_frames, " +
                                "COALESCE(ARRAY_AGG(DISTINCT protocol_elem) FILTER (WHERE protocol_elem IS NOT NULL), " +
                                "ARRAY[]::text[]) AS security_protocols, " +
                                "ARRAY_AGG(DISTINCT fingerprints_elem) AS fingerprints, " +
                                "ARRAY_AGG(DISTINCT(s.ssid)) AS ssids, " +
                                "ARRAY_AGG(DISTINCT infratypes_elem) AS infrastructure_types, " +
                                "COUNT(DISTINCT(c.client_mac)) AS client_count, " +
                                "ARRAY[]::integer[] AS frequencies " + // Part of BSSIDSummary but not needed.
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_ssids AS s ON b.id = s.bssid_id " +
                                "LEFT JOIN LATERAL UNNEST(s.fingerprints) AS fingerprints_elem ON TRUE " +
                                "LEFT JOIN LATERAL UNNEST(s.infrastructure_types) AS infratypes_elem ON TRUE " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS(s.security_settings) AS security_elem ON TRUE " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS_TEXT(security_elem->'protocols') AS protocol_elem ON TRUE " +
                                "LEFT JOIN dot11_bssid_clients AS c on b.id = c.bssid_id " +
                                "WHERE b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "AND b.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                "GROUP BY b.bssid HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(BSSIDSummary.class)
                        .list()
        );
    }

    public boolean bssidExist(String bssid, TimeRange timeRange, List<UUID> taps) {
        if (taps.isEmpty()) {
            return false;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT EXISTS (SELECT 1 FROM dot11_bssids " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " +
                                "AND bssid = :bssid)")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bind("bssid", bssid)
                        .mapTo(Boolean.class)
                        .one()
        );
    }

    public boolean clientExist(String clientMac, int minutes, List<UUID> taps) {
        if (taps.isEmpty()) {
            return false;
        }

        boolean isDisconnectedClient = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT EXISTS (SELECT 1 FROM dot11_clients " +
                                "WHERE created_at > :cutoff AND tap_uuid IN (<taps>) " +
                                "AND client_mac = :client_mac)")
                        .bind("cutoff", DateTime.now().minusMinutes(minutes))
                        .bindList("taps", taps)
                        .bind("client_mac", clientMac)
                        .mapTo(Boolean.class)
                        .one()
        );

        boolean isConnectedClient = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT EXISTS (SELECT 1 FROM dot11_bssid_clients AS c LEFT JOIN dot11_bssids AS b ON c.bssid_id = b.id " +
                                "WHERE b.created_at > :cutoff AND b.tap_uuid IN (<taps>) " +
                                "AND c.client_mac = :client_mac)")
                        .bind("cutoff", DateTime.now().minusMinutes(minutes))
                        .bindList("taps", taps)
                        .bind("client_mac", clientMac)
                        .mapTo(Boolean.class)
                        .one()
        );

        return isDisconnectedClient || isConnectedClient;
    }

    public Optional<Integer> findMostActiveChannelOfBSSID(TimeRange timeRange, String bssid, List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT c.frequency, MAX(c.stats_frames) AS frames FROM dot11_ssids AS s " +
                                "LEFT JOIN dot11_channels AS c ON c.ssid_id = s.id " +
                                "WHERE s.bssid = :bssid AND s.tap_uuid IN (<taps>) " +
                                "AND s.created_at >= :tr_from AND s.created_at <= :tr_to " +
                                "GROUP BY c.frequency ORDER BY SUM(c.stats_frames) DESC LIMIT 1")
                        .bind("bssid", bssid)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(Integer.class)
                        .findOne()
        );
    }

    public Optional<Integer> findMostActiveChannelOfSSID(TimeRange timeRange, String bssid, String ssid, List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT c.frequency, MAX(c.stats_frames) AS frames FROM dot11_ssids AS s " +
                                "LEFT JOIN dot11_channels AS c ON c.ssid_id = s.id " +
                                "WHERE s.bssid = :bssid AND s.ssid = :ssid AND s.tap_uuid IN (<taps>) " +
                                "AND s.created_at >= :tr_from AND s.created_at <= :tr_to " +
                                "GROUP BY c.frequency ORDER BY SUM(c.stats_frames) DESC LIMIT 1")
                        .bind("bssid", bssid)
                        .bind("ssid", ssid)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(Integer.class)
                        .findOne()
        );
    }

    public List<SSIDChannelDetails> findSSIDPerChannelsOfBSSID(TimeRange timeRange, String bssid, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT s.ssid, c.frequency, MAX(s.created_at) AS last_seen, " +
                                "COALESCE(ARRAY_AGG(DISTINCT protocol_elem) FILTER (WHERE protocol_elem IS NOT NULL), " +
                                "ARRAY[]::text[]) AS security_protocols, " +
                                "ARRAY_AGG(DISTINCT wps_elem) AS has_wps, " +
                                "ARRAY_AGG(DISTINCT infratypes_elem) AS infrastructure_types, " +
                                "AVG(s.signal_strength_average) AS signal_strength_average, " +
                                "SUM(c.stats_bytes) AS total_bytes, SUM(c.stats_frames) AS total_frames " +
                                "FROM dot11_ssids AS s " +
                                "LEFT JOIN dot11_channels AS c on s.id = c.ssid_id " +
                                "LEFT JOIN LATERAL UNNEST(s.infrastructure_types) AS infratypes_elem ON TRUE " +
                                "LEFT JOIN LATERAL UNNEST(s.has_wps) AS wps_elem ON TRUE " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS(s.security_settings) AS security_elem ON TRUE " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS_TEXT(security_elem->'protocols') AS protocol_elem ON TRUE " +
                                "WHERE s.created_at >= :tr_from AND s.created_at <= :tr_to " +
                                "AND bssid = :bssid AND tap_uuid IN (<taps>) " +
                                "GROUP BY s.ssid, c.frequency")
                        .bind("bssid", bssid)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(SSIDChannelDetails.class)
                        .list()
        );
    }

    public Set<String> findSSIDNamesOfBSSID(TimeRange timeRange, String bssid, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptySet();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT(ssid) FROM dot11_ssids " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND bssid = :bssid AND tap_uuid IN (<taps>)")
                        .bind("bssid", bssid)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(String.class)
                        .set()
        );
    }

    public Optional<SSIDDetails> findSSIDDetails(TimeRange timeRange, String bssid, String ssid, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT s.ssid, MAX(s.created_at) AS last_seen, " +
                                "COALESCE(ARRAY_AGG(DISTINCT protocol_elem) FILTER (WHERE protocol_elem IS NOT NULL), " +
                                "ARRAY[]::text[]) AS security_protocols, " +
                                "COALESCE(ARRAY_AGG(DISTINCT security_elem) FILTER (WHERE security_elem IS NOT NULL), " +
                                "ARRAY[]::jsonb[]) AS security_settings, " +
                                "ARRAY_AGG(DISTINCT rate_elem) AS rates, " +
                                "ARRAY_AGG(DISTINCT infratypes_elem) AS infrastructure_types, " +
                                "ARRAY_AGG(DISTINCT fingerprints_elem) AS fingerprints, " +
                                "ARRAY_AGG(DISTINCT wps_elem) AS has_wps, " +
                                "AVG(s.signal_strength_average) AS signal_strength_average, " +
                                "ARRAY_AGG(DISTINCT(cl.client_mac)) AS access_point_clients, " +
                                "ARRAY_AGG(DISTINCT(c.frequency)) AS frequencies, " +
                                "SUM(c.stats_bytes) AS total_bytes, SUM(c.stats_frames) AS total_frames " +
                                "FROM dot11_ssids AS s " +
                                "LEFT JOIN dot11_bssids AS b on s.bssid_id = b.id " +
                                "LEFT JOIN dot11_channels AS c on s.id = c.ssid_id " +
                                "LEFT JOIN LATERAL UNNEST(s.rates) AS rate_elem ON TRUE " +
                                "LEFT JOIN LATERAL UNNEST(s.infrastructure_types) AS infratypes_elem ON TRUE " +
                                "LEFT JOIN LATERAL UNNEST(s.has_wps) AS wps_elem ON TRUE " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS(s.security_settings) AS security_elem ON TRUE " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS_TEXT(security_elem->'protocols') AS protocol_elem ON TRUE " +
                                "LEFT JOIN LATERAL UNNEST(s.fingerprints) AS fingerprints_elem ON TRUE " +
                                "LEFT JOIN dot11_bssid_clients cl on b.id = cl.bssid_id " +
                                "WHERE s.created_at >= :tr_from AND s.created_at <= :tr_to " +
                                "AND s.bssid = :bssid AND s.ssid = :ssid " +
                                "AND s.tap_uuid IN (<taps>) " +
                                "GROUP BY ssid")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("bssid", bssid)
                        .bind("ssid", ssid)
                        .bindList("taps", taps)
                        .mapTo(SSIDDetails.class)
                        .findOne()
        );
    }

    public Set<Double> getRatesOfSSID(TimeRange timeRange, String bssid, String ssid, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptySet();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT rate_elem " +
                                "FROM dot11_ssids AS s " +
                                "CROSS JOIN LATERAL UNNEST(s.rates) AS rate_elem " +
                                "WHERE s.created_at >= :tr_from AND s.created_at <= :tr_to " +
                                "AND s.bssid = :bssid AND s.ssid = :ssid " +
                                "AND s.tap_uuid IN (<taps>)")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("bssid", bssid)
                        .bind("ssid", ssid)
                        .bindList("taps", taps)
                        .mapTo(Double.class)
                        .set()
        );
    }

    public Set<Double> getSecuritySettingsOfSSID(TimeRange timeRange, String bssid, String ssid, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptySet();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT rate_elem " +
                                "FROM dot11_ssids AS s " +
                                "CROSS JOIN LATERAL UNNEST(s.rates) AS rate_elem " +
                                "WHERE s.created_at >= :tr_from AND s.created_at <= :tr_to " +
                                "AND s.bssid = :bssid AND s.ssid = :ssid " +
                                "AND s.tap_uuid IN (<taps>)")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("bssid", bssid)
                        .bind("ssid", ssid)
                        .bindList("taps", taps)
                        .mapTo(Double.class)
                        .set()
        );
    }

    public List<BSSIDAndSSIDCountHistogramEntry> getBSSIDAndSSIDCountHistogram(TimeRange timeRange,
                                                                               Bucketing.BucketingConfiguration bc,
                                                                               Filters filters,
                                                                               List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11BSSIDFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT b.bssid) AS bssid_count, " +
                                "COUNT(DISTINCT s.ssid) AS ssid_count, " +
                                "DATE_TRUNC(:date_trunc, b.created_at) AS bucket " +
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_ssids AS s ON b.id = s.bssid_id " +
                                "LEFT JOIN dot11_bssid_clients AS c on b.id = c.bssid_id " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS(s.security_settings) AS security_elem ON TRUE " +
                                "WHERE b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "AND b.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY bucket HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY bucket")
                        .bind("date_trunc", bc.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .mapTo(BSSIDAndSSIDCountHistogramEntry.class)
                        .list()
        );
    }

    public List<Dot11AdvertisementHistogramEntry> getSSIDAdvertisementHistogram(String bssid,
                                                                                String ssid,
                                                                                TimeRange timeRange,
                                                                                Bucketing.BucketingConfiguration bc,
                                                                                List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT SUM(beacon_advertisements) AS beacons, " +
                                "SUM(proberesp_advertisements) AS proberesponses, " +
                                "DATE_TRUNC(:date_trunc, created_at) AS bucket FROM dot11_ssids " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " +
                                "AND bssid = :bssid AND ssid = :ssid " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bc.type().getDateTruncName())
                        .bindList("taps", taps)
                        .bind("bssid", bssid)
                        .bind("ssid", ssid)
                        .mapTo(Dot11AdvertisementHistogramEntry.class)
                        .list()
        );
    }

    public List<ActiveChannel> getSSIDChannelUsageHistogram(String bssid,
                                                            String ssid,
                                                            TimeRange timeRange,
                                                            List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT c.frequency, sum(c.stats_frames) AS frames, sum(c.stats_bytes) AS bytes " +
                                "FROM dot11_ssids AS s " +
                                "LEFT JOIN dot11_channels c on s.id = c.ssid_id " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) AND s.bssid = :bssid " +
                                "AND s.ssid = :ssid GROUP BY c.frequency")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bind("bssid", bssid)
                        .bind("ssid", ssid)
                        .mapTo(ActiveChannel.class)
                        .list()
        );
    }

    public List<SignalTrackHistogramEntry> getSSIDSignalStrengthWaterfall(String bssid,
                                                                          String ssid,
                                                                          int frequency,
                                                                          TimeRange timeRange,
                                                                          UUID tapId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DATE_TRUNC('minute', s.created_at) AS bucket, signal_strength, " +
                                "SUM(frame_count) AS frame_count FROM dot11_ssids AS s " +
                                "LEFT JOIN dot11_channel_histograms h on s.id = h.ssid_id " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND s.tap_uuid = :tap_id AND bssid = :bssid " +
                                "AND ssid = :ssid AND h.frequency = :frequency " +
                                "GROUP BY bucket, signal_strength ORDER BY bucket DESC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("tap_id", tapId)
                        .bind("bssid", bssid)
                        .bind("ssid", ssid)
                        .bind("frequency", frequency)
                        .mapTo(SignalTrackHistogramEntry.class)
                        .list()
        );
    }

    public List<SignalTrackHistogramEntry> getBSSIDSignalStrengthWaterfall(String bssid,
                                                                           int frequency,
                                                                           TimeRange timeRange,
                                                                           UUID tapId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DATE_TRUNC('minute', s.created_at) AS bucket, " +
                                "h.signal_strength AS signal_strength, SUM(h.frame_count) AS frame_count " +
                                "FROM dot11_ssids AS s " +
                                "LEFT JOIN dot11_channel_histograms h on s.id = h.ssid_id " +
                                "WHERE h.frequency = :frequency " +
                                "AND s.created_at >= :tr_from AND s.created_at <= :tr_to " +
                                "AND s.tap_uuid = :tap_id AND s.bssid = :bssid " +
                                "GROUP BY bucket, signal_strength ORDER BY bucket DESC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("frequency", frequency)
                        .bind("tap_id", tapId)
                        .bind("bssid", bssid)
                        .mapTo(SignalTrackHistogramEntry.class)
                        .list()
        );
    }

    public Optional<TrackDetectorConfig> findCustomTrackDetectorConfiguration(UUID organizationId,
                                                                              UUID tapId,
                                                                              String bssid,
                                                                              String ssid,
                                                                              int channel) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT frame_threshold, gap_threshold, signal_centerline_jitter " +
                                "FROM dot11_track_detector_configuration " +
                                "WHERE organization_id = :organization_id AND bssid = :bssid AND ssid = :ssid " +
                                "AND channel = :channel AND tap_id = :tap_id")
                        .bind("organization_id", organizationId)
                        .bind("tap_id", tapId)
                        .bind("bssid", bssid)
                        .bind("ssid", ssid)
                        .bind("channel", channel)
                        .mapTo(TrackDetectorConfig.class)
                        .findOne()
        );
    }

    public void updateCustomTrackDetectorConfiguration(UUID organizationId,
                                                       UUID tapId,
                                                       String bssid,
                                                       String ssid,
                                                       int channel,
                                                       int frameThreshold,
                                                       int gapThreshold,
                                                       int signalCenterlineJitter) {
        if (findCustomTrackDetectorConfiguration(organizationId, tapId, bssid, ssid, channel).isPresent()) {
            // Update existing configuration.
            nzyme.getDatabase().useHandle(handle ->
                    handle.createUpdate("UPDATE dot11_track_detector_configuration " +
                                    "SET frame_threshold = :frame_threshold, gap_threshold = :gap_threshold, " +
                                    "signal_centerline_jitter = :signal_centerline_jitter, updated_at = NOW() " +
                                    "WHERE organization_id = :organization_id AND bssid = :bssid AND ssid = :ssid " +
                                    "AND channel = :channel AND tap_id = :tap_id")
                            .bind("organization_id", organizationId)
                            .bind("tap_id", tapId)
                            .bind("bssid", bssid)
                            .bind("ssid", ssid)
                            .bind("channel", channel)
                            .bind("frame_threshold", frameThreshold)
                            .bind("gap_threshold", gapThreshold)
                            .bind("signal_centerline_jitter", signalCenterlineJitter)
                            .execute()
            );
        } else {
            // Write new configuration.
            nzyme.getDatabase().useHandle(handle ->
                    handle.createUpdate("INSERT INTO dot11_track_detector_configuration(uuid, organization_id, " +
                                    "tap_id, bssid, ssid, channel, frame_threshold, gap_threshold, " +
                                    "signal_centerline_jitter, updated_at, created_at) VALUES(:uuid, :organization_id, " +
                                    ":tap_id, :bssid, :ssid, :channel, :frame_threshold, :gap_threshold, " +
                                    ":signal_centerline_jitter, NOW(), NOW())")
                            .bind("uuid", UUID.randomUUID())
                            .bind("organization_id", organizationId)
                            .bind("tap_id", tapId)
                            .bind("bssid", bssid)
                            .bind("ssid", ssid)
                            .bind("channel", channel)
                            .bind("frame_threshold", frameThreshold)
                            .bind("gap_threshold", gapThreshold)
                            .bind("signal_centerline_jitter", signalCenterlineJitter)
                            .execute()
            );
        }
    }

    public long countBSSIDClients(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ConnectedClientFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT(c.client_mac)) " +
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_bssid_clients c on b.id = c.bssid_id " +
                                "WHERE b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "AND b.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "HAVING 1=1 " + filterFragment.havingSql())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<ConnectedClientDetails> findBSSIDClients(TimeRange timeRange,
                                                         Filters filters,
                                                         List<UUID> taps,
                                                         int limit,
                                                         int offset,
                                                         ClientOrderColumn orderColumn,
                                                         OrderDirection orderDirection) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ConnectedClientFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bssid AS bssid, c.client_mac AS client_mac, " +
                                "AVG(c.signal_strength_average) AS signal_strength_average, " +
                                "MAX(b.created_at) AS last_seen FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_bssid_clients c on b.id = c.bssid_id " +
                                "WHERE b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "AND b.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY c.client_mac, b.bssid " +
                                "HAVING c.client_mac IS NOT NULL " + filterFragment.havingSql() +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(ConnectedClientDetails.class)
                        .list()
        );
    }

    public List<String> findMacAddressesOfAllBSSIDClients(TimeRange timeRange, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT(b.bssid) AS bssid " +
                                "FROM dot11_bssids AS b " +
                                "WHERE b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "AND b.tap_uuid IN (<taps>)")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(String.class)
                        .list()
        );
    }

    public List<String> findProbeRequestsOfClient(String clientMac,
                                                  List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT(s.ssid) " +
                                "FROM dot11_clients AS c " +
                                "LEFT JOIN dot11_client_probereq_ssids s on c.id = s.client_id " +
                                "WHERE c.tap_uuid IN (<taps>) " +
                                "AND c.client_mac = :client_mac AND s.ssid IS NOT NULL")
                        .bindList("taps", taps)
                        .bind("client_mac", clientMac)
                        .mapTo(String.class)
                        .list()
        );
    }

    private String skipRandomizedQueryFragment(boolean skipRandomized) {
        return skipRandomized ? " AND client_mac_is_randomized = false " : "";
    }

    public long countClients(TimeRange timeRange, Filters filters, boolean skipRandomized, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DisconnectedClientFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT(c.client_mac)) " +
                                "FROM dot11_clients AS c " +
                                "LEFT JOIN dot11_client_probereq_ssids AS pr on c.id = pr.client_id " +
                                "WHERE c.created_at >= :tr_from AND c.created_at <= :tr_to " +
                                "AND c.tap_uuid IN (<taps>)" +
                                skipRandomizedQueryFragment(skipRandomized) + " " + filterFragment.whereSql() +
                                "HAVING 1=1 " + filterFragment.havingSql())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DisconnectedClientDetails> findClients(TimeRange timeRange,
                                                       Filters filters,
                                                       List<UUID> taps,
                                                       List<String> excludeClientMacs,
                                                       boolean skipRandomized,
                                                       int limit,
                                                       int offset,
                                                       ClientOrderColumn orderColumn,
                                                       OrderDirection orderDirection) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DisconnectedClientFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT c.client_mac, MAX(created_at) AS last_seen, " +
                                "AVG(c.signal_strength_average) AS signal_strength_average, " +
                                "ARRAY_AGG(DISTINCT(pr.ssid)) AS probe_requests " +
                                "FROM dot11_clients AS c " +
                                "LEFT JOIN dot11_client_probereq_ssids AS pr on c.id = pr.client_id " +
                                "WHERE c.created_at >= :tr_from AND c.created_at <= :tr_to " +
                                "AND c.tap_uuid IN (<taps>) " + skipRandomizedQueryFragment(skipRandomized) +
                                "AND NOT c.client_mac IN (<exclude_client_macs>) " + filterFragment.whereSql() +
                                "GROUP BY c.client_mac HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindList("exclude_client_macs", excludeClientMacs == null || excludeClientMacs.isEmpty() ?
                                noValuesBindList : excludeClientMacs)
                        .bindMap(filterFragment.bindings())
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(DisconnectedClientDetails.class)
                        .list()
        );
    }

    public List<ConnectedClientDetails> findClientsOfBSSID(Handle handle, String bssid, int minutes, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return handle.createQuery("SELECT b.bssid AS bssid, c.client_mac AS client_mac, " +
                        "MAX(b.created_at) AS last_seen, " +
                        "AVG(c.signal_strength_average) AS signal_strength_average " +
                        "FROM dot11_bssids AS b " +
                        "LEFT JOIN dot11_bssid_clients c on b.id = c.bssid_id " +
                        "WHERE b.bssid = :bssid AND b.created_at > :cutoff AND b.tap_uuid IN (<taps>) " +
                        "GROUP BY c.client_mac, b.bssid " +
                        "HAVING c.client_mac IS NOT NULL " +
                        "ORDER BY c.client_mac ASC ")
                .bind("bssid", bssid)
                .bind("cutoff", DateTime.now().minusMinutes(minutes))
                .bindList("taps", taps)
                .mapTo(ConnectedClientDetails.class)
                .list();
    }

    public Map<String, Set<UUID>> findTapsOfClientsOfBSSID(Handle handle, String bssid, DateTime since, List<UUID> taps) {
        Map<String, Set<UUID>> result = Maps.newHashMap();
        if (taps.isEmpty()) {
            return result;
        }

        handle.createQuery("SELECT DISTINCT c.client_mac, b.tap_uuid::text AS tap_uuid FROM dot11_bssids AS b " +
                        "JOIN dot11_bssid_clients AS c ON b.id = c.bssid_id " +
                        "WHERE b.bssid = :bssid AND b.created_at > :since AND b.tap_uuid IN (<taps>)")
                .bind("bssid", bssid)
                .bind("since", since)
                .bindList("taps", taps)
                .map((rs, ctx) -> Map.entry(rs.getString("client_mac"), UUID.fromString(rs.getString("tap_uuid"))))
                .forEach(row -> result.computeIfAbsent(row.getKey(), k -> Sets.newLinkedHashSet()).add(row.getValue()));

        return result;
    }

    public List<ConnectedClientDetails> findClientsOfBSSID(String bssid, int minutes, List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle -> findClientsOfBSSID(handle, bssid, minutes, taps));
    }

    public List<ClientHistogramEntry> getDisconnectedClientHistogram(TimeRange timeRange,
                                                                     Filters filters,
                                                                     boolean skipRandomized,
                                                                     Bucketing.BucketingConfiguration bc,
                                                                     List<UUID> taps,
                                                                     List<String> excludeClientMacs) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DisconnectedClientFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT(c.client_mac)) AS client_count, " +
                                "DATE_TRUNC(:date_trunc, c.created_at) as bucket " +
                                "FROM dot11_clients AS c " +
                                "LEFT JOIN dot11_client_probereq_ssids AS pr on c.id = pr.client_id " +
                                "WHERE c.created_at >= :tr_from AND c.created_at <= :tr_to " +
                                "AND c.tap_uuid IN (<taps>) " + skipRandomizedQueryFragment(skipRandomized) +
                                "AND NOT c.client_mac IN (<exclude_client_macs>) " + filterFragment.whereSql() +
                                "GROUP BY bucket HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY bucket DESC")
                        .bind("date_trunc", bc.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bindList("exclude_client_macs", excludeClientMacs == null || excludeClientMacs.isEmpty() ?
                                noValuesBindList : excludeClientMacs)
                        .mapTo(ClientHistogramEntry.class)
                        .list()
        );
    }

    public List<ClientHistogramEntry> getConnectedClientHistogram(TimeRange timeRange,
                                                                  Filters filters,
                                                                  Bucketing.BucketingConfiguration bc,
                                                                  List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ConnectedClientFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT(c.client_mac)) AS client_count, " +
                                "DATE_TRUNC(:date_trunc, b.created_at) as bucket " +
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_bssid_clients c on b.id = c.bssid_id " +
                                "WHERE b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "AND b.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY bucket HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY bucket DESC")
                        .bind("date_trunc", bc.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .mapTo(ClientHistogramEntry.class)
                        .list()
        );
    }

    public List<String> findBSSIDsClientWasConnectedTo(String clientMac, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bssid " +
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_bssid_clients c on b.id = c.bssid_id " +
                                "WHERE c.client_mac = :client_mac " +
                                "AND b.tap_uuid IN (<taps>)" +
                                "GROUP by b.bssid")
                        .bind("client_mac", clientMac)
                        .bindList("taps", taps)
                        .mapTo(String.class)
                        .list()
        );
    }

    public List<String> findSSIDsAdvertisedByBSSID(String bssid, List<UUID> taps, TimeRange timeRange) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        Optional<String[]> x = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT ARRAY_AGG(DISTINCT(s.ssid)) " +
                                "FROM dot11_ssids AS s " +
                                "WHERE s.created_at >= :tr_from AND s.created_at <= :tr_to AND " +
                                "s.bssid = :bssid AND s.tap_uuid IN (<taps>)")
                        .bind("bssid", bssid)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(String[].class)
                        .findOne()
        );

        if (x.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = Lists.newArrayList();
        result.addAll(Arrays.asList(x.get()));

        return result;
    }

    public Optional<ClientDetails> findMergedConnectedOrDisconnectedClient(String clientMac,
                                                                           List<UUID> taps,
                                                                           @Nullable TimeRange histogramTimeRange,
                                                                           @Nullable Bucketing.BucketingConfiguration histogramBucketing,
                                                                           AuthenticatedUser authenticatedUser) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        Optional<FirstLastSeenTuple> connected = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT MAX(b.created_at) AS last_seen, MIN(b.created_at) AS first_seen " +
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_bssid_clients AS c on b.id = c.bssid_id " +
                                "WHERE c.client_mac = :client_mac AND b.tap_uuid IN (<taps>)")
                        .bind("client_mac", clientMac)
                        .bindList("taps", taps)
                        .mapTo(FirstLastSeenTuple.class)
                        .findOne()
        );

        // Aggregations might return NULL.
        if (connected.isPresent()
                && connected.get().firstSeen() == null && connected.get().lastSeen() == null) {
            connected = Optional.empty();
        }

        Optional<String> currentlyConnectedBSSID = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bssid " +
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN dot11_bssid_clients AS c on b.id = c.bssid_id " +
                                "WHERE c.client_mac = :client_mac AND b.tap_uuid IN (<taps>) " +
                                "ORDER BY b.created_at DESC " +
                                "LIMIT 1")
                        .bind("client_mac", clientMac)
                        .bindList("taps", taps)
                        .mapTo(String.class)
                        .findOne()
        );

        List<ConnectedBSSID> connectedBSSIDs = Lists.newArrayList();
        List<ClientActivityHistogramEntry> connectedHistogram;
        if (connected.isPresent()) {
            // We have found this client as connected client.
            for (String bssid : findBSSIDsClientWasConnectedTo(clientMac, taps)) {
                Optional<MacAddressContextEntry> bssidContext = nzyme.getContextService().findMacAddressContext(
                        bssid,
                        authenticatedUser.getOrganizationId(),
                        authenticatedUser.getTenantId()
                );

                List<String> advertisedSSIDs = findSSIDsAdvertisedByBSSID(bssid, taps, TimeRangeFactory.oneDay());

                connectedBSSIDs.add(ConnectedBSSID.create(
                        Dot11MacAddressResponse.create(
                                bssid,
                                nzyme.getOuiService().lookup(bssid).orElse(null),
                                null,
                                bssidContext.map(macAddressContextEntry ->
                                                Dot11MacAddressContextResponse.create(
                                                        macAddressContextEntry.name(),
                                                        macAddressContextEntry.description(),
                                                        macAddressContextEntry.notes()
                                                ))
                                        .orElse(null)
                        ),
                        advertisedSSIDs
                ));
            }

            if (histogramTimeRange != null) {
                connectedHistogram = nzyme.getDatabase().withHandle(handle ->
                        handle.createQuery("SELECT DATE_TRUNC(:date_trunc, b.created_at) as bucket, " +
                                        "COALESCE(SUM(c.rx_frames) + SUM(c.tx_frames), 0) AS frames " +
                                        "FROM dot11_bssids AS b " +
                                        "LEFT JOIN dot11_bssid_clients c on b.id = c.bssid_id " +
                                        "WHERE b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                        "AND b.tap_uuid IN (<taps>) AND c.client_mac = :client_mac " +
                                        "GROUP BY bucket " +
                                        "ORDER BY bucket DESC")
                                .bind("tr_from", histogramTimeRange.from())
                                .bind("tr_to", histogramTimeRange.to())
                                .bind("date_trunc", histogramBucketing.type().getDateTruncName())
                                .bind("client_mac", clientMac)
                                .bindList("taps", taps)
                                .mapTo(ClientActivityHistogramEntry.class)
                                .list()
                );
            } else {
                connectedHistogram = Collections.emptyList();
            }
        } else {
            connectedBSSIDs = Collections.emptyList();
            connectedHistogram = Collections.emptyList();
        }

        Optional<FirstLastSeenTuple> disconnected = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT MAX(c.created_at) AS last_seen, MIN(c.created_at) AS first_seen " +
                                "FROM dot11_clients AS c " +
                                "WHERE c.client_mac = :client_mac AND c.tap_uuid IN (<taps>)")
                        .bind("client_mac", clientMac)
                        .bindList("taps", taps)
                        .mapTo(FirstLastSeenTuple.class)
                        .findOne()
        );

        // Aggregations might return NULL.
        if (disconnected.isPresent()
                && disconnected.get().firstSeen() == null && disconnected.get().lastSeen() == null) {
            disconnected = Optional.empty();
        }

        List<String> probeRequests;
        List<ClientActivityHistogramEntry> disconnectedHistogram;
        if (disconnected.isPresent()) {
            probeRequests = findProbeRequestsOfClient(clientMac, taps);

            if (histogramTimeRange != null) {
                disconnectedHistogram = nzyme.getDatabase().withHandle(handle ->
                        handle.createQuery("SELECT DATE_TRUNC(:date_trunc, c.created_at) as bucket, " +
                                        "COALESCE(SUM(wildcard_probe_requests), 0) " +
                                        "+ COALESCE(SUM(pr.frame_count), 0) AS frames " +
                                        "FROM dot11_clients AS c " +
                                        "LEFT JOIN dot11_client_probereq_ssids AS pr on c.id = pr.client_id " +
                                        "WHERE c.created_at >= :tr_from AND c.created_at <= :tr_to " +
                                        "AND c.tap_uuid IN (<taps>) AND client_mac = :client_mac " +
                                        "GROUP BY bucket " +
                                        "ORDER BY bucket DESC")
                                .bind("tr_from", histogramTimeRange.from())
                                .bind("tr_to", histogramTimeRange.to())
                                .bind("date_trunc", histogramBucketing.type().getDateTruncName())
                                .bind("client_mac", clientMac)
                                .bindList("taps", taps)
                                .mapTo(ClientActivityHistogramEntry.class)
                                .list()
                );
            } else {
                disconnectedHistogram = Collections.emptyList();
            }
        } else {
            probeRequests = Collections.emptyList();
            disconnectedHistogram = Collections.emptyList();
        }

        if (connected.isEmpty() && disconnected.isEmpty()) {
            // Client not found.
            return Optional.empty();
        }

        // Decide which last seen is more recent.
        DateTime lastSeen;
        DateTime firstSeen;
        if (connected.isPresent() && disconnected.isPresent()) {
            if (connected.get().lastSeen().isAfter(disconnected.get().lastSeen())) {
                lastSeen = connected.get().lastSeen();
            } else {
                lastSeen = disconnected.get().lastSeen();
            }

            if (connected.get().firstSeen().isBefore(disconnected.get().firstSeen())) {
                firstSeen = connected.get().lastSeen();
            } else {
                firstSeen = disconnected.get().lastSeen();
            }
        } else if (connected.isPresent()) {
            lastSeen = connected.get().lastSeen();
            firstSeen = connected.get().firstSeen();
        } else {
            lastSeen = disconnected.get().lastSeen();
            firstSeen = disconnected.get().firstSeen();
        }


        ConnectedBSSID currentlyConnectedBssidResponse = null;
        if (currentlyConnectedBSSID.isPresent()) {
            Optional<MacAddressContextEntry> bssidContext = nzyme.getContextService().findMacAddressContext(
                    currentlyConnectedBSSID.get(),
                    authenticatedUser.getOrganizationId(),
                    authenticatedUser.getTenantId()
            );

            currentlyConnectedBssidResponse = ConnectedBSSID.create(
                    Dot11MacAddressResponse.create(
                            currentlyConnectedBSSID.get(),
                            nzyme.getOuiService().lookup(currentlyConnectedBSSID.get()).orElse(null),
                            null,
                            bssidContext.map(macAddressContextEntry ->
                                            Dot11MacAddressContextResponse.create(
                                                    macAddressContextEntry.name(),
                                                    macAddressContextEntry.description(),
                                                    macAddressContextEntry.notes()
                                            ))
                                    .orElse(null)
                    ), Collections.emptyList());
        }

        return Optional.of(ClientDetails.create(
                clientMac,
                nzyme.getOuiService().lookup(clientMac).orElse(null),
                currentlyConnectedBssidResponse,
                connectedBSSIDs,
                firstSeen,
                lastSeen,
                probeRequests,
                connectedHistogram,
                disconnectedHistogram
        ));
    }

    public List<TapBasedSignalStrengthResult> findDisconnectedClientSignalStrengthPerTap(String clientMac,
                                                                                         TimeRange timeRange,
                                                                                         List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT c.tap_uuid AS tap_uuid, t.name AS tap_name, " +
                                "AVG(c.signal_strength_average) AS signal_strength " +
                                "FROM dot11_clients AS c " +
                                "LEFT JOIN taps AS t ON c.tap_uuid = t.uuid " +
                                "WHERE c.client_mac = :client_mac AND c.tap_uuid IN (<taps>) " +
                                "AND c.created_at >= :tr_from AND c.created_at <= :tr_to " +
                                "GROUP BY c.tap_uuid, t.name")
                        .bind("client_mac", clientMac)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(TapBasedSignalStrengthResult.class)
                        .list()
        );
    }

    public List<TapBasedSignalStrengthResult> findBssidClientSignalStrengthPerTap(String clientMac,
                                                                                  TimeRange timeRange,
                                                                                  List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.tap_uuid AS tap_uuid, t.name AS tap_name, " +
                                "AVG(c.signal_strength_average) AS signal_strength " +
                                "FROM dot11_bssid_clients AS c " +
                                "LEFT JOIN dot11_bssids AS b on b.id = c.bssid_id " +
                                "LEFT JOIN taps AS t ON b.tap_uuid = t.uuid " +
                                "WHERE c.client_mac = :client_mac AND b.tap_uuid IN (<taps>) " +
                                "AND b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "GROUP BY b.tap_uuid, t.name")
                        .bind("client_mac", clientMac)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(TapBasedSignalStrengthResult.class)
                        .list()
        );
    }

    public List<ClientSignalStrengthResult> findDisconnectedClientSignalStrengthHistogram(String clientMac,
                                                                                          TimeRange timeRange,
                                                                                          Bucketing.BucketingConfiguration bucketing,
                                                                                          UUID tap) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT AVG(signal_strength_average) AS signal_strength, " +
                                "DATE_TRUNC(:date_trunc, created_at) AS bucket " +
                                "FROM dot11_clients " +
                                "WHERE client_mac = :client_mac AND tap_uuid = :tap_uuid " +
                                "AND created_at >= :tr_from AND created_at <= :tr_to " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("client_mac", clientMac)
                        .bind("tap_uuid", tap)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .mapTo(ClientSignalStrengthResult.class)
                        .list()
        );
    }

    public List<ClientSignalStrengthResult> findBssidClientSignalStrengthHistogram(String clientMac,
                                                                                   TimeRange timeRange,
                                                                                   Bucketing.BucketingConfiguration bucketing,
                                                                                   UUID tap) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT AVG(c.signal_strength_average) AS signal_strength, " +
                                "DATE_TRUNC(:date_trunc, b.created_at) AS bucket " +
                                "FROM dot11_bssid_clients AS c " +
                                "LEFT JOIN dot11_bssids AS b ON b.id = c.bssid_id " +
                                "WHERE c.client_mac = :client_mac AND b.tap_uuid = :tap_uuid " +
                                "AND b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("client_mac", clientMac)
                        .bind("tap_uuid", tap)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .mapTo(ClientSignalStrengthResult.class)
                        .list()
        );
    }


    public List<TapBasedSignalStrengthResultHistogramEntry> getDisconnectedClientSignalStrengthPerTapHistogram(String clientMac,
                                                                                                               TimeRange timeRange,
                                                                                                               Bucketing.BucketingConfiguration bucketing,
                                                                                                               List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DATE_TRUNC('minute', c.created_at) AS bucket, " +
                                "c.tap_uuid, t.name AS tap_name, AVG(c.signal_strength_average) AS signal_strength " +
                                "FROM dot11_clients AS c LEFT JOIN taps AS t ON c.tap_uuid = t.uuid " +
                                "WHERE c.client_mac = :client_mac AND c.tap_uuid IN (<taps>) " +
                                "AND c.created_at >= :tr_from AND c.created_at <= :tr_to " +
                                "GROUP BY c.tap_uuid, tap_name, bucket ORDER BY bucket DESC")
                        .bind("client_mac", clientMac)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .mapTo(TapBasedSignalStrengthResultHistogramEntry.class)
                        .list()
        );
    }

    public List<TapBasedSignalStrengthResultHistogramEntry> getConnectedClientSignalStrengthPerTapHistogram(String clientMac,
                                                                                                            TimeRange timeRange,
                                                                                                            Bucketing.BucketingConfiguration bucketing,
                                                                                                            List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DATE_TRUNC('minute', b.created_at) AS bucket, " +
                                "b.tap_uuid, t.name AS tap_name, AVG(c.signal_strength_average) AS signal_strength " +
                                "FROM dot11_bssid_clients AS c " +
                                "LEFT JOIN dot11_bssids AS b on b.id = c.bssid_id " +
                                "LEFT JOIN taps AS t ON tap_uuid = t.uuid " +
                                "WHERE c.client_mac = :client_mac AND b.tap_uuid IN (<taps>) " +
                                "AND b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "GROUP BY b.tap_uuid, tap_name, bucket ORDER BY bucket DESC")
                        .bind("client_mac", clientMac)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .mapTo(TapBasedSignalStrengthResultHistogramEntry.class)
                        .list()
        );
    }

    public void createMonitoredSSID(String ssid, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
            handle.createUpdate("INSERT INTO dot11_monitored_networks(uuid, ssid, organization_id, tenant_id, " +
                            "created_at, updated_at) VALUES(:uuid, :ssid, :organization_id, :tenant_id, " +
                            "NOW(), NOW())")
                    .bind("uuid", UUID.randomUUID())
                    .bind("ssid", ssid)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .execute()
        );
    }

    public void bumpMonitoredSSIDUpdatedAt(long networkId) {
        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("UPDATE dot11_monitored_networks SET updated_at = NOW() WHERE id = :id")
                    .bind("id", networkId)
                    .execute();
        });
    }

    public void setMonitoredSSIDEnabledState(long networkId, boolean enabled) {
        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("UPDATE dot11_monitored_networks SET enabled = :enabled WHERE id = :id")
                    .bind("enabled", enabled)
                    .bind("id", networkId)
                    .execute();
        });
    }

    public void deleteMonitoredSSID(long networkId) {
        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("DELETE FROM dot11_monitored_networks WHERE id = :id")
                    .bind("id", networkId)
                    .execute();
        });
    }

    public Optional<MonitoredSSID> findMonitoredSSID(UUID uuid) {
        return nzyme.getDatabase().withHandle(handle ->
            handle.createQuery("SELECT * FROM dot11_monitored_networks WHERE uuid = :uuid LIMIT 1")
                    .bind("uuid", uuid)
                    .mapTo(MonitoredSSID.class)
                    .findOne()
        );
    }

    public List<MonitoredSSID> findAllMonitoredSSIDs(@Nullable UUID organizationId, @Nullable UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> {
            Query query;
            if (organizationId == null && tenantId == null) {
                // Super Admin.
                query = handle.createQuery("SELECT * FROM dot11_monitored_networks ORDER BY ssid ASC");
            } else if (organizationId != null && tenantId == null) {
                // Organization Admin.
                query = handle.createQuery("SELECT * FROM dot11_monitored_networks " +
                                "WHERE organization_id = :organization_id ORDER BY ssid ASC")
                        .bind("organization_id", organizationId);
            } else {
                // Tenant User.
                query = handle.createQuery("SELECT * FROM dot11_monitored_networks " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id ORDER BY ssid ASC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId);
            }

            return query.mapTo(MonitoredSSID.class).list();
        });
    }

    public Optional<Long> findMonitoredBSSIDId(long networkId, UUID bssidUUID) {
        return nzyme.getDatabase().withHandle(handle ->
            handle.createQuery("SELECT b.id FROM dot11_monitored_networks AS s " +
                            "LEFT JOIN dot11_monitored_networks_bssids b on s.id = b.monitored_network_id " +
                            "WHERE s.id = :network_id AND b.uuid = :bssid_uuid LIMIT 1")
                    .bind("network_id", networkId)
                    .bind("bssid_uuid", bssidUUID)
                    .mapTo(Long.class)
                    .findOne()
        );
    }

    public Optional<Long> findMonitoredBSSIDId(long networkId, String bssid) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.id FROM dot11_monitored_networks AS s " +
                                "LEFT JOIN dot11_monitored_networks_bssids b on s.id = b.monitored_network_id " +
                                "WHERE s.id = :network_id AND b.bssid = :bssid LIMIT 1")
                        .bind("network_id", networkId)
                        .bind("bssid", bssid)
                        .mapTo(Long.class)
                        .findOne()
        );
    }

    public List<MonitoredBSSID> findMonitoredBSSIDsOfMonitoredNetwork(Handle handle, long ssidId) {
        return handle.createQuery("SELECT * FROM dot11_monitored_networks_bssids " +
                        "WHERE monitored_network_id = :ssid_id ORDER BY bssid ASC")
                .bind("ssid_id", ssidId)
                .mapTo(MonitoredBSSID.class)
                .list();
    }


    public List<MonitoredBSSID> findMonitoredBSSIDsOfMonitoredNetwork(long ssidId) {
        return nzyme.getDatabase().withHandle(handle -> findMonitoredBSSIDsOfMonitoredNetwork(handle, ssidId));
    }

    public List<MonitoredFingerprint> findMonitoredFingerprintsOfMonitoredBSSID(long bssidId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_monitored_networks_fingerprints " +
                                "WHERE monitored_network_bssid_id = :bssid_id ORDER BY fingerprint ASC")
                        .bind("bssid_id", bssidId)
                        .mapTo(MonitoredFingerprint.class)
                        .list()
        );
    }

    public long createMonitoredBSSID(long monitoredNetworkId, String bssid) {
        String uppercaseBSSID = bssid.toUpperCase();
        if (!Tools.isValidMacAddress(uppercaseBSSID)) {
            throw new RuntimeException("Invalid MAC address: " + uppercaseBSSID);
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("INSERT INTO dot11_monitored_networks_bssids(uuid, monitored_network_id, " +
                                "bssid) VALUES(:uuid, :monitored_network_id, :bssid) RETURNING *")
                        .bind("uuid", UUID.randomUUID())
                        .bind("monitored_network_id", monitoredNetworkId)
                        .bind("bssid", uppercaseBSSID)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public void deleteMonitoredBSSID(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_monitored_networks_bssids WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void deleteAllMonitoredBSSIDs(long ssidId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_monitored_networks_bssids " +
                                "WHERE monitored_network_id = :ssid_id")
                        .bind("ssid_id", ssidId)
                        .execute()
        );
    }

    public void createdMonitoredBSSIDFingerprint(long bssidId, String fingerprint) {
        if (fingerprint == null || fingerprint.length() != 64) {
            throw new RuntimeException("Invalid fingerprint: " + fingerprint);
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO dot11_monitored_networks_fingerprints(uuid, " +
                                "monitored_network_bssid_id, fingerprint) VALUES(:uuid, :monitored_network_bssid_id, " +
                                ":fingerprint)")
                        .bind("uuid", UUID.randomUUID())
                        .bind("monitored_network_bssid_id", bssidId)
                        .bind("fingerprint", fingerprint)
                        .execute()
        );
    }

    public void deleteMonitoredBSSIDFingerprint(long bssidId, UUID fingerprintUUID) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_monitored_networks_fingerprints " +
                                "WHERE monitored_network_bssid_id = :bssidId AND uuid = :uuid")
                        .bind("bssidId", bssidId)
                        .bind("uuid", fingerprintUUID)
                        .execute()
        );
    }

    public List<MonitoredChannel> findMonitoredChannelsOfMonitoredNetwork(long ssidId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_monitored_networks_channels " +
                                "WHERE monitored_network_id = :ssid_id ORDER BY frequency ASC")
                        .bind("ssid_id", ssidId)
                        .mapTo(MonitoredChannel.class)
                        .list()
        );
    }

    public void createMonitoredChannel(long monitoredNetworkId, long frequency) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO dot11_monitored_networks_channels(uuid, monitored_network_id, " +
                                "frequency) VALUES(:uuid, :monitored_network_id, :frequency)")
                        .bind("uuid", UUID.randomUUID())
                        .bind("monitored_network_id", monitoredNetworkId)
                        .bind("frequency", frequency)
                        .execute()
        );
    }

    public void deleteMonitoredChannel(long monitoredNetworkId, UUID channelUUID) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_monitored_networks_channels " +
                                "WHERE monitored_network_id = :monitored_network_id AND uuid = :uuid")
                        .bind("monitored_network_id", monitoredNetworkId)
                        .bind("uuid", channelUUID)
                        .execute()
        );
    }

    public List<MonitoredSecuritySuite> findMonitoredSecuritySuitesOfMonitoredNetwork(long ssidId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_monitored_networks_security_suites " +
                                "WHERE monitored_network_id = :ssid_id ORDER BY suite ASC")
                        .bind("ssid_id", ssidId)
                        .mapTo(MonitoredSecuritySuite.class)
                        .list()
        );
    }

    public void createMonitoredSecuritySuite(long monitoredNetworkId, String suite) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO dot11_monitored_networks_security_suites(uuid, " +
                                "monitored_network_id, suite) VALUES(:uuid, :monitored_network_id, :suite)")
                        .bind("uuid", UUID.randomUUID())
                        .bind("monitored_network_id", monitoredNetworkId)
                        .bind("suite", suite)
                        .execute()
        );
    }

    public void deleteMonitoredSecuritySuite(long monitoredNetworkId, UUID suiteUUID) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_monitored_networks_security_suites " +
                                "WHERE monitored_network_id = :monitored_network_id AND uuid = :uuid")
                        .bind("monitored_network_id", monitoredNetworkId)
                        .bind("uuid", suiteUUID)
                        .execute()
        );
    }

    public void setMonitorAlertStatus(long monitoredNetworkId, MonitorActiveStatusTypeColumn type, boolean status) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_monitored_networks SET <column> = :status WHERE id = :id")
                        .bind("id", monitoredNetworkId)
                        .define("column", type.getColumnName())
                        .bind("status", status)
                        .execute()
        );
    }

    public void setChannelSwitchMonitorConfiguration(long monitoredNetworkId, int burstThreshold) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_monitored_networks " +
                                "SET dconf_channel_switch_burst_threshold = :burst_threshold " +
                                "WHERE id = :id")
                        .bind("id", monitoredNetworkId)
                        .bind("burst_threshold", burstThreshold)
                        .execute()
        );
    }

    public void setSimilarSSIDMonitorConfiguration(long monitoredNetworkId, int threshold) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_monitored_networks " +
                                "SET dconf_similar_looking_ssid_threshold = :threshold " +
                                "WHERE id = :id")
                        .bind("id", monitoredNetworkId)
                        .bind("threshold", threshold)
                        .execute()
        );
    }

    public List<RestrictedSSIDSubstring> findAllRestrictedSSIDSubstrings(long monitoredNetworkId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_monitored_networks_restricted_substrings " +
                                "WHERE monitored_network_id = :monitored_network_id ORDER BY substring")
                        .bind("monitored_network_id", monitoredNetworkId)
                        .mapTo(RestrictedSSIDSubstring.class)
                        .list()
        );
    }

    public void createRestrictedSSIDSubstring(long monitoredNetworkId, String substring) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO dot11_monitored_networks_restricted_substrings(uuid, " +
                                "monitored_network_id, substring, created_at) VALUES(:uuid, " +
                                ":monitored_network_id, :substring, NOW())")
                        .bind("uuid", UUID.randomUUID())
                        .bind("monitored_network_id", monitoredNetworkId)
                        .bind("substring", substring)
                        .execute()
        );
    }

    public void deleteRestrictedSSIDSubstring(long monitoredNetworkId, UUID uuid) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_monitored_networks_restricted_substrings " +
                                "WHERE monitored_network_id = :monitored_network_id AND uuid = :uuid")
                        .bind("uuid", uuid)
                        .bind("monitored_network_id", monitoredNetworkId)
                        .execute()
        );
    }

    public static final int MAX_ALLOWED_SIMILAR_SSIDS = 500;

    public List<String> findAllowedSimilarSSIDs(long monitoredNetworkId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT ssid FROM dot11_monitored_networks_allowed_similar_ssids " +
                                "WHERE monitored_network_id = :monitored_network_id ORDER BY ssid")
                        .bind("monitored_network_id", monitoredNetworkId)
                        .mapTo(String.class)
                        .list()
        );
    }

    public void createAllowedSimilarSSID(long monitoredNetworkId, String ssid) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO dot11_monitored_networks_allowed_similar_ssids(" +
                                "monitored_network_id, ssid, created_at) VALUES(:monitored_network_id, :ssid, NOW()) " +
                                "ON CONFLICT (monitored_network_id, ssid) DO NOTHING")
                        .bind("monitored_network_id", monitoredNetworkId)
                        .bind("ssid", ssid)
                        .execute()
        );
    }

    public void deleteAllowedSimilarSSID(long monitoredNetworkId, String ssid) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_monitored_networks_allowed_similar_ssids " +
                                "WHERE monitored_network_id = :monitored_network_id AND ssid = :ssid")
                        .bind("monitored_network_id", monitoredNetworkId)
                        .bind("ssid", ssid)
                        .execute()
        );
    }

    public long countCustomBandits(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dot11_bandits " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public void createCustomBandit(UUID organizationId, UUID tenantId, String name, String description) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO dot11_bandits(uuid, organization_id, tenant_id, name, " +
                                "description, created_at, updated_at) VALUES(:uuid, :organization_id, :tenant_id, " +
                                ":name, :description, NOW(), NOW())")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("uuid", UUID.randomUUID())
                        .bind("name", name)
                        .bind("description", description)
                        .execute()
        );
    }

    public void editCustomBandit(long id, String name, String description) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_bandits SET name = :name, description = :description " +
                                "WHERE id = :id")
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .execute()
        );
    }

    public void deleteCustomBandit(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_bandits WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public List<CustomBanditDescription> findAllCustomBandits(UUID organizationId,
                                                              UUID tenantId,
                                                              int limit,
                                                              int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_bandits " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "ORDER BY name ASC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(CustomBanditDescription.class)
                        .list()
        );
    }

    public Optional<CustomBanditDescription> findCustomBandit(UUID uuid) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_bandits WHERE uuid = :uuid")
                        .bind("uuid", uuid)
                        .mapTo(CustomBanditDescription.class)
                        .findOne()
        );
    }

    public Optional<CustomBanditDescription> findCustomBanditByFingerprint(String fingerprint,
                                                                           UUID organizationId,
                                                                           UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.* FROM dot11_bandits AS b " +
                                "WHERE b.organization_id = :organization_id AND b.tenant_id = :tenant_id " +
                                "AND EXISTS (SELECT 1 FROM dot11_bandit_fingerprints AS f " +
                                "WHERE f.bandit_id = b.id AND f.fingerprint = :fingerprint) " +
                                "ORDER BY b.name ASC LIMIT 1")
                        .bind("fingerprint", fingerprint)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(CustomBanditDescription.class)
                        .findOne()
        );
    }

    public List<String> findFingerprintsOfCustomBandit(long banditId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT fingerprint FROM dot11_bandit_fingerprints " +
                                "WHERE bandit_id = :bandit_id")
                        .bind("bandit_id", banditId)
                        .mapTo(String.class)
                        .list()
        );
    }

    public void bumpCustomBanditUpdatedAt(long banditId) {
        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("UPDATE dot11_bandits SET updated_at = NOW() WHERE id = :id")
                    .bind("id", banditId)
                    .execute();
        });
    }

    public void addFingerprintOfCustomBandit(long banditId, String fingerprint) {
        if (fingerprint.length() != 64) {
            throw new RuntimeException("Fingerprint must be 64 characters long.");
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO dot11_bandit_fingerprints(bandit_id, fingerprint) " +
                                "VALUES(:bandit_id, :fingerprint)")
                        .bind("bandit_id", banditId)
                        .bind("fingerprint", fingerprint)
                        .execute()
        );
    }

    public void removeFingerprintOfCustomBandit(long banditId, String fingerprint) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_bandit_fingerprints " +
                                "WHERE bandit_id = :bandit_id AND fingerprint = :fingerprint")
                        .bind("bandit_id", banditId)
                        .bind("fingerprint", fingerprint)
                        .execute()
        );
    }

    public static final String BROADCAST_MAC = "FF:FF:FF:FF:FF:FF";

    private static final String DISCO_BSSID_RESTRICTION_SQL =
            "AND (transmitter IN (<bssids>) OR receiver IN (<bssids>) OR bssid IN (<bssids>)) ";

    // A null BSSID list applies no restriction, an empty list yields an empty result.
    private String discoBssidRestriction(@Nullable List<String> bssids) {
        return bssids == null ? "" : DISCO_BSSID_RESTRICTION_SQL;
    }

    private Query bindBssidScopedQuery(Query query,
                                 TimeRange timeRange,
                                 List<UUID> taps,
                                 FilterSqlFragment filterFragment,
                                 @Nullable List<String> bssids) {
        query.bind("tr_from", timeRange.from())
                .bind("tr_to", timeRange.to())
                .bindList("taps", taps)
                .bindMap(filterFragment.bindings());

        if (bssids != null) {
            query.bindList("bssids", bssids);
        }

        return query;
    }

    private boolean bssidScopedQueryIsEmpty(List<UUID> taps, @Nullable List<String> bssids) {
        return taps.isEmpty() || (bssids != null && bssids.isEmpty());
    }

    public List<DiscoHistogramEntry> getDiscoHistogram(TimeRange timeRange,
                                                       Bucketing.BucketingConfiguration bucketing,
                                                       List<UUID> taps,
                                                       Filters filters,
                                                       @Nullable List<String> bssids) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT DATE_TRUNC(:date_trunc, created_at) AS bucket, " +
                                "SUM(frame_count) AS frame_count, " +
                                "COALESCE(SUM(frame_count) FILTER (WHERE disco_type = 'DEAUTHENTICATION'), 0) " +
                                "AS deauthentication_count, " +
                                "COALESCE(SUM(frame_count) FILTER (WHERE disco_type = 'DISASSOCIATION'), 0) " +
                                "AS disassociation_count, " +
                                "COALESCE(SUM(frame_count) FILTER (WHERE receiver = :broadcast), 0) " +
                                "AS broadcast_count " +
                                "FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids) +
                                "GROUP BY bucket ORDER BY bucket DESC"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("broadcast", BROADCAST_MAC)
                        .mapTo(DiscoHistogramEntry.class)
                        .list()
        );
    }

    public List<DiscoFrameEntry> findDiscoFrames(TimeRange timeRange,
                                                 Filters filters,
                                                 @Nullable List<String> bssids,
                                                 int limit,
                                                 int offset,
                                                 DiscoFrameOrderColumn orderColumn,
                                                 OrderDirection orderDirection,
                                                 List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT * FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids) +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, id DESC " +
                                "LIMIT :limit OFFSET :offset"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(DiscoFrameEntry.class)
                        .list()
        );
    }

    public long countDiscoFrames(TimeRange timeRange,
                                 Filters filters,
                                 @Nullable List<String> bssids,
                                 List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT COUNT(*) FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids)),
                        timeRange, taps, filterFragment, bssids)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Dot11MacFrameCount> getDiscoMacHistogram(DiscoMacDimension dimension,
                                                         TimeRange timeRange,
                                                         Filters filters,
                                                         @Nullable List<String> bssids,
                                                         int limit,
                                                         int offset,
                                                         TwoColumnHistogramOrderColumn orderColumn,
                                                         OrderDirection orderDirection,
                                                         List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        // BSSIDFrameCountMapper reads the MAC address from a column called "bssid".
        String orderColumnName = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "bssid" : "frame_count";

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT <dimension> AS bssid, SUM(frame_count) AS frame_count " +
                                "FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids) +
                                "GROUP BY <dimension> ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("dimension", dimension.getColumnName())
                        .define("order_column", orderColumnName)
                        .define("order_direction", orderDirection)
                        .mapTo(Dot11MacFrameCount.class)
                        .list()
        );
    }

    public long countDiscoMacHistogram(DiscoMacDimension dimension,
                                       TimeRange timeRange,
                                       Filters filters,
                                       @Nullable List<String> bssids,
                                       List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT COUNT(DISTINCT <dimension>) FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids)),
                        timeRange, taps, filterFragment, bssids)
                        .define("dimension", dimension.getColumnName())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<BSSIDPairFrameCount> getDiscoPairs(TimeRange timeRange,
                                                   Filters filters,
                                                   @Nullable List<String> bssids,
                                                   int limit,
                                                   int offset,
                                                   ThreeColumnHistogramOrderColumn orderColumn,
                                                   OrderDirection orderDirection,
                                                   List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        String orderColumnName = switch (orderColumn) {
            case VALUE1 -> "sender";
            case VALUE2 -> "receiver";
            case VALUE3 -> "frame_count";
        };

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT transmitter AS sender, receiver, " +
                                "SUM(frame_count) AS frame_count FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids) +
                                "GROUP BY transmitter, receiver ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumnName)
                        .define("order_direction", orderDirection)
                        .mapTo(BSSIDPairFrameCount.class)
                        .list()
        );
    }

    public long countDiscoPairs(TimeRange timeRange,
                                Filters filters,
                                @Nullable List<String> bssids,
                                List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids) +
                                "GROUP BY transmitter, receiver) AS pairs"),
                        timeRange, taps, filterFragment, bssids)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DiscoReasonCodeCount> getDiscoReasonCodeHistogram(TimeRange timeRange,
                                                                  Filters filters,
                                                                  @Nullable List<String> bssids,
                                                                  int limit,
                                                                  int offset,
                                                                  TwoColumnHistogramOrderColumn orderColumn,
                                                                  OrderDirection orderDirection,
                                                                  List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        String orderColumnName = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "reason_code" : "frame_count";

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT reason_code, SUM(frame_count) AS frame_count " +
                                "FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids) +
                                "GROUP BY reason_code ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumnName)
                        .define("order_direction", orderDirection)
                        .mapTo(DiscoReasonCodeCount.class)
                        .list()
        );
    }

    public long countDiscoReasonCodes(TimeRange timeRange,
                                      Filters filters,
                                      @Nullable List<String> bssids,
                                      List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT COUNT(DISTINCT reason_code) FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids)),
                        timeRange, taps, filterFragment, bssids)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DiscoFrequencyCount> getDiscoFrequencyHistogram(TimeRange timeRange,
                                                                Filters filters,
                                                                @Nullable List<String> bssids,
                                                                int limit,
                                                                int offset,
                                                                TwoColumnHistogramOrderColumn orderColumn,
                                                                OrderDirection orderDirection,
                                                                List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        String orderColumnName = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "frequency" : "frame_count";

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT frequency, SUM(frame_count) AS frame_count " +
                                "FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids) +
                                "GROUP BY frequency ORDER BY <order_column> <order_direction> NULLS LAST " +
                                "LIMIT :limit OFFSET :offset"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumnName)
                        .define("order_direction", orderDirection)
                        .mapTo(DiscoFrequencyCount.class)
                        .list()
        );
    }

    public long countDiscoFrequencies(TimeRange timeRange,
                                      Filters filters,
                                      @Nullable List<String> bssids,
                                      List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM dot11_disco_frames " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                discoBssidRestriction(bssids) +
                                "GROUP BY frequency) AS frequencies"),
                        timeRange, taps, filterFragment, bssids)
                        .mapTo(Long.class)
                        .one()
        );
    }

    private static final String CHANNEL_SWITCH_SELECTION_SQL =
            "WHERE created_at >= :tr_from AND created_at <= :tr_to AND tap_uuid IN (<taps>) ";

    private String channelSwitchSelection(FilterSqlFragment filterFragment, @Nullable List<String> bssids) {
        return CHANNEL_SWITCH_SELECTION_SQL + filterFragment.whereSql() + " "
                + (bssids == null ? "" : "AND bssid IN (<bssids>) ");
    }

    public List<ChannelSwitchHistogramEntry> getChannelSwitchHistogram(TimeRange timeRange,
                                                                       Bucketing.BucketingConfiguration bucketing,
                                                                       List<UUID> taps,
                                                                       Filters filters,
                                                                       @Nullable List<String> bssids) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ChannelSwitchFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT DATE_TRUNC(:date_trunc, created_at) AS bucket, " +
                                "SUM(frame_count) AS frame_count, " +
                                "COALESCE(SUM(frame_count) FILTER (WHERE cardinality(anomalies) > 0), 0) " +
                                "AS anomalous_frame_count " +
                                "FROM dot11_channel_switches " +
                                channelSwitchSelection(filterFragment, bssids) +
                                "GROUP BY bucket ORDER BY bucket DESC"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .mapTo(ChannelSwitchHistogramEntry.class)
                        .list()
        );
    }

    public List<ChannelSwitchEntry> findChannelSwitches(TimeRange timeRange,
                                                        Filters filters,
                                                        @Nullable List<String> bssids,
                                                        int limit,
                                                        int offset,
                                                        ChannelSwitchOrderColumn orderColumn,
                                                        OrderDirection orderDirection,
                                                        List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ChannelSwitchFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT * FROM dot11_channel_switches " +
                                channelSwitchSelection(filterFragment, bssids) +
                                "ORDER BY <order_column> <order_direction> <order_nulls>, id DESC " +
                                "LIMIT :limit OFFSET :offset"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .define("order_nulls", orderColumn.nullsSql())
                        .mapTo(ChannelSwitchEntry.class)
                        .list()
        );
    }

    public long countChannelSwitches(TimeRange timeRange,
                                     Filters filters,
                                     @Nullable List<String> bssids,
                                     List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ChannelSwitchFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT COUNT(*) FROM dot11_channel_switches " +
                                channelSwitchSelection(filterFragment, bssids)),
                        timeRange, taps, filterFragment, bssids)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Dot11MacFrameCount> getChannelSwitchBssidHistogram(TimeRange timeRange,
                                                                   Filters filters,
                                                                   @Nullable List<String> bssids,
                                                                   int limit,
                                                                   int offset,
                                                                   TwoColumnHistogramOrderColumn orderColumn,
                                                                   OrderDirection orderDirection,
                                                                   List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ChannelSwitchFilters());

        String orderColumnName = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "bssid" : "frame_count";

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT bssid, SUM(frame_count) AS frame_count " +
                                "FROM dot11_channel_switches " +
                                channelSwitchSelection(filterFragment, bssids) +
                                "GROUP BY bssid ORDER BY <order_column> <order_direction>, bssid " +
                                "LIMIT :limit OFFSET :offset"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumnName)
                        .define("order_direction", orderDirection)
                        .mapTo(Dot11MacFrameCount.class)
                        .list()
        );
    }

    public long countChannelSwitchBssids(TimeRange timeRange,
                                         Filters filters,
                                         @Nullable List<String> bssids,
                                         List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ChannelSwitchFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT COUNT(DISTINCT bssid) FROM dot11_channel_switches " +
                                channelSwitchSelection(filterFragment, bssids)),
                        timeRange, taps, filterFragment, bssids)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<ChannelSwitchChannelCount> getChannelSwitchChannelHistogram(TimeRange timeRange,
                                                                            Filters filters,
                                                                            @Nullable List<String> bssids,
                                                                            int limit,
                                                                            int offset,
                                                                            TwoColumnHistogramOrderColumn orderColumn,
                                                                            OrderDirection orderDirection,
                                                                            List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ChannelSwitchFilters());

        String orderColumnName = orderColumn == TwoColumnHistogramOrderColumn.KEY ? "new_channel" : "frame_count";

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT new_channel, new_frequency, " +
                                "SUM(frame_count) AS frame_count FROM dot11_channel_switches " +
                                channelSwitchSelection(filterFragment, bssids) +
                                "GROUP BY new_channel, new_frequency " +
                                "ORDER BY <order_column> <order_direction>, new_channel, new_frequency " +
                                "LIMIT :limit OFFSET :offset"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumnName)
                        .define("order_direction", orderDirection)
                        .mapTo(ChannelSwitchChannelCount.class)
                        .list()
        );
    }

    public long countChannelSwitchChannels(TimeRange timeRange,
                                           Filters filters,
                                           @Nullable List<String> bssids,
                                           List<UUID> taps) {
        if (bssidScopedQueryIsEmpty(taps, bssids)) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11ChannelSwitchFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindBssidScopedQuery(handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM dot11_channel_switches " +
                                channelSwitchSelection(filterFragment, bssids) +
                                "GROUP BY new_channel, new_frequency) AS channels"),
                        timeRange, taps, filterFragment, bssids)
                        .mapTo(Long.class)
                        .one()
        );
    }

    private static final String CHANNEL_SWITCH_COUNTDOWN_END_SQL =
            "p.created_at + (15 + p.count_max * 0.2) * interval '1 second'";

    private static final String CHANNEL_SWITCH_EVIDENCE_END_SQL =
            "p.created_at + :evidence_window * interval '1 minute'";

    /*
     * Judges announcements by the channels the beacons of the BSSID named as their own afterwards, as the same tap
     * recorded them. The countdown of an announcement may still run when it is reported, so only beacons after it
     * ended count. An access point that is on its channel in the second half of that time did not follow, whatever
     * was on the announced channel: a few beacons there must not turn a forged announcement into a followed one.
     * A second transmitter with the same BSSID is on both channels in each half.
     *
     * Oldest first and limited, so that a flood of announcements takes several runs instead of one long one.
     */
    public int resolveChannelSwitchOutcomes(DateTime now, DateTime announcedBefore, boolean monitoredBssidsOnly, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("UPDATE dot11_channel_switches AS cs SET outcome = judged.outcome, " +
                                "outcome_at = :now FROM (" +
                                "SELECT p.id, CASE " +
                                "WHEN p.frequency IS NULL THEN :unknown " +
                                "WHEN p.new_frequency IS NULL AND NOT (:invalid_channel = ANY(p.anomalies)) THEN :unknown " +
                                "WHEN p.new_frequency = p.frequency THEN :unknown " +
                                "WHEN e.halves_on_both = 2 THEN :both_channels " +
                                "WHEN e.old_frames_late >= :minimum_frames_per_half THEN :stayed " +
                                "WHEN e.new_frames >= :minimum_frames THEN :switched " +
                                "WHEN e.old_frames >= :minimum_frames THEN :stayed " +
                                "ELSE :unknown END AS outcome " +
                                "FROM (SELECT id, tap_uuid, bssid, frequency, new_frequency, anomalies, count_max, created_at " +
                                "FROM dot11_channel_switches AS pending " +
                                "WHERE outcome IS NULL AND created_at < :announced_before " +
                                (monitoredBssidsOnly
                                        ? "AND EXISTS (SELECT 1 FROM taps AS t " +
                                        "JOIN dot11_monitored_networks AS mn ON mn.organization_id = t.organization_id " +
                                        "AND mn.tenant_id = t.tenant_id " +
                                        "JOIN dot11_monitored_networks_bssids AS mb ON mb.monitored_network_id = mn.id " +
                                        "WHERE t.uuid = pending.tap_uuid AND mb.bssid = pending.bssid " +
                                        "AND mn.enabled AND mn.enabled_channel_switch_monitor) "
                                        : "") +
                                "ORDER BY created_at LIMIT :limit) AS p " +
                                "CROSS JOIN LATERAL (SELECT COALESCE(SUM(h.old_frames), 0) AS old_frames, " +
                                "COALESCE(SUM(h.new_frames), 0) AS new_frames, " +
                                "COALESCE(SUM(h.old_frames) FILTER (WHERE h.second_half), 0) AS old_frames_late, " +
                                "COUNT(*) FILTER (WHERE h.old_frames >= :minimum_frames_per_half " +
                                "AND h.new_frames >= :minimum_frames_per_half) AS halves_on_both " +
                                "FROM (SELECT b.created_at > " + CHANNEL_SWITCH_COUNTDOWN_END_SQL + " + (" +
                                CHANNEL_SWITCH_EVIDENCE_END_SQL + " - (" + CHANNEL_SWITCH_COUNTDOWN_END_SQL + ")) / 2 " +
                                "AS second_half, " +
                                "COALESCE(SUM((b.declared_frequencies->>(p.frequency::text))::bigint), 0) AS old_frames, " +
                                "COALESCE(SUM((b.declared_frequencies->>(p.new_frequency::text))::bigint), 0) AS new_frames " +
                                "FROM dot11_bssids AS b " +
                                "WHERE b.bssid = p.bssid AND b.tap_uuid = p.tap_uuid " +
                                "AND b.declared_frequencies IS NOT NULL " +
                                "AND b.created_at > " + CHANNEL_SWITCH_COUNTDOWN_END_SQL + " " +
                                "AND b.created_at <= " + CHANNEL_SWITCH_EVIDENCE_END_SQL + " " +
                                "GROUP BY second_half) AS h) AS e" +
                                ") AS judged WHERE cs.id = judged.id AND cs.outcome IS NULL")
                        .bind("now", now)
                        .bind("unknown", ChannelSwitchOutcome.UNKNOWN.name())
                        .bind("switched", ChannelSwitchOutcome.SWITCHED.name())
                        .bind("stayed", ChannelSwitchOutcome.STAYED.name())
                        .bind("both_channels", ChannelSwitchOutcome.BOTH_CHANNELS.name())
                        .bind("invalid_channel", ChannelSwitchAnomaly.INVALID_CHANNEL.name())
                        .bind("minimum_frames", ChannelSwitchRules.OUTCOME_MINIMUM_FRAMES)
                        .bind("minimum_frames_per_half", ChannelSwitchRules.OUTCOME_MINIMUM_FRAMES_PER_HALF)
                        .bind("evidence_window", ChannelSwitchRules.OUTCOME_EVIDENCE_MINUTES)
                        .bind("announced_before", announcedBefore)
                        .bind("limit", limit)
                        .execute()
        );
    }

    private static final String CHANNEL_SWITCH_NETWORK_SELECTION_SQL =
            "WHERE created_at >= :tr_from AND created_at <= :tr_to AND tap_uuid IN (<taps>) " +
                    "AND bssid IN (<bssids>) ";

    // Only what a statement uses can be bound.
    private record ChannelSwitchHighlight(List<String> conditions, boolean burst, List<String> anomalies, List<String> outcomes) {

        private static final String BURST_SQL = "(frame_type = :beacon AND peak_frames_per_second >= :burst_threshold)";
        private static final String OUTCOME_SQL = "outcome IN (<alerting_outcomes>)";

        static ChannelSwitchHighlight burstThreshold() {
            return new ChannelSwitchHighlight(List.of(BURST_SQL), true, List.of(), List.of());
        }

        static ChannelSwitchHighlight monitor(Set<ChannelSwitchMonitorRule> disabledRules) {
            List<String> anomalies = ChannelSwitchMonitorRule.enabledAnomalies(disabledRules);
            boolean burst = !disabledRules.contains(ChannelSwitchMonitorRule.BURST_RATE);

            List<String> conditions = Lists.newArrayList();
            if (!anomalies.isEmpty()) {
                conditions.add("anomalies && ARRAY[<alerting>]::text[]");
            }
            if (burst) {
                conditions.add(BURST_SQL);
            }

            // On both channels, the access point is still on its own as well.
            List<String> outcomes = Lists.newArrayList();
            if (!disabledRules.contains(ChannelSwitchMonitorRule.NOT_FOLLOWED)) {
                outcomes.add(ChannelSwitchOutcome.STAYED.name());
                outcomes.add(ChannelSwitchOutcome.BOTH_CHANNELS.name());
            }

            return new ChannelSwitchHighlight(conditions, burst, anomalies, outcomes);
        }

        String conditionSql() {
            List<String> all = Lists.newArrayList(conditions);
            if (!outcomes.isEmpty()) {
                all.add(OUTCOME_SQL);
            }

            return all.isEmpty() ? "FALSE" : String.join(" OR ", all);
        }

        // An announcement is judged minutes after it was recorded, so its outcome counts from when it was judged.
        String conditionSql(String recentSql, String judgedSql) {
            return "((" + recentSql + " AND (" + (conditions.isEmpty() ? "FALSE" : String.join(" OR ", conditions))
                    + ")) OR (" + judgedSql + " AND " + (outcomes.isEmpty() ? "FALSE" : OUTCOME_SQL) + "))";
        }

        <T extends SqlStatement<T>> T bind(T statement, int burstThreshold) {
            if (burst) {
                statement.bind("burst_threshold", burstThreshold)
                        .bind("beacon", ChannelSwitchRules.FRAME_TYPE_BEACON);
            }
            if (!anomalies.isEmpty()) {
                statement.bindList("alerting", anomalies);
            }
            if (!outcomes.isEmpty()) {
                statement.bindList("alerting_outcomes", outcomes);
            }

            return statement;
        }

    }

    private <T extends SqlStatement<T>> T bindNetworkChannelSwitches(T statement,
                                                                    TimeRange timeRange,
                                                                    List<String> bssids,
                                                                    List<UUID> taps) {
        return statement
                .bind("tr_from", timeRange.from())
                .bind("tr_to", timeRange.to())
                .bindList("taps", taps)
                .bindList("bssids", bssids);
    }

    public List<ChannelSwitchEntry> findChannelSwitchesAtBurstThreshold(TimeRange timeRange,
                                                                       List<String> bssids,
                                                                       int burstThreshold,
                                                                       int limit,
                                                                       int offset,
                                                                       ChannelSwitchOrderColumn orderColumn,
                                                                       OrderDirection orderDirection,
                                                                       List<UUID> taps) {
        if (taps.isEmpty() || bssids.isEmpty()) {
            return Collections.emptyList();
        }

        ChannelSwitchHighlight highlight = ChannelSwitchHighlight.burstThreshold();

        return nzyme.getDatabase().withHandle(handle ->
                highlight.bind(bindNetworkChannelSwitches(handle.createQuery("SELECT * FROM dot11_channel_switches " +
                                CHANNEL_SWITCH_NETWORK_SELECTION_SQL +
                                "AND " + highlight.conditionSql() + " " +
                                "ORDER BY <order_column> <order_direction> <order_nulls>, id DESC " +
                                "LIMIT :limit OFFSET :offset"),
                        timeRange, bssids, taps), burstThreshold)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .define("order_nulls", orderColumn.nullsSql())
                        .mapTo(ChannelSwitchEntry.class)
                        .list()
        );
    }

    public long countChannelSwitchesAtBurstThreshold(TimeRange timeRange,
                                                     List<String> bssids,
                                                     int burstThreshold,
                                                     List<UUID> taps) {
        if (taps.isEmpty() || bssids.isEmpty()) {
            return 0;
        }

        ChannelSwitchHighlight highlight = ChannelSwitchHighlight.burstThreshold();

        return nzyme.getDatabase().withHandle(handle ->
                highlight.bind(bindNetworkChannelSwitches(handle.createQuery("SELECT COUNT(*) FROM dot11_channel_switches " +
                                CHANNEL_SWITCH_NETWORK_SELECTION_SQL +
                                "AND " + highlight.conditionSql()),
                        timeRange, bssids, taps), burstThreshold)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public record HighlightedChannelSwitchBucket(DateTime bucket, long frameCount, long highlightedFrameCount) { }

    public List<HighlightedChannelSwitchBucket> getChannelSwitchSimulationHistogram(TimeRange timeRange,
                                                                                    Bucketing.BucketingConfiguration bucketing,
                                                                                    List<String> bssids,
                                                                                    int burstThreshold,
                                                                                    List<UUID> taps) {
        return highlightedChannelSwitchHistogram(
                timeRange, bucketing, bssids, burstThreshold, ChannelSwitchHighlight.burstThreshold(), taps
        );
    }

    public List<HighlightedChannelSwitchBucket> getChannelSwitchMonitorHistogram(TimeRange timeRange,
                                                                                 Bucketing.BucketingConfiguration bucketing,
                                                                                 List<String> bssids,
                                                                                 int burstThreshold,
                                                                                 Set<ChannelSwitchMonitorRule> disabledRules,
                                                                                 List<UUID> taps) {
        return highlightedChannelSwitchHistogram(
                timeRange, bucketing, bssids, burstThreshold, ChannelSwitchHighlight.monitor(disabledRules), taps
        );
    }

    private List<HighlightedChannelSwitchBucket> highlightedChannelSwitchHistogram(TimeRange timeRange,
                                                                                   Bucketing.BucketingConfiguration bucketing,
                                                                                   List<String> bssids,
                                                                                   int burstThreshold,
                                                                                   ChannelSwitchHighlight highlight,
                                                                                   List<UUID> taps) {
        if (taps.isEmpty() || bssids.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                highlight.bind(bindNetworkChannelSwitches(handle.createQuery(
                                "SELECT DATE_TRUNC(:date_trunc, created_at) AS bucket, " +
                                        "SUM(frame_count) AS frame_count, " +
                                        "COALESCE(SUM(frame_count) FILTER (WHERE " + highlight.conditionSql() + "), 0) " +
                                        "AS highlighted_frame_count FROM dot11_channel_switches " +
                                        CHANNEL_SWITCH_NETWORK_SELECTION_SQL +
                                        "GROUP BY bucket ORDER BY bucket DESC"),
                        timeRange, bssids, taps), burstThreshold)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .map((rs, ctx) -> new HighlightedChannelSwitchBucket(
                                new DateTime(rs.getTimestamp("bucket")),
                                rs.getLong("frame_count"),
                                rs.getLong("highlighted_frame_count")
                        ))
                        .list()
        );
    }

    public void setChannelSwitchMonitorRuleEnabled(long monitoredNetworkId, ChannelSwitchMonitorRule rule, boolean enabled) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_monitored_networks SET dconf_channel_switch_disabled_rules = " +
                                (enabled
                                        ? "array_remove(dconf_channel_switch_disabled_rules, :rule) "
                                        : "array_append(array_remove(dconf_channel_switch_disabled_rules, :rule), :rule) ") +
                                "WHERE id = :id")
                        .bind("id", monitoredNetworkId)
                        .bind("rule", rule.name())
                        .execute()
        );
    }

    public List<String> findBssidsDeclaringSeveralFrequencies(List<UUID> taps, List<String> bssids, DateTime since) {
        if (taps.isEmpty() || bssids.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bssid FROM dot11_bssids AS b " +
                                "CROSS JOIN LATERAL jsonb_object_keys(b.declared_frequencies) AS f(frequency) " +
                                "WHERE b.tap_uuid IN (<taps>) AND b.bssid IN (<bssids>) AND b.created_at > :since " +
                                "AND b.declared_frequencies IS NOT NULL " +
                                "GROUP BY b.bssid HAVING COUNT(DISTINCT f.frequency) > 1")
                        .bindList("taps", taps)
                        .bindList("bssids", bssids)
                        .bind("since", since)
                        .mapTo(String.class)
                        .list()
        );
    }

    public record DeclaredFrequency(UUID tapUuid, String bssid, DateTime at, int frequency) { }

    // The newest first, so that a limit cuts off the oldest part of the time.
    public List<DeclaredFrequency> findDeclaredFrequencies(List<UUID> taps,
                                                           List<String> bssids,
                                                           DateTime since,
                                                           int minimumBeacons,
                                                           int limit) {
        if (taps.isEmpty() || bssids.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.tap_uuid, b.bssid, b.created_at, f.frequency::integer AS frequency " +
                                "FROM dot11_bssids AS b " +
                                "CROSS JOIN LATERAL jsonb_each_text(b.declared_frequencies) AS f(frequency, beacons) " +
                                "WHERE b.tap_uuid IN (<taps>) AND b.bssid IN (<bssids>) AND b.created_at > :since " +
                                "AND b.declared_frequencies IS NOT NULL AND f.beacons::bigint >= :minimum_beacons " +
                                "ORDER BY b.created_at DESC LIMIT :limit")
                        .bindList("taps", taps)
                        .bindList("bssids", bssids)
                        .bind("since", since)
                        .bind("minimum_beacons", minimumBeacons)
                        .bind("limit", limit)
                        .map((rs, ctx) -> new DeclaredFrequency(
                                UUID.fromString(rs.getString("tap_uuid")),
                                rs.getString("bssid"),
                                new DateTime(rs.getTimestamp("created_at")),
                                rs.getInt("frequency")
                        ))
                        .list()
        );
    }

    public record TenantId(UUID organizationId, UUID tenantId) { }

    public List<TenantId> findTenantsMonitoringMultipleChannels() {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT organization_id, tenant_id FROM dot11_monitored_networks " +
                                "WHERE enabled AND enabled_multiple_channels")
                        .map((rs, ctx) -> new TenantId(
                                UUID.fromString(rs.getString("organization_id")),
                                UUID.fromString(rs.getString("tenant_id"))
                        ))
                        .list()
        );
    }

    public List<TenantId> findTenantsWithChannelSwitches(DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT t.organization_id, t.tenant_id FROM taps AS t " +
                                "WHERE t.deleted = false AND (EXISTS (SELECT 1 FROM dot11_channel_switches AS cs " +
                                "WHERE cs.tap_uuid = t.uuid AND cs.created_at > :since) " +
                                "OR t.uuid IN (SELECT cs.tap_uuid FROM dot11_channel_switches AS cs " +
                                "WHERE cs.outcome_at > :since))")
                        .bind("since", since)
                        .map((rs, ctx) -> new TenantId(
                                UUID.fromString(rs.getString("organization_id")),
                                UUID.fromString(rs.getString("tenant_id"))
                        ))
                        .list()
        );
    }

    public record AlertingChannelSwitches(UUID tapUuid,
                                          String bssid,
                                          List<String> anomalies,
                                          int peakFramesPerSecond,
                                          boolean notFollowed,
                                          DateTime earliest,
                                          String frameType,
                                          int newChannel,
                                          @Nullable Integer frequency,
                                          DateTime latest) { }

    // One row per tap and BSSID, however many announcements a flood left behind.
    public List<AlertingChannelSwitches> findAlertingChannelSwitches(List<UUID> taps,
                                                                    List<String> bssids,
                                                                    DateTime since,
                                                                    int burstThreshold,
                                                                    Set<ChannelSwitchMonitorRule> disabledRules) {
        if (taps.isEmpty() || bssids.isEmpty()) {
            return Collections.emptyList();
        }

        ChannelSwitchHighlight highlight = ChannelSwitchHighlight.monitor(disabledRules);

        return nzyme.getDatabase().withHandle(handle ->
                highlight.bind(handle.createQuery("SELECT cs.tap_uuid, cs.bssid, " +
                                "ARRAY_AGG(DISTINCT a.anomaly) FILTER (WHERE a.anomaly IS NOT NULL) AS anomalies, " +
                                "MAX(cs.peak_frames_per_second) AS peak_frames_per_second, " +
                                "COALESCE(BOOL_OR(cs.outcome IN (<not_followed_outcomes>)), false) AS not_followed, " +
                                "MIN(cs.created_at) AS earliest, " +
                                "(ARRAY_AGG(cs.frame_type ORDER BY cs.created_at DESC, cs.id DESC))[1] AS frame_type, " +
                                "(ARRAY_AGG(cs.new_channel ORDER BY cs.created_at DESC, cs.id DESC))[1] AS new_channel, " +
                                "(ARRAY_AGG(cs.frequency ORDER BY cs.created_at DESC, cs.id DESC))[1] AS frequency, " +
                                "MAX(cs.created_at) AS latest " +
                                "FROM dot11_channel_switches AS cs " +
                                "LEFT JOIN LATERAL UNNEST(cs.anomalies) AS a(anomaly) ON true " +
                                "WHERE cs.tap_uuid IN (<taps>) AND cs.bssid IN (<bssids>) AND " +
                                highlight.conditionSql("cs.created_at > :since", "cs.outcome_at > :since") + " " +
                                "GROUP BY cs.tap_uuid, cs.bssid"), burstThreshold)
                        .bindList("taps", taps)
                        .bindList("bssids", bssids)
                        .bind("since", since)
                        .bindList("not_followed_outcomes", List.of(
                                ChannelSwitchOutcome.STAYED.name(), ChannelSwitchOutcome.BOTH_CHANNELS.name()
                        ))
                        .map((rs, ctx) -> {
                            Array anomalies = rs.getArray("anomalies");
                            return new AlertingChannelSwitches(
                                    UUID.fromString(rs.getString("tap_uuid")),
                                    rs.getString("bssid"),
                                    anomalies == null ? List.of() : Arrays.asList((String[]) anomalies.getArray()),
                                    rs.getInt("peak_frames_per_second"),
                                    rs.getBoolean("not_followed"),
                                    new DateTime(rs.getTimestamp("earliest")),
                                    rs.getString("frame_type"),
                                    rs.getInt("new_channel"),
                                    rs.getObject("frequency", Integer.class),
                                    new DateTime(rs.getTimestamp("latest"))
                            );
                        })
                        .list()
        );
    }

    public Dot11DiscoMonitorMethodConfiguration getDiscoMonitorMethodConfiguration(long monitoredNetworkId) {
        Optional<MonitoredSSID> monitoredNetwork = nzyme.getDatabase().withHandle(handle ->
            handle.createQuery("SELECT * FROM dot11_monitored_networks " +
                            "WHERE id = :id")
                    .bind("id", monitoredNetworkId)
                    .mapTo(MonitoredSSID.class)
                    .findOne()
        );

        if (monitoredNetwork.isEmpty()) {
            throw new RuntimeException("Monitored network with ID <" + monitoredNetworkId + "> not found.");
        }

        ObjectMapper om = new ObjectMapper();
        Map<String, Object> configuration;

        try {
            configuration = om.readValue(monitoredNetwork.get().discoMonitorConfiguration(), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Could not parse 802.11 disconnection monitor configuration of " +
                    "monitored network [" + monitoredNetwork.get().uuid() + "].", e);
        }

        return Dot11DiscoMonitorMethodConfiguration.create(monitoredNetwork.get().discoMonitorType(), configuration);
    }

    public void setDiscoMonitorMethodConfiguration(String methodType,
                                                   String configuration,
                                                   long monitoredNetworkId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_monitored_networks SET disco_monitor_type = :monitor_type, " +
                                "disco_monitor_configuration = :configuration WHERE id = :id")
                        .bind("monitor_type", methodType)
                        .bind("configuration", configuration)
                        .bind("id", monitoredNetworkId)
                        .execute()
        );
    }

    public List<String> findBSSIDsAdvertisingSSID(String ssid, List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT(bssid) FROM dot11_ssids " +
                                "WHERE ssid = :ssid AND tap_uuid IN (<taps>) " +
                                "AND created_at >= (NOW() - INTERVAL '24 hours') " +
                                "ORDER BY bssid ASC")
                        .bind("ssid", ssid)
                        .bindList("taps", taps)
                        .mapTo(String.class)
                        .list()
        );
    }

    public List<String> findFingerprintsOfBSSID(String bssid, TimeRange timeRange, List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT fingerprints_elem FROM dot11_bssids " +
                                "LEFT JOIN LATERAL UNNEST(fingerprints) AS fingerprints_elem ON TRUE " +
                                "WHERE bssid = :bssid AND tap_uuid IN (<taps>) " +
                                "AND created_at >= :tr_from AND created_at <= :tr_to")
                        .bind("bssid", bssid)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(String.class)
                        .list()
        );
    }

    public List<Dot11SecuritySuiteJson> findSecuritySuitesOfSSID(String ssid, List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COALESCE(ARRAY_AGG(DISTINCT security_elem) FILTER (WHERE security_elem IS NOT NULL), " +
                                "ARRAY[]::jsonb[]) AS security_settings FROM dot11_ssids AS s " +
                                "LEFT JOIN LATERAL JSONB_ARRAY_ELEMENTS(s.security_settings) AS security_elem ON TRUE  " +
                                "WHERE s.ssid = :ssid AND s.tap_uuid IN (<taps>) " +
                                "AND s.created_at >= (NOW() - INTERVAL '24 hours') " +
                                "ORDER BY security_settings ASC")
                        .bind("ssid", ssid)
                        .bindList("taps", taps)
                        .map((rs, ctx) ->
                                SecuritySettingsParser.parseSecuritySettings(rs.getArray("security_settings")))
                        .findOne()
                        .orElse(Lists.newArrayList())
        );
    }

    public List<Long> findChannelsOfSSID(String ssid, List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT(c.frequency) FROM dot11_ssids AS s " +
                                "LEFT JOIN dot11_channels AS c ON s.id = c.ssid_id " +
                                "WHERE s.ssid = :ssid AND s.tap_uuid IN (<taps>) AND c.frequency IS NOT NULL " +
                                "AND s.created_at >= (NOW() - INTERVAL '24 hours') " +
                                "ORDER BY c.frequency ASC")
                        .bind("ssid", ssid)
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .list()
        );
    }

    public List<TapBasedSignalStrengthResult> findBSSIDSignalStrengthPerTap(String bssid,
                                                                            TimeRange timeRange,
                                                                            List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.tap_uuid AS tap_uuid, t.name AS tap_name, " +
                                "AVG(b.signal_strength_average) AS signal_strength " +
                                "FROM dot11_bssids AS b " +
                                "LEFT JOIN taps AS t ON b.tap_uuid = t.uuid " +
                                "WHERE b.bssid = :bssid  AND b.tap_uuid IN (<taps>) " +
                                "AND b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "GROUP BY b.tap_uuid, t.name ORDER BY signal_strength DESC")
                        .bind("bssid", bssid)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(TapBasedSignalStrengthResult.class)
                        .list()
        );
    }

    public List<TapBasedSignalStrengthResultHistogramEntry> getBSSIDSignalStrengthPerTapHistogram(String bssid,
                                                                                                  TimeRange timeRange,
                                                                                                  Bucketing.BucketingConfiguration bucketing,
                                                                                                  List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DATE_TRUNC(:date_trunc, b.created_at) AS bucket, " +
                                "b.tap_uuid AS tap_uuid, t.name AS tap_name, " +
                                "AVG(b.signal_strength_average) AS signal_strength " +
                                "FROM dot11_bssids AS b LEFT JOIN taps AS t ON b.tap_uuid = t.uuid " +
                                "WHERE b.bssid = :bssid AND b.tap_uuid IN (<taps>) " +
                                "AND b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                                "GROUP BY b.tap_uuid, t.name, bucket ORDER BY bucket DESC")
                        .bind("bssid", bssid)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .mapTo(TapBasedSignalStrengthResultHistogramEntry.class)
                        .list()
        );
    }

    public long countAllMonitoredProbeRequests(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dot11_monitored_probereqs " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<MonitoredProbeRequestEntry> findAllMonitoredProbeRequests(UUID organizationId,
                                                                          UUID tenantId,
                                                                          int limit,
                                                                          int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_monitored_probereqs " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(MonitoredProbeRequestEntry.class)
                        .list()
        );
    }

    public Optional<MonitoredProbeRequestEntry> findMonitoredProbeRequest(UUID uuid,
                                                                          UUID organizationId,
                                                                          UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_monitored_probereqs WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(MonitoredProbeRequestEntry.class)
                        .findOne()
        );
    }

    public Optional<MonitoredProbeRequestEntry> findMonitoredProbeRequest(UUID uuid) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_monitored_probereqs WHERE uuid = :uuid")
                        .bind("uuid", uuid)
                        .mapTo(MonitoredProbeRequestEntry.class)
                        .findOne()
        );
    }

    public void createMonitoredProbeRequest(UUID organizationId,
                                            UUID tenantId,
                                            String ssid,
                                            String notes) {
        final String sanitizedNotes;
        if (notes != null && !notes.trim().isEmpty()) {
            sanitizedNotes = notes.trim();
        } else {
            sanitizedNotes = null;
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO dot11_monitored_probereqs(uuid, organization_id, tenant_id, " +
                                "ssid, notes, updated_at, created_at) VALUES(:uuid, :organization_id, :tenant_id, " +
                                ":ssid, :notes, NOW(), NOW())")
                        .bind("uuid", UUID.randomUUID())
                        .bind("ssid", ssid.trim())
                        .bind("notes", sanitizedNotes)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void updateMonitoredProbeRequest(UUID uuid,
                                            UUID organizationId,
                                            UUID tenantId,
                                            String ssid,
                                            String notes) {
        final String sanitizedNotes;
        if (notes != null && !notes.trim().isEmpty()) {
            sanitizedNotes = notes.trim();
        } else {
            sanitizedNotes = null;
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_monitored_probereqs SET ssid = :ssid, notes = :notes, " +
                                "updated_at = NOW() WHERE uuid = :uuid AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("ssid", ssid.trim())
                        .bind("notes", sanitizedNotes)
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteMonitoredProbeRequest(UUID uuid,
                                            UUID organizationId,
                                            UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_monitored_probereqs WHERE uuid = :uuid AND " +
                                "organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public long countAllKnownNetworks(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dot11_known_networks " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public long countAllKnownNetworksByPattern(UUID organizationId, UUID tenantId, Pattern regex) {
        return nzyme.getDatabase().withHandle(handle ->
            handle.createQuery("SELECT COUNT(*) FROM dot11_known_networks " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND ssid ~ :regex")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("regex", regex.pattern())
                    .mapTo(Long.class)
                    .one()
        );
    }

    public List<Dot11KnownNetwork> findAllKnownNetworks(UUID organizationId,
                                                        UUID tenantId,
                                                        KnownSSIDOrderColumn orderColumn,
                                                        OrderDirection orderDirection,
                                                        int limit,
                                                        int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                findAllKnownNetworks(handle, organizationId, tenantId, orderColumn, orderDirection, limit, offset)
        );
    }

    public List<Dot11KnownNetwork> findAllKnownNetworks(Handle handle,
                                                        UUID organizationId,
                                                        UUID tenantId,
                                                        KnownSSIDOrderColumn orderColumn,
                                                        OrderDirection orderDirection,
                                                        int limit,
                                                        int offset) {
        return handle.createQuery("SELECT * FROM dot11_known_networks " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("limit", limit)
                .bind("offset", offset)
                .define("order_column", orderColumn.getColumnName())
                .define("order_direction", orderDirection)
                .mapTo(Dot11KnownNetwork.class)
                .list();
    }

    public List<Dot11KnownNetwork> findAllKnownNetworksByPattern(UUID organizationId,
                                                                 UUID tenantId,
                                                                 Pattern regex,
                                                                 KnownSSIDOrderColumn orderColumn,
                                                                 OrderDirection orderDirection,
                                                                 int limit,
                                                                 int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_known_networks " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND ssid ~ :regex " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("regex", regex.pattern())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(Dot11KnownNetwork.class)
                        .list()
        );
    }

    public Optional<Dot11KnownNetwork> findKnownNetwork(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_known_networks " +
                                "WHERE uuid = :uuid AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Dot11KnownNetwork.class)
                        .findOne()
        );
    }

    public Optional<Dot11KnownNetwork> findKnownNetworkBySSID(String ssid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                // Nodes can each have created a row for the SSID. One that is still open is the one to act on.
                handle.createQuery("SELECT * FROM dot11_known_networks " +
                                "WHERE ssid = :ssid AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " +
                                "ORDER BY (is_approved OR is_ignored) ASC, id ASC LIMIT 1")
                        .bind("ssid", ssid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Dot11KnownNetwork.class)
                        .findOne()
        );
    }

    public void changeStatusOfKnownNetwork(long id, boolean isApproved) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_known_networks SET is_approved = :is_approved WHERE id = :id")
                        .bind("id", id)
                        .bind("is_approved", isApproved)
                        .execute()
        );
    }

    public void changeIgnoreStatusOfKnownNetwork(long id, boolean isIgnored) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_known_networks SET is_ignored = :is_ignored WHERE id = :id")
                        .bind("id", id)
                        .bind("is_ignored", isIgnored)
                        .execute()
        );
    }

    public void deleteKnownNetwork(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_known_networks WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public int applyToKnownNetworks(List<UUID> uuids,
                                    KnownNetworkBulkAction action,
                                    UUID organizationId,
                                    UUID tenantId) {
        if (uuids.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate(action.statement + " WHERE uuid IN (<uuids>) " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id " +
                                action.condition)
                        .bindList("uuids", uuids)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteKnownNetworksOfTenant(UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_known_networks " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void changeStatusOfAllKnownNetworksOfTenant(UUID organizationId, UUID tenantId, boolean isApproved) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_known_networks SET is_approved = :is_approved " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("is_approved", isApproved)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void changeStatusOfAllKnownNetworksOfTenantByRegex(UUID organizationId, UUID tenantId, Pattern regex, boolean isApproved) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_known_networks SET is_approved = :is_approved " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND ssid ~ :regex")
                        .bind("regex", regex.pattern())
                        .bind("is_approved", isApproved)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void createKnownNetwork(Handle handle, String ssid, UUID organizationId, UUID tenantId) {
        handle.createUpdate("INSERT INTO dot11_known_networks(uuid, ssid, is_approved, is_ignored, " +
                        "organization_id, tenant_id, first_seen, last_seen) VALUES(:uuid, :ssid, :is_approved, " +
                        ":is_ignored, :organization_id, :tenant_id, NOW(), NOW())")
                .bind("uuid", UUID.randomUUID())
                .bind("ssid", ssid)
                .bind("is_approved", false)
                .bind("is_ignored", false)
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .execute();
    }

    public void touchKnownNetwork(Handle handle, long id) {
        handle.createUpdate("UPDATE dot11_known_networks SET last_seen = NOW() WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public void retentionCleanKnownNetworks(DateTime since) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_known_networks WHERE last_seen < :since")
                        .bind("since", since)
                        .execute()
        );
    }

    public long countAllKnownClients(long monitoredNetworkId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dot11_known_clients " +
                                "WHERE monitored_network_id = :monitored_network_id")
                        .bind("monitored_network_id", monitoredNetworkId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Dot11KnownClient> findAllKnownClients(long monitoredNetworkId, int limit, int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_known_clients " +
                                "WHERE monitored_network_id = :monitored_network_id ORDER BY mac ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("monitored_network_id", monitoredNetworkId)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(Dot11KnownClient.class)
                        .list()
        );
    }

    public Optional<Dot11KnownClient> findKnownClient(Handle handle, String mac, long monitoredNetworkId) {
        return handle.createQuery("SELECT * FROM dot11_known_clients " +
                        "WHERE mac = :mac AND monitored_network_id = :monitored_network_id")
                .bind("mac", mac)
                .bind("monitored_network_id", monitoredNetworkId)
                .mapTo(Dot11KnownClient.class)
                .findOne();
    }

    public Optional<Dot11KnownClient> findKnownClient(String mac, long monitoredNetworkId) {
        return nzyme.getDatabase().withHandle(handle -> findKnownClient(handle, mac, monitoredNetworkId));
    }

    public Optional<Dot11KnownClient> findKnownClientByUuid(UUID uuid, long monitoredNetworkId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_known_clients " +
                                "WHERE uuid = :uuid AND monitored_network_id = :monitored_network_id")
                        .bind("uuid", uuid)
                        .bind("monitored_network_id", monitoredNetworkId)
                        .mapTo(Dot11KnownClient.class)
                        .findOne()
        );
    }


    public void changeStatusOfKnownClient(long id, boolean isApproved) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_known_clients SET is_approved = :is_approved WHERE id = :id")
                        .bind("id", id)
                        .bind("is_approved", isApproved)
                        .execute()
        );
    }

    public void changeIgnoreStatusOfKnownClient(long id, boolean isIgnored) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dot11_known_clients SET is_ignored = :is_ignored WHERE id = :id")
                        .bind("id", id)
                        .bind("is_ignored", isIgnored)
                        .execute()
        );
    }

    public void deleteKnownClient(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_known_clients WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void deleteKnownClientsOfMonitoredNetwork(long monitoredNetworkId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_known_clients " +
                                "WHERE monitored_network_id = :monitored_network_id")
                        .bind("monitored_network_id", monitoredNetworkId)
                        .execute()
        );
    }

    public void createKnownClient(Handle handle, String mac, long monitoredNetworkId) {
        handle.createUpdate("INSERT INTO dot11_known_clients(uuid, mac, is_approved, is_ignored, " +
                        "monitored_network_id, first_seen, last_seen) VALUES(:uuid, :mac, :is_approved, " +
                        ":is_ignored, :monitored_network_id, NOW(), NOW())")
                .bind("uuid", UUID.randomUUID())
                .bind("mac", mac)
                .bind("is_approved", false)
                .bind("is_ignored", false)
                .bind("monitored_network_id", monitoredNetworkId)
                .execute();
    }

    public void touchKnownClient(Handle handle, long id) {
        handle.createUpdate("UPDATE dot11_known_clients SET last_seen = NOW() WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public void retentionCleanKnownClients(DateTime since) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dot11_known_clients WHERE last_seen < :since")
                        .bind("since", since)
                        .execute()
        );
    }

    private static final String SECURITY_SUITE_NO_PMF_SUFFIX = "+PMF_NA";

    public static String securitySuitesToIdentifier(Dot11SecuritySuiteJson suite) {
        return securitySuitesToIdentifier(
                suite.groupCipher(), suite.pairwiseCiphers(), suite.keyManagementModes(), suite.pmfMode()
        );
    }

    public static String securitySuitesToIdentifier(Dot11SecurityInformationReport suite) {
        return securitySuitesToIdentifier(
                suite.suites().groupCipher(),
                Joiner.on(",").join(suite.suites().pairwiseCiphers()),
                Joiner.on(",").join(suite.suites().keyManagementModes()),
                suite.pmf()
        );
    }

    private static String securitySuitesToIdentifier(@Nullable String groupCipher,
                                                     @Nullable String pairwiseCiphers,
                                                     @Nullable String keyManagementModes,
                                                     @Nullable String pmfMode) {
        if (Strings.isNullOrEmpty(groupCipher) && Strings.isNullOrEmpty(pairwiseCiphers)
                && Strings.isNullOrEmpty(keyManagementModes)) {
            return "NONE";
        }

        String id = Strings.nullToEmpty(groupCipher) + "-" + Strings.nullToEmpty(pairwiseCiphers)
                + "/" + Strings.nullToEmpty(keyManagementModes);

        if (!Strings.isNullOrEmpty(pmfMode) && !pmfMode.equals("Unavailable")) {
            return id + "+PMF_" + pmfMode.toUpperCase();
        }

        return id + SECURITY_SUITE_NO_PMF_SUFFIX;
    }

    /*
     * Tap reports used to be compared without the suffix for suites that have no PMF information, so users
     * added those suites to their monitored networks without it.
     */
    public static boolean isMonitoredSecuritySuite(Collection<String> monitoredSuites, String identifier) {
        if (monitoredSuites.contains(identifier)) {
            return true;
        }

        return identifier.endsWith(SECURITY_SUITE_NO_PMF_SUFFIX) && monitoredSuites.contains(
                identifier.substring(0, identifier.length() - SECURITY_SUITE_NO_PMF_SUFFIX.length())
        );
    }

    private static final Map<Integer, Integer> frequencyChannelMap = Maps.newHashMap();

    public static int frequencyToChannel(int frequency) {
        // Also keeps arbitrary input from callers out of the cache below.
        if (frequency < 2400 || frequency > 7125) {
            return -1;
        }

        Integer c = frequencyChannelMap.get(frequency);
        if (c == null) {
            if (frequency == 2484) {
                c = 14;
            } else if (frequency == 5935) {
                /* see 802.11ax D6.1 27.3.23.2 and Annex E */
                c = 2;
            } else if (frequency < 2484) {
                c = (frequency - 2407) / 5;
            } else if (frequency >= 4910 && frequency <= 4980) {
                c = (frequency - 4000) / 5;
            } else if (frequency < 5950) {
                c = (frequency - 5000) / 5;
            } else if (frequency <= 7115) {
                c = (frequency - 5950) / 5;
            } else {
                c = -1;
            }

            // Frequencies between the bands calculate to zero or negative numbers.
            if (c < 1) {
                c = -1;
            }

            // we cache the conversion, since this will called many times
            synchronized (frequencyChannelMap) {
                frequencyChannelMap.put(frequency, c);
            }
        }

        return c;
    }

}
