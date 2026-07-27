package app.nzyme.core.tables.ethernet;

import app.nzyme.core.assets.AssetInformation;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.ethernet.l4.tcp.TCPFingerprint;
import app.nzyme.core.ethernet.l4.tcp.TcpSessionState;
import app.nzyme.core.ethernet.l4.tcp.db.TcpSessionEntry;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.integrations.geoip.GeoIpService;
import app.nzyme.core.rest.resources.taps.reports.tables.tcp.TcpSessionReport;
import app.nzyme.core.rest.resources.taps.reports.tables.tcp.TcpSessionsReport;
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
import java.util.*;

import static app.nzyme.core.util.Tools.stringtoInetAddress;

public class TCPTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(TCPTable.class);

    private final TablesService tablesService;

    private final ObjectMapper om;

    private final Timer totalReportTimer;
    private final Timer sessionsReportTimer;
    private final Timer sessionDiscoveryTimer;
    private final Timer assetRegistrationTimer;

    private final GeoIpService geoIp;

    public TCPTable(TablesService tablesService) {
        this.tablesService = tablesService;

        this.geoIp = tablesService.getNzyme().getGeoIpService();

        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.TCP_TOTAL_REPORT_PROCESSING_TIMER);

        this.sessionsReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.TCP_SESSIONS_REPORT_PROCESSING_TIMER);

        this.sessionDiscoveryTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.TCP_SESSION_DISCOVERY_QUERY_TIMER);

        this.assetRegistrationTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.TCP_ASSET_REGISTRATION_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, TcpSessionsReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            tablesService.getProcessorPool().submit(() -> {
                Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);
                if (tap.isEmpty()) {
                    throw new RuntimeException("Reporting tap [" + tapUuid + "] not found. Not processing report.");
                }

                tablesService.getNzyme().getDatabase().useHandle(handle -> {
                    try (Timer.Context ignored2 = sessionsReportTimer.time()) {
                        writeSessions(handle, tap.get(), timestamp, report.sessions());
                    }

                    try (Timer.Context ignored2 = assetRegistrationTimer.time()) {
                        registerAssets(handle, tap.get(), timestamp, report.sessions());
                    }
                });
            });
        }
    }

    private void writeSessions(Handle handle, Tap tap, DateTime timestamp, List<TcpSessionReport> sessions) {
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
                "destination_address_geo_latitude, destination_address_geo_longitude, ip_ttl, ip_tos, ip_df, " +
                "tcp_syn_window_size, tcp_syn_maximum_segment_size, tcp_syn_window_scale_multiplier, tcp_syn_cwr, " +
                "tcp_syn_ece, tcp_syn_options, fingerprint, tags, created_at) " +
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
                ":destination_address_geo_latitude, :destination_address_geo_longitude, :ip_ttl, :ip_tos, :ip_df, " +
                ":tcp_syn_window_size, :tcp_syn_maximum_segment_size, :tcp_syn_window_scale_multiplier, " +
                ":tcp_syn_cwr, :tcp_syn_ece, :tcp_syn_options::jsonb, :fingerprint, :tags::jsonb, :created_at)");
        PreparedBatch updateBatch = handle.prepareBatch("UPDATE l4_sessions SET state = :state, " +
                "bytes_rx_count = :bytes_rx_count, bytes_tx_count = :bytes_tx_count, " +
                "segments_count = :segments_count, tags = tags::jsonb, end_time = :end_time, " +
                "most_recent_segment_time = :most_recent_segment_time WHERE id = :id");

        long totalRxBytes = 0;
        long totalTxBytes = 0;
        long totalRxInternalBytes = 0;
        long totalTxInternalBytes = 0;
        long totalSegments = 0;
        long totalSessions = 0;
        long totalInternalSessions = 0;

        for (TcpSessionReport session : sessions) {

            String tags;
            if (session.tags() != null && !session.tags().isEmpty()) {
                try {
                    tags = om.writeValueAsString(session.tags());
                } catch (JacksonException e) {
                     LOG.error("Could not serialize session tags.", e);
                     tags = null;
                }
            } else {
                tags = null;
            }

            if (session.mostRecentSegmentTime().isAfter(DateTime.now().minusMinutes(1))) {
                try {
                    String sessionKey = Tools.buildL4Key(
                            session.startTime(),
                            session.sourceAddress(),
                            session.destinationAddress(),
                            session.sourcePort(),
                            session.destinationPort()
                    );

                    String untimedSessionKey = Tools.buildUntimedL4Key(
                            session.sourceAddress(),
                            session.destinationAddress(),
                            session.sourcePort(),
                            session.destinationPort()
                    );

                    InetAddress sourceAddress = stringtoInetAddress(session.sourceAddress());
                    InetAddress destinationAddress = stringtoInetAddress(session.destinationAddress());
                    Optional<GeoIpLookupResult> sourceGeo = geoIp.lookup(sourceAddress);
                    Optional<GeoIpLookupResult> destinationGeo = geoIp.lookup(destinationAddress);

                    Optional<TcpSessionEntry> existingSession;
                    try (Timer.Context ignored = sessionDiscoveryTimer.time()) {
                        existingSession = handle.createQuery("SELECT * FROM l4_sessions " +
                                        "WHERE l4_type = 'TCP' AND session_key = :session_key AND end_time IS NULL " +
                                        "AND tap_uuid = :tap_uuid")
                                .bind("session_key", sessionKey)
                                .bind("tap_uuid", tap.uuid())
                                .mapTo(TcpSessionEntry.class)
                                .findOne();
                    }

                    if (existingSession.isPresent()) {
                        // Existing session. Update.
                        updateBatch
                                .bind("state", TcpSessionState.valueOf(session.state().toUpperCase()))
                                .bind("bytes_rx_count", session.bytesCountRx())
                                .bind("bytes_tx_count", session.bytesCountTx())
                                .bind("segments_count", session.segmentsCount())
                                .bind("end_time", session.endTime())
                                .bind("most_recent_segment_time", session.mostRecentSegmentTime())
                                .bind("tags", tags)
                                .bind("id", existingSession.get().id())
                                .add();
                    } else {
                        String synOptions;
                        try {
                            synOptions = om.writeValueAsString(session.synOptions());
                        } catch (JacksonException e) {
                            throw new RuntimeException("Could not serialize SYN options: " + session.synOptions(), e);
                        }

                        String fingerprint = new TCPFingerprint(
                                session.synIpTtl(),
                                session.synIpTos(),
                                session.synIpDf(),
                                session.synWindowSize(),
                                session.synMaximumSegmentSize(),
                                session.synMaximumScaleMultiplier(),
                                session.synOptions()
                        ).generate();

                        // This is a new session.
                        insertBatch
                                .bind("tap_uuid", tap.uuid())
                                .bind("l4_type", "TCP")
                                .bind("session_key", sessionKey)
                                .bind("untimed_session_key", untimedSessionKey)
                                .bind("source_mac", session.sourceMac())
                                .bind("source_address", session.sourceAddress())
                                .bind("source_address_is_site_local", sourceAddress.isSiteLocalAddress())
                                .bind("source_address_is_loopback", sourceAddress.isLoopbackAddress())
                                .bind("source_address_is_multicast", sourceAddress.isMulticastAddress())
                                .bind("source_port", session.sourcePort())
                                .bind("destination_mac", session.destinationMac())
                                .bind("destination_address", session.destinationAddress())
                                .bind("destination_address_is_site_local", destinationAddress.isSiteLocalAddress())
                                .bind("destination_address_is_loopback", destinationAddress.isLoopbackAddress())
                                .bind("destination_address_is_multicast", destinationAddress.isMulticastAddress())
                                .bind("destination_port", session.destinationPort())
                                .bind("bytes_rx_count", session.bytesCountRx())
                                .bind("bytes_tx_count", session.bytesCountTx())
                                .bind("segments_count", session.segmentsCount())
                                .bind("start_time", session.startTime())
                                .bind("end_time", session.endTime())
                                .bind("most_recent_segment_time", session.mostRecentSegmentTime())
                                .bind("state", TcpSessionState.valueOf(session.state().toUpperCase()))
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
                                .bind("ip_ttl", session.synIpTtl())
                                .bind("ip_tos", session.synIpTos())
                                .bind("ip_df", session.synIpDf())
                                .bind("tcp_syn_window_size", session.synWindowSize())
                                .bind("tcp_syn_maximum_segment_size", session.synMaximumSegmentSize())
                                .bind("tcp_syn_window_scale_multiplier", session.synMaximumScaleMultiplier())
                                .bind("tcp_syn_cwr", session.synCwr())
                                .bind("tcp_syn_ece", session.synEce())
                                .bind("tcp_syn_options", synOptions)
                                .bind("fingerprint", fingerprint)
                                .bind("created_at", timestamp)
                                .bind("tags", tags)
                                .add();
                    }

                    totalRxBytes += session.bytesCountRxIncremental();
                    totalTxBytes += session.bytesCountTxIncremental();
                    totalSegments += session.segmentsCountIncremental();
                    totalSessions += 1;

                    if (sourceAddress.isSiteLocalAddress() && destinationAddress.isSiteLocalAddress()) {
                        totalInternalSessions += 1;
                        totalRxInternalBytes += session.bytesCountRxIncremental();
                        totalTxInternalBytes += session.bytesCountTxIncremental();
                    }
                } catch (Exception e) {
                    LOG.error("Could not handle TCP session.", e);
                }
            }
        }

        try {
            // Aggregated statistics.
            handle.createUpdate("INSERT INTO l4_statistics(tap_uuid, bytes_rx_tcp, bytes_tx_tcp, " +
                            "bytes_rx_internal_tcp, bytes_tx_internal_tcp, segments_tcp, " +
                            "sessions_tcp, sessions_internal_tcp, timestamp, created_at) VALUES(:tap_uuid, " +
                            ":bytes_rx_tcp, :bytes_tx_tcp, :bytes_rx_internal_tcp, :bytes_tx_internal_tcp, " +
                            ":segments_tcp, :sessions_tcp, :sessions_internal_tcp, " +
                            ":timestamp, NOW())")
                    .bind("tap_uuid", tap.uuid())
                    .bind("bytes_rx_tcp", totalRxBytes)
                    .bind("bytes_tx_tcp", totalTxBytes)
                    .bind("bytes_rx_internal_tcp", totalRxInternalBytes)
                    .bind("bytes_tx_internal_tcp", totalTxInternalBytes)
                    .bind("segments_tcp", totalSegments)
                    .bind("sessions_tcp", totalSessions)
                    .bind("sessions_internal_tcp", totalInternalSessions)
                    .bind("timestamp", timestamp)
                    .execute();

            updateBatch.execute();
            insertBatch.execute();
        } catch (Exception e) {
            LOG.error("Could not write TCP sessions.", e);
        }
    }

    private void registerAssets(Handle handle, Tap tap, DateTime timestamp, List<TcpSessionReport> sessions) {
        PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO assets(uuid, organization_id, tenant_id, " +
                "mac, first_seen, last_seen, seen_tcp, updated_at, created_at) VALUES(:uuid, " +
                ":organization_id, :tenant_id, :mac, :first_seen, :last_seen, true, NOW(), NOW())");
        PreparedBatch updateBatch = handle.prepareBatch("UPDATE assets SET last_seen = :last_seen, " +
                "seen_tcp = true, updated_at = NOW() WHERE id = :id");

        // We may have multiple sessions from the same client MAC. Pre-process and aggregate.
        Map<String, AssetInformation> assets = Maps.newHashMap();
        for (TcpSessionReport session : sessions) {
            if (session.sourceMac() == null) {
                continue;
            }

            String fingerprint = new TCPFingerprint(
                    session.synIpTtl(),
                    session.synIpTos(),
                    session.synIpDf(),
                    session.synWindowSize(),
                    session.synMaximumSegmentSize(),
                    session.synMaximumScaleMultiplier(),
                    session.synOptions()
            ).generate();

            AssetInformation existingAsset = assets.get(session.sourceMac());
            if (existingAsset != null) {
                // We have already seen this asset. Update timestamps.
                DateTime firstSeen;
                DateTime lastSeen;

                if (session.startTime().isBefore(existingAsset.firstSeen())) {
                    firstSeen = session.startTime();
                } else {
                    firstSeen = existingAsset.firstSeen();
                }

                if (session.mostRecentSegmentTime().isAfter(existingAsset.lastSeen())) {
                    lastSeen = session.mostRecentSegmentTime();
                } else {
                    lastSeen = existingAsset.lastSeen();
                }

                assets.put(session.sourceMac(), AssetInformation.create(session.sourceMac(), firstSeen, lastSeen));
            } else {
                // Not seen before.
                assets.put(session.sourceMac(), AssetInformation.create(
                        session.sourceMac(), session.startTime(), session.mostRecentSegmentTime()
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
                LOG.error("Could not register asset from TCP session.", e);
            }
        }

        try {
            updateBatch.execute();
            insertBatch.execute();
        } catch(Exception e) {
            LOG.error("Could not write assets from TCP sessions.", e);
        }
    }

    @Override
    public void retentionClean() {
        // NOOP. Remove from plugin APIs if there remains no use. Database cleaned by category/tenant independently.
    }

}
