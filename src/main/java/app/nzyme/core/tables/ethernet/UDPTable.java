package app.nzyme.core.tables.ethernet;

import app.nzyme.core.assets.AssetInformation;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.ethernet.l4.udp.UdpConversationState;
import app.nzyme.core.ethernet.l4.udp.db.UdpConversationEntry;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.integrations.geoip.GeoIpService;
import app.nzyme.core.rest.resources.taps.reports.tables.udp.UdpConversationReport;
import app.nzyme.core.rest.resources.taps.reports.tables.udp.UdpConversationsReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.Tools.stringtoInetAddress;

public class UDPTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(UDPTable.class);

    private final TablesService tablesService;

    private final ObjectMapper om;

    private final Timer totalReportTimer;
    private final Timer conversationsReportTimer;
    private final Timer conversationsDiscoveryTimer;
    private final Timer assetRegistrationTimer;

    private final GeoIpService geoIp;

    public UDPTable(TablesService tablesService) {
        this.tablesService = tablesService;

        this.geoIp = tablesService.getNzyme().getGeoIpService();

        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.UDP_TOTAL_REPORT_PROCESSING_TIMER);

        this.conversationsReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.UDP_CONVERSATIONS_REPORT_PROCESSING_TIMER);

        this.conversationsDiscoveryTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.UDP_CONVERSATION_DISCOVERY_QUERY_TIMER);

        this.assetRegistrationTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.UDP_ASSET_REGISTRATION_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, UdpConversationsReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            tablesService.getProcessorPool().submit(() -> {
                Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);
                if (tap.isEmpty()) {
                    throw new RuntimeException("Reporting tap [" + tapUuid + "] not found. Not processing report.");
                }

                tablesService.getNzyme().getDatabase().useHandle(handle -> {
                    try (Timer.Context ignored2 = conversationsReportTimer.time()) {
                        writeConversations(handle, tap.get(), timestamp, report.conversations());
                    }

                    try (Timer.Context ignored2 = assetRegistrationTimer.time()) {
                        registerAssets(handle, tap.get(), timestamp, report.conversations());
                    }
                });
            });
        }
    }

    private void writeConversations(Handle handle,
                                    Tap tap,
                                    DateTime timestamp,
                                    List<UdpConversationReport> conversations) {
        PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO l4_sessions(tap_uuid, l4_type, " +
                "session_key, untimed_session_key, source_mac, source_address, source_address_is_site_local, " +
                "source_address_is_loopback, source_address_is_multicast, source_port, destination_mac, " +
                "destination_address, destination_address_is_site_local, destination_address_is_loopback, " +
                "destination_address_is_multicast, destination_port, bytes_rx_count, bytes_tx_count, segments_count, " +
                "start_time, end_time, most_recent_segment_time, state, source_address_geo_asn_number, " +
                "source_address_geo_asn_name, source_address_geo_asn_domain, source_address_geo_city, " +
                "source_address_geo_country_code, source_address_geo_latitude, " +
                "source_address_geo_longitude, destination_address_geo_asn_number, " +
                "destination_address_geo_asn_name, destination_address_geo_asn_domain, " +
                "destination_address_geo_city, destination_address_geo_country_code, " +
                "destination_address_geo_latitude, destination_address_geo_longitude, tags, created_at) " +
                "VALUES(:tap_uuid, :l4_type, :session_key, :untimed_session_key, :source_mac, :source_address::inet, " +
                ":source_address_is_site_local, :source_address_is_loopback, :source_address_is_multicast, " +
                ":source_port, :destination_mac, :destination_address::inet, " +
                ":destination_address_is_site_local, :destination_address_is_loopback, " +
                ":destination_address_is_multicast, :destination_port, :bytes_rx_count, :bytes_tx_count, " +
                ":segments_count, :start_time, :end_time, :most_recent_segment_time, :state, " +
                ":source_address_geo_asn_number, :source_address_geo_asn_name, " +
                ":source_address_geo_asn_domain, :source_address_geo_city, " +
                ":source_address_geo_country_code, :source_address_geo_latitude, " +
                ":source_address_geo_longitude, :destination_address_geo_asn_number, " +
                ":destination_address_geo_asn_name, :destination_address_geo_asn_domain, " +
                ":destination_address_geo_city, :destination_address_geo_country_code, " +
                ":destination_address_geo_latitude, :destination_address_geo_longitude, :tags::jsonb, :created_at)");
        PreparedBatch updateBatch = handle.prepareBatch("UPDATE l4_sessions SET state = :state, " +
                "bytes_rx_count = :bytes_rx_count, bytes_tx_count = :bytes_tx_count, " +
                "segments_count = :segments_count, tags = :tags::jsonb, end_time = :end_time, " +
                "most_recent_segment_time = :most_recent_segment_time WHERE id = :id");

        long totalRxBytes = 0;
        long totalTxBytes = 0;
        long totalRxInternalBytes = 0;
        long totalTxInternalBytes = 0;
        long totalDatagrams = 0;
        long totalSessions = 0;
        long totalInternalSessions = 0;

        for (UdpConversationReport conversation : conversations) {
            String tags;
            if (conversation.tags() != null && !conversation.tags().isEmpty()) {
                try {
                    tags = om.writeValueAsString(conversation.tags());
                } catch (JacksonException e) {
                    LOG.error("Could not serialize session tags.", e);
                    tags = null;
                }
            } else {
                tags = null;
            }

            try {
                String sessionKey = Tools.buildL4Key(
                        conversation.startTime(),
                        conversation.sourceAddress(),
                        conversation.destinationAddress(),
                        conversation.sourcePort(),
                        conversation.destinationPort()
                );
                String untimedSessionKey = Tools.buildUntimedL4Key(
                        conversation.sourceAddress(),
                        conversation.destinationAddress(),
                        conversation.sourcePort(),
                        conversation.destinationPort()
                );

                InetAddress sourceAddress = stringtoInetAddress(conversation.sourceAddress());
                InetAddress destinationAddress = stringtoInetAddress(conversation.destinationAddress());
                Optional<GeoIpLookupResult> sourceGeo = geoIp.lookup(sourceAddress);
                Optional<GeoIpLookupResult> destinationGeo = geoIp.lookup(destinationAddress);

                Optional<UdpConversationEntry> existingConversation;
                try (Timer.Context ignored = conversationsDiscoveryTimer.time()) {
                    existingConversation = handle.createQuery("SELECT * FROM l4_sessions " +
                                    "WHERE l4_type = 'UDP' AND session_key = :session_key AND end_time IS NULL " +
                                    "AND tap_uuid = :tap_uuid")
                            .bind("session_key", sessionKey)
                            .bind("tap_uuid", tap.uuid())
                            .mapTo(UdpConversationEntry.class)
                            .findOne();
                }

                if (existingConversation.isPresent()) {
                    // Existing session. Update.
                    updateBatch
                            .bind("state", UdpConversationState.valueOf(conversation.state().toUpperCase()))
                            .bind("bytes_rx_count", conversation.bytesCountRx())
                            .bind("bytes_tx_count", conversation.bytesCountTx())
                            .bind("segments_count", conversation.datagramsCount())
                            .bind("tags", tags)
                            .bind("end_time", conversation.endTime())
                            .bind("most_recent_segment_time", conversation.mostRecentSegmentTime())
                            .bind("id", existingConversation.get().id())
                            .add();
                } else {
                    // This is a new session.
                    insertBatch
                            .bind("tap_uuid", tap.uuid())
                            .bind("l4_type", "UDP")
                            .bind("session_key", sessionKey)
                            .bind("untimed_session_key", untimedSessionKey)
                            .bind("source_mac", conversation.sourceMac())
                            .bind("source_address", conversation.sourceAddress())
                            .bind("source_address_is_site_local", sourceAddress.isSiteLocalAddress())
                            .bind("source_address_is_loopback", sourceAddress.isLoopbackAddress())
                            .bind("source_address_is_multicast", sourceAddress.isMulticastAddress())
                            .bind("source_port", conversation.sourcePort())
                            .bind("destination_mac", conversation.destinationMac())
                            .bind("destination_address", conversation.destinationAddress())
                            .bind("destination_address_is_site_local", destinationAddress.isSiteLocalAddress())
                            .bind("destination_address_is_loopback", destinationAddress.isLoopbackAddress())
                            .bind("destination_address_is_multicast", destinationAddress.isMulticastAddress())
                            .bind("destination_port", conversation.destinationPort())
                            .bind("bytes_rx_count", conversation.bytesCountRx())
                            .bind("bytes_tx_count", conversation.bytesCountTx())
                            .bind("segments_count", conversation.datagramsCount())
                            .bind("start_time", conversation.startTime())
                            .bind("end_time", conversation.endTime())
                            .bind("most_recent_segment_time", conversation.mostRecentSegmentTime())
                            .bind("state", UdpConversationState.valueOf(conversation.state().toUpperCase()))
                            .bind("source_address_geo_asn_number", sourceGeo.map(g -> g.asn().number()).orElse(null))
                            .bind("source_address_geo_asn_name", sourceGeo.map(g -> g.asn().name()).orElse(null))
                            .bind("source_address_geo_asn_domain", sourceGeo.map(g -> g.asn().domain()).orElse(null))
                            .bind("source_address_geo_city", sourceGeo.map(g -> g.geo().city()).orElse(null))
                            .bind("source_address_geo_country_code", sourceGeo.map(g -> g.geo().countryCode()).orElse(null))
                            .bind("source_address_geo_latitude", sourceGeo.map(g -> g.geo().latitude()).orElse(null))
                            .bind("source_address_geo_longitude", sourceGeo.map(g -> g.geo().longitude()).orElse(null))
                            .bind("destination_address_geo_asn_number", destinationGeo.map(g -> g.asn().number()).orElse(null))
                            .bind("destination_address_geo_asn_name", destinationGeo.map(g -> g.asn().name()).orElse(null))
                            .bind("destination_address_geo_asn_domain", destinationGeo.map(g -> g.asn().domain()).orElse(null))
                            .bind("destination_address_geo_city", destinationGeo.map(g -> g.geo().city()).orElse(null))
                            .bind("destination_address_geo_country_code", destinationGeo.map(g -> g.geo().countryCode()).orElse(null))
                            .bind("destination_address_geo_latitude", destinationGeo.map(g -> g.geo().latitude()).orElse(null))
                            .bind("destination_address_geo_longitude", destinationGeo.map(g -> g.geo().longitude()).orElse(null))
                            .bind("tags", tags)
                            .bind("created_at", timestamp)
                            .add();
                }

                totalRxBytes += conversation.bytesCountRxIncremental();
                totalTxBytes += conversation.bytesCountTxIncremental();
                totalDatagrams += conversation.datagramsCountIncremental();
                totalSessions += 1;

                if (sourceAddress.isSiteLocalAddress() && destinationAddress.isSiteLocalAddress()) {
                    totalInternalSessions += 1;
                    totalRxInternalBytes += conversation.bytesCountRxIncremental();
                    totalTxInternalBytes += conversation.bytesCountTxIncremental();
                }
            } catch(Exception e) {
                LOG.error("Could not handle UDP conversation.", e);
            }
        }

        try {
            // Aggregated statistics.
            handle.createUpdate("INSERT INTO l4_statistics(tap_uuid, bytes_rx_udp, bytes_tx_udp, " +
                            "bytes_rx_internal_udp, bytes_tx_internal_udp, datagrams_udp, " +
                            "sessions_udp, sessions_internal_udp, timestamp, created_at) VALUES(:tap_uuid, " +
                            ":bytes_rx_udp, :bytes_tx_udp, :bytes_rx_internal_udp, :bytes_tx_internal_udp, " +
                            ":datagrams_udp, :sessions_udp, :sessions_internal_udp, " +
                            ":timestamp, NOW())")
                    .bind("tap_uuid", tap.uuid())
                    .bind("bytes_rx_udp", totalRxBytes)
                    .bind("bytes_tx_udp", totalTxBytes)
                    .bind("bytes_rx_internal_udp", totalRxInternalBytes)
                    .bind("bytes_tx_internal_udp", totalTxInternalBytes)
                    .bind("datagrams_udp", totalDatagrams)
                    .bind("sessions_udp", totalSessions)
                    .bind("sessions_internal_udp", totalInternalSessions)
                    .bind("timestamp", timestamp)
                    .execute();

            updateBatch.execute();
            insertBatch.execute();
        } catch (Exception e) {
            LOG.error("Could not write UDP conversations.", e);
        }
    }

    private void registerAssets(Handle handle, Tap tap, DateTime timestamp, List<UdpConversationReport> conversations) {
        PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO assets(uuid, organization_id, tenant_id, " +
                "mac, first_seen, last_seen, seen_udp, updated_at, created_at) VALUES(:uuid, " +
                ":organization_id, :tenant_id, :mac, :first_seen, :last_seen, true, NOW(), NOW())");
        PreparedBatch updateBatch = handle.prepareBatch("UPDATE assets SET last_seen = :last_seen, " +
                "seen_udp = true, updated_at = NOW() WHERE id = :id");

        // We may have multiple sessions from the same client MAC. Pre-process and aggregate.
        Map<String, AssetInformation> assets = Maps.newHashMap();
        for (UdpConversationReport conversation : conversations) {
            if (conversation.sourceMac() == null) {
                continue;
            }

            AssetInformation existingAsset = assets.get(conversation.sourceMac());
            if (existingAsset != null) {
                // We have already seen this asset. Update timestamps.
                DateTime firstSeen;
                DateTime lastSeen;

                if (conversation.startTime().isBefore(existingAsset.firstSeen())) {
                    firstSeen = conversation.startTime();
                } else {
                    firstSeen = existingAsset.firstSeen();
                }

                if (conversation.mostRecentSegmentTime().isAfter(existingAsset.lastSeen())) {
                    lastSeen = conversation.mostRecentSegmentTime();
                } else {
                    lastSeen = existingAsset.lastSeen();
                }

                assets.put(conversation.sourceMac(), AssetInformation.create(conversation.sourceMac(), firstSeen, lastSeen));
            } else {
                // Not seen before.
                assets.put(conversation.sourceMac(), AssetInformation.create(
                        conversation.sourceMac(), conversation.startTime(), conversation.mostRecentSegmentTime()
                ));
            }
        }

        for (AssetInformation assetInfo : assets.values()) {
            try {
                Optional<AssetEntry> asset = tablesService.getNzyme().getAssetsManager()
                        .findAssetByMac(assetInfo.mac(), tap.organizationId(), tap.tenantId());

                if (asset.isPresent()) {
                    // We have an existing asset.
                    updateBatch
                            .bind("id", asset.get().id())
                            .bind("last_seen", assetInfo.lastSeen())
                            .add();
                } else {
                    // First time we are seeing this asset.
                    UUID uuid = UUID.randomUUID();
                    insertBatch
                            .bind("uuid", uuid)
                            .bind("organization_id", tap.organizationId())
                            .bind("tenant_id", tap.tenantId())
                            .bind("mac", assetInfo.mac())
                            .bind("first_seen", assetInfo.firstSeen())
                            .bind("last_seen", assetInfo.lastSeen())
                            .add();

                    // Handle new asset.
                    tablesService.getNzyme().getAssetsManager().onNewAsset(
                            Subsystem.ETHERNET,
                            uuid,
                            assetInfo.mac(),
                            tap.organizationId(),
                            tap.tenantId(),
                            tap.uuid()
                    );
                }
            } catch (Exception e) {
                LOG.error("Could not register asset from UDP conversations.", e);
            }
        }

        try {
            updateBatch.execute();
            insertBatch.execute();
        } catch(Exception e) {
            LOG.error("Could not write assets from UDP conversations.", e);
        }
    }

    @Override
    public void retentionClean() {
        // NOOP. Remove from plugin APIs if there remains no use. Database cleaned by category/tenant independently.
    }

}
