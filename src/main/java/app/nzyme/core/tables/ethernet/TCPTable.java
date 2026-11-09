package app.nzyme.core.tables.ethernet;

import app.nzyme.core.assets.profile.AssetProfileBatch;
import app.nzyme.core.assets.AssetInformation;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.ethernet.l4.tcp.TcpSignature;
import app.nzyme.core.rest.resources.taps.reports.tables.tcp.TcpSynAckReport;
import app.nzyme.core.ethernet.l4.tcp.TcpSessionState;
import app.nzyme.core.ethernet.l4.tcp.db.TcpSessionEntry;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.integrations.geoip.GeoIpService;
import app.nzyme.core.rest.resources.taps.reports.tables.tcp.TcpProbeReplyReport;
import app.nzyme.core.rest.resources.taps.reports.tables.tcp.TcpProbeReport;
import app.nzyme.core.rest.resources.taps.reports.tables.tcp.TcpProbesReport;
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
    private final Timer probesReportTimer;
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

        this.probesReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.TCP_PROBES_REPORT_PROCESSING_TIMER);

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

    public void handleProbesReport(UUID tapUuid, DateTime timestamp, TcpProbesReport report) {
        boolean hasProbes = report.probes() != null && !report.probes().isEmpty();
        boolean hasReplies = report.replies() != null && !report.replies().isEmpty();
        if (!hasProbes && !hasReplies) {
            return;
        }

        try (Timer.Context ignored = probesReportTimer.time()) {
            tablesService.getProcessorPool().submit(() -> {
                Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);
                if (tap.isEmpty()) {
                    throw new RuntimeException("Reporting tap [" + tapUuid + "] not found. Not processing report.");
                }

                tablesService.getNzyme().getDatabase().useHandle(handle -> {
                    if (hasProbes) {
                        writeProbes(handle, tap.get(), timestamp, report.probes());
                    }

                    if (hasReplies) {
                        writeProbeReplies(handle, tap.get(), report.replies());
                    }
                });
            });
        }
    }

    // A reply may answer a probe reported in an earlier cycle, so recent rows of the same triple are updated.
    private static final int PROBE_REPLY_MATCH_MINUTES = 10;

    private void writeProbeReplies(Handle handle, Tap tap, List<TcpProbeReplyReport> replies) {
        PreparedBatch batch = handle.prepareBatch("UPDATE tcp_probes SET reply_count = reply_count + :packet_count " +
                "WHERE tap_uuid = :tap_uuid AND source_address = :source_address::inet " +
                "AND destination_address = :destination_address::inet AND destination_port = :destination_port " +
                "AND last_seen >= :since");

        DateTime since = DateTime.now().minusMinutes(PROBE_REPLY_MATCH_MINUTES);
        for (TcpProbeReplyReport reply : replies) {
            batch.bind("tap_uuid", tap.uuid())
                    .bind("source_address", reply.sourceAddress())
                    .bind("destination_address", reply.destinationAddress())
                    .bind("destination_port", reply.destinationPort())
                    .bind("packet_count", reply.packetCount())
                    .bind("since", since)
                    .add();
        }

        try {
            batch.execute();
        } catch (Exception e) {
            LOG.error("Could not write TCP probe replies.", e);
        }
    }

    private void writeSessions(Handle handle, Tap tap, DateTime timestamp, List<TcpSessionReport> sessions) {
        PreparedBatch upsertBatch = handle.prepareBatch("INSERT INTO l4_sessions(tap_uuid, l4_type, " +
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
                "tcp_syn_ece, tcp_syn_options, fingerprint, signature, ip_hop_distance, ip_id, ip_id_behavior, " +
                "tcp_syn_timestamp_value, tcp_syn_timestamp_echo, tcp_client_timestamp_value, " +
                "tcp_client_timestamp_time, tcp_client_ip_id, tcp_timestamp_rate, segments_count_client_to_server, " +
                "server_fingerprint, server_signature, server_ip_hop_distance, tcp_synack_ip_ttl, tcp_synack_ip_tos, " +
                "tcp_synack_ip_df, tcp_synack_ip_id, tcp_synack_cwr, tcp_synack_ece, tcp_synack_window_size, " +
                "tcp_synack_maximum_segment_size, tcp_synack_window_scale_multiplier, tcp_synack_options, " +
                "tcp_synack_timestamp_value, tcp_synack_timestamp_echo, tags, created_at) " +
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
                ":tcp_syn_cwr, :tcp_syn_ece, :tcp_syn_options::jsonb, :fingerprint, :signature, :ip_hop_distance, " +
                ":ip_id, :ip_id_behavior, :tcp_syn_timestamp_value, :tcp_syn_timestamp_echo, " +
                ":tcp_client_timestamp_value, :tcp_client_timestamp_time, :tcp_client_ip_id, :tcp_timestamp_rate, " +
                ":segments_count_client_to_server, :server_fingerprint, :server_signature, :server_ip_hop_distance, " +
                ":tcp_synack_ip_ttl, :tcp_synack_ip_tos, :tcp_synack_ip_df, :tcp_synack_ip_id, :tcp_synack_cwr, " +
                ":tcp_synack_ece, :tcp_synack_window_size, :tcp_synack_maximum_segment_size, " +
                ":tcp_synack_window_scale_multiplier, :tcp_synack_options::jsonb, :tcp_synack_timestamp_value, " +
                ":tcp_synack_timestamp_echo, :tags::jsonb, :created_at) " +
                "ON CONFLICT (tap_uuid, l4_type, session_key) DO UPDATE SET " +
                "state = EXCLUDED.state, " +
                "ip_id_behavior = EXCLUDED.ip_id_behavior, " +
                "tcp_client_timestamp_value = EXCLUDED.tcp_client_timestamp_value, " +
                "tcp_client_timestamp_time = EXCLUDED.tcp_client_timestamp_time, " +
                "tcp_client_ip_id = EXCLUDED.tcp_client_ip_id, " +
                "tcp_timestamp_rate = EXCLUDED.tcp_timestamp_rate, " +
                "segments_count_client_to_server = EXCLUDED.segments_count_client_to_server, " +
                "server_fingerprint = COALESCE(EXCLUDED.server_fingerprint, l4_sessions.server_fingerprint), " +
                "server_signature = COALESCE(EXCLUDED.server_signature, l4_sessions.server_signature), " +
                "server_ip_hop_distance = COALESCE(EXCLUDED.server_ip_hop_distance, l4_sessions.server_ip_hop_distance), " +
                "tcp_synack_ip_ttl = COALESCE(EXCLUDED.tcp_synack_ip_ttl, l4_sessions.tcp_synack_ip_ttl), " +
                "tcp_synack_ip_tos = COALESCE(EXCLUDED.tcp_synack_ip_tos, l4_sessions.tcp_synack_ip_tos), " +
                "tcp_synack_ip_df = COALESCE(EXCLUDED.tcp_synack_ip_df, l4_sessions.tcp_synack_ip_df), " +
                "tcp_synack_ip_id = COALESCE(EXCLUDED.tcp_synack_ip_id, l4_sessions.tcp_synack_ip_id), " +
                "tcp_synack_cwr = COALESCE(EXCLUDED.tcp_synack_cwr, l4_sessions.tcp_synack_cwr), " +
                "tcp_synack_ece = COALESCE(EXCLUDED.tcp_synack_ece, l4_sessions.tcp_synack_ece), " +
                "tcp_synack_window_size = COALESCE(EXCLUDED.tcp_synack_window_size, l4_sessions.tcp_synack_window_size), " +
                "tcp_synack_maximum_segment_size = COALESCE(EXCLUDED.tcp_synack_maximum_segment_size, l4_sessions.tcp_synack_maximum_segment_size), " +
                "tcp_synack_window_scale_multiplier = COALESCE(EXCLUDED.tcp_synack_window_scale_multiplier, l4_sessions.tcp_synack_window_scale_multiplier), " +
                "tcp_synack_options = COALESCE(EXCLUDED.tcp_synack_options, l4_sessions.tcp_synack_options), " +
                "tcp_synack_timestamp_value = COALESCE(EXCLUDED.tcp_synack_timestamp_value, l4_sessions.tcp_synack_timestamp_value), " +
                "tcp_synack_timestamp_echo = COALESCE(EXCLUDED.tcp_synack_timestamp_echo, l4_sessions.tcp_synack_timestamp_echo), " +
                "bytes_rx_count = EXCLUDED.bytes_rx_count, " +
                "bytes_tx_count = EXCLUDED.bytes_tx_count, " +
                "segments_count = EXCLUDED.segments_count, " +
                "tags = EXCLUDED.tags, " +
                "end_time = COALESCE(EXCLUDED.end_time, l4_sessions.end_time), " +
                "most_recent_segment_time = EXCLUDED.most_recent_segment_time " +
                "WHERE (l4_sessions.most_recent_segment_time IS NULL " +
                "OR l4_sessions.most_recent_segment_time <= EXCLUDED.most_recent_segment_time) " +
                // Skip the write when the report carries nothing new.
                "AND (l4_sessions.state, l4_sessions.ip_id_behavior, l4_sessions.tcp_client_timestamp_value, " +
                "l4_sessions.tcp_client_timestamp_time, l4_sessions.tcp_client_ip_id, " +
                "l4_sessions.tcp_timestamp_rate, l4_sessions.segments_count_client_to_server, " +
                "l4_sessions.server_fingerprint, l4_sessions.server_signature, " +
                "l4_sessions.server_ip_hop_distance, l4_sessions.tcp_synack_ip_ttl, " +
                "l4_sessions.tcp_synack_ip_tos, l4_sessions.tcp_synack_ip_df, l4_sessions.tcp_synack_ip_id, " +
                "l4_sessions.tcp_synack_cwr, l4_sessions.tcp_synack_ece, l4_sessions.tcp_synack_window_size, " +
                "l4_sessions.tcp_synack_maximum_segment_size, l4_sessions.tcp_synack_window_scale_multiplier, " +
                "l4_sessions.tcp_synack_options, l4_sessions.tcp_synack_timestamp_value, " +
                "l4_sessions.tcp_synack_timestamp_echo, l4_sessions.bytes_rx_count, l4_sessions.bytes_tx_count, " +
                "l4_sessions.segments_count, l4_sessions.tags, l4_sessions.end_time, " +
                "l4_sessions.most_recent_segment_time) IS DISTINCT FROM (EXCLUDED.state, EXCLUDED.ip_id_behavior, " +
                "EXCLUDED.tcp_client_timestamp_value, EXCLUDED.tcp_client_timestamp_time, " +
                "EXCLUDED.tcp_client_ip_id, EXCLUDED.tcp_timestamp_rate, " +
                "EXCLUDED.segments_count_client_to_server, COALESCE(EXCLUDED.server_fingerprint, " +
                "l4_sessions.server_fingerprint), COALESCE(EXCLUDED.server_signature, " +
                "l4_sessions.server_signature), COALESCE(EXCLUDED.server_ip_hop_distance, " +
                "l4_sessions.server_ip_hop_distance), COALESCE(EXCLUDED.tcp_synack_ip_ttl, " +
                "l4_sessions.tcp_synack_ip_ttl), COALESCE(EXCLUDED.tcp_synack_ip_tos, " +
                "l4_sessions.tcp_synack_ip_tos), COALESCE(EXCLUDED.tcp_synack_ip_df, " +
                "l4_sessions.tcp_synack_ip_df), COALESCE(EXCLUDED.tcp_synack_ip_id, " +
                "l4_sessions.tcp_synack_ip_id), COALESCE(EXCLUDED.tcp_synack_cwr, l4_sessions.tcp_synack_cwr), " +
                "COALESCE(EXCLUDED.tcp_synack_ece, l4_sessions.tcp_synack_ece), " +
                "COALESCE(EXCLUDED.tcp_synack_window_size, l4_sessions.tcp_synack_window_size), " +
                "COALESCE(EXCLUDED.tcp_synack_maximum_segment_size, l4_sessions.tcp_synack_maximum_segment_size), " +
                "COALESCE(EXCLUDED.tcp_synack_window_scale_multiplier, " +
                "l4_sessions.tcp_synack_window_scale_multiplier), COALESCE(EXCLUDED.tcp_synack_options, " +
                "l4_sessions.tcp_synack_options), COALESCE(EXCLUDED.tcp_synack_timestamp_value, " +
                "l4_sessions.tcp_synack_timestamp_value), COALESCE(EXCLUDED.tcp_synack_timestamp_echo, " +
                "l4_sessions.tcp_synack_timestamp_echo), EXCLUDED.bytes_rx_count, EXCLUDED.bytes_tx_count, " +
                "EXCLUDED.segments_count, EXCLUDED.tags, COALESCE(EXCLUDED.end_time, l4_sessions.end_time), " +
                "EXCLUDED.most_recent_segment_time)");

        DateTime activityCutoff = DateTime.now().minusMinutes(1);
        Map<String, TcpSessionReport> deduplicated = new LinkedHashMap<>();
        for (TcpSessionReport session : sessions) {
            if (session.mostRecentSegmentTime() == null || !session.mostRecentSegmentTime().isAfter(activityCutoff)) {
                continue;
            }

            try {
                String sessionKey = Tools.buildL4Key(
                        session.startTime(),
                        session.sourceAddress(),
                        session.destinationAddress(),
                        session.sourcePort(),
                        session.destinationPort()
                );

                deduplicated.merge(sessionKey, session, (existing, incoming) ->
                        incoming.mostRecentSegmentTime().isAfter(existing.mostRecentSegmentTime())
                                ? incoming : existing);
            } catch (Exception e) {
                LOG.error("Could not build session key for TCP session.", e);
            }
        }

        long totalRxBytes = 0;
        long totalTxBytes = 0;
        long totalRxInternalBytes = 0;
        long totalTxInternalBytes = 0;
        long totalSegments = 0;
        long totalSessions = 0;
        long totalInternalSessions = 0;

        AssetProfileBatch profile = new AssetProfileBatch(tap);

        for (Map.Entry<String, TcpSessionReport> entry : deduplicated.entrySet()) {
            String sessionKey = entry.getKey();
            TcpSessionReport session = entry.getValue();

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

            try {
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

                String synOptions;
                try {
                    synOptions = om.writeValueAsString(session.synOptions());
                } catch (JacksonException e) {
                    throw new RuntimeException("Could not serialize SYN options: " + session.synOptions(), e);
                }

                // Older taps send no IP ID. -1 keeps the zero-ID quirk off in that case.
                int synIpId = session.synIpId() == null ? -1 : session.synIpId();
                TcpSignature client = new TcpSignature(
                        session.synIpTtl(),
                        session.synIpTos(),
                        session.synIpDf(),
                        synIpId,
                        session.synCwr(),
                        session.synEce(),
                        session.synWindowSize(),
                        session.synMaximumSegmentSize(),
                        session.synMaximumScaleMultiplier(),
                        session.synOptions(),
                        Boolean.TRUE.equals(session.synOptionsTruncated()),
                        session.synTimestampValue(),
                        session.synTimestampEcho()
                );

                TcpSynAckReport synAck = session.synAck();
                TcpSignature server = null;
                String synAckOptions = null;
                if (synAck != null) {
                    server = new TcpSignature(
                            synAck.ipTtl(),
                            synAck.ipTos(),
                            synAck.ipDf(),
                            synAck.ipId(),
                            synAck.cwr(),
                            synAck.ece(),
                            synAck.windowSize(),
                            synAck.maximumSegmentSize(),
                            synAck.windowScaleMultiplier(),
                            synAck.options(),
                            Boolean.TRUE.equals(synAck.optionsTruncated()),
                            synAck.timestampValue(),
                            synAck.timestampEcho()
                    );

                    try {
                        synAckOptions = om.writeValueAsString(synAck.options());
                    } catch (JacksonException e) {
                        throw new RuntimeException("Could not serialize SYN-ACK options: " + synAck.options(), e);
                    }
                }

                Integer timestampRate = TcpSignature.timestampRate(
                        session.synTimestampValue(),
                        session.startTime(),
                        session.latestClientTimestampValue(),
                        session.latestClientTimestampTime()
                );
                String ipIdBehavior = session.synIpId() == null ? null : TcpSignature.ipIdBehavior(
                        session.synIpId(),
                        session.latestClientIpId(),
                        session.segmentsCountClientToServer() == null ? 0 : session.segmentsCountClientToServer()
                );

                AssetProfileBatch.TcpIdentity identity = new AssetProfileBatch.TcpIdentity(
                        client,
                        session.sourcePort(),
                        TcpSignature.timestampRateBucket(timestampRate),
                        ipIdBehavior,
                        server
                );

                upsertBatch
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
                        .bind("fingerprint", client.fingerprint())
                        .bind("signature", client.signature())
                        .bind("ip_hop_distance", client.hopDistance())
                        .bind("ip_id", session.synIpId())
                        .bind("ip_id_behavior", ipIdBehavior)
                        .bind("tcp_syn_timestamp_value", session.synTimestampValue())
                        .bind("tcp_syn_timestamp_echo", session.synTimestampEcho())
                        .bind("tcp_client_timestamp_value", session.latestClientTimestampValue())
                        .bind("tcp_client_timestamp_time", session.latestClientTimestampTime())
                        .bind("tcp_client_ip_id", session.latestClientIpId())
                        .bind("tcp_timestamp_rate", timestampRate)
                        .bind("segments_count_client_to_server", session.segmentsCountClientToServer())
                        .bind("server_fingerprint", server == null ? null : server.fingerprint())
                        .bind("server_signature", server == null ? null : server.signature())
                        .bind("server_ip_hop_distance", server == null ? null : server.hopDistance())
                        .bind("tcp_synack_ip_ttl", synAck == null ? null : synAck.ipTtl())
                        .bind("tcp_synack_ip_tos", synAck == null ? null : synAck.ipTos())
                        .bind("tcp_synack_ip_df", synAck == null ? null : synAck.ipDf())
                        .bind("tcp_synack_ip_id", synAck == null ? null : synAck.ipId())
                        .bind("tcp_synack_cwr", synAck == null ? null : synAck.cwr())
                        .bind("tcp_synack_ece", synAck == null ? null : synAck.ece())
                        .bind("tcp_synack_window_size", synAck == null ? null : synAck.windowSize())
                        .bind("tcp_synack_maximum_segment_size", synAck == null ? null : synAck.maximumSegmentSize())
                        .bind("tcp_synack_window_scale_multiplier", synAck == null ? null : synAck.windowScaleMultiplier())
                        .bind("tcp_synack_options", synAckOptions)
                        .bind("tcp_synack_timestamp_value", synAck == null ? null : synAck.timestampValue())
                        .bind("tcp_synack_timestamp_echo", synAck == null ? null : synAck.timestampEcho())
                        .bind("tags", tags)
                        .bind("created_at", timestamp)
                        .add();

                totalRxBytes += session.bytesCountRxIncremental();
                totalTxBytes += session.bytesCountTxIncremental();
                totalSegments += session.segmentsCountIncremental();
                totalSessions += 1;

                if (sourceAddress.isSiteLocalAddress() && destinationAddress.isSiteLocalAddress()) {
                    totalInternalSessions += 1;
                    totalRxInternalBytes += session.bytesCountRxIncremental();
                    totalTxInternalBytes += session.bytesCountTxIncremental();
                }

                // A session is new when every segment it has is from this report cycle.
                profile.l4Session(
                        AssetProfileBatch.L4Type.TCP,
                        session.sourceMac(),
                        session.destinationMac(),
                        sourceAddress,
                        destinationAddress,
                        session.destinationPort(),
                        session.tags(),
                        session.segmentsCount() == session.segmentsCountIncremental(),
                        session.bytesCountRxIncremental(),
                        session.bytesCountTxIncremental(),
                        session.startTime(),
                        session.mostRecentSegmentTime(),
                        sourceGeo,
                        destinationGeo,
                        identity
                );
            } catch (Exception e) {
                LOG.error("Could not handle TCP session.", e);
            }
        }

        try {
            upsertBatch.execute();
        } catch (Exception e) {
            LOG.error("Could not write TCP sessions.", e);
        }

        profile.flush(handle);

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
        } catch (Exception e) {
            LOG.error("Could not write TCP statistics.", e);
        }
    }

    private void writeProbes(Handle handle, Tap tap, DateTime timestamp, List<TcpProbeReport> probes) {
        PreparedBatch batch = handle.prepareBatch("INSERT INTO tcp_probes(tap_uuid, source_mac, source_address, " +
                "source_address_is_site_local, destination_mac, destination_address, " +
                "destination_address_is_site_local, destination_port, flag_syn, flag_ack, flag_fin, flag_rst, " +
                "flag_psh, flag_urg, flag_ece, flag_cwr, window_size, maximum_segment_size, " +
                "window_scale_multiplier, options, ip_ttl, ip_tos, ip_df, packet_count, first_seen, last_seen, " +
                "created_at) VALUES(:tap_uuid, :source_mac, :source_address::inet, :source_address_is_site_local, " +
                ":destination_mac, :destination_address::inet, :destination_address_is_site_local, " +
                ":destination_port, :flag_syn, :flag_ack, :flag_fin, :flag_rst, :flag_psh, :flag_urg, :flag_ece, " +
                ":flag_cwr, :window_size, :maximum_segment_size, :window_scale_multiplier, :options::jsonb, " +
                ":ip_ttl, :ip_tos, :ip_df, :packet_count, :first_seen, :last_seen, :created_at)");

        int added = 0;
        for (TcpProbeReport probe : probes) {
            try {
                InetAddress sourceAddress = stringtoInetAddress(probe.sourceAddress());
                InetAddress destinationAddress = stringtoInetAddress(probe.destinationAddress());

                String options;
                try {
                    options = om.writeValueAsString(probe.options() == null ? List.of() : probe.options());
                } catch (JacksonException e) {
                    throw new RuntimeException("Could not serialize probe options: " + probe.options(), e);
                }

                batch.bind("tap_uuid", tap.uuid())
                        .bind("source_mac", probe.sourceMac())
                        .bind("source_address", probe.sourceAddress())
                        .bind("source_address_is_site_local", sourceAddress.isSiteLocalAddress())
                        .bind("destination_mac", probe.destinationMac())
                        .bind("destination_address", probe.destinationAddress())
                        .bind("destination_address_is_site_local", destinationAddress.isSiteLocalAddress())
                        .bind("destination_port", probe.destinationPort())
                        .bind("flag_syn", probe.flagSyn())
                        .bind("flag_ack", probe.flagAck())
                        .bind("flag_fin", probe.flagFin())
                        .bind("flag_rst", probe.flagRst())
                        .bind("flag_psh", probe.flagPsh())
                        .bind("flag_urg", probe.flagUrg())
                        .bind("flag_ece", probe.flagEce())
                        .bind("flag_cwr", probe.flagCwr())
                        .bind("window_size", probe.windowSize())
                        .bind("maximum_segment_size", probe.maximumSegmentSize())
                        .bind("window_scale_multiplier", probe.windowScaleMultiplier())
                        .bind("options", options)
                        .bind("ip_ttl", probe.ipTtl())
                        .bind("ip_tos", probe.ipTos())
                        .bind("ip_df", probe.ipDf())
                        .bind("packet_count", probe.packetCount())
                        .bind("first_seen", probe.firstSeen())
                        .bind("last_seen", probe.lastSeen())
                        .bind("created_at", timestamp)
                        .add();
                added++;
            } catch (Exception e) {
                LOG.error("Could not handle TCP probe.", e);
            }
        }

        if (added == 0) {
            return;
        }

        try {
            batch.execute();
        } catch (Exception e) {
            LOG.error("Could not write TCP probes.", e);
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
            if (session.sourceMac() == null || session.sourceMac().equals("00:00:00:00:00:00")) {
                continue;
            }

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
