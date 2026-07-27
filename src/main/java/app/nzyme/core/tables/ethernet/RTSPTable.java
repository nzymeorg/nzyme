package app.nzyme.core.tables.ethernet;

import app.nzyme.core.rest.resources.taps.reports.tables.rtsp.RtspStreamReport;
import app.nzyme.core.rest.resources.taps.reports.tables.rtsp.RtspStreamsReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.Tools;
import com.codahale.metrics.Timer;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import tools.jackson.databind.ObjectMapper;

import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RTSPTable implements DataTable  {

    private final TablesService tablesService;
    private final ObjectMapper om;

    private final Timer totalReportTimer;

    public RTSPTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.RTSP_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, RtspStreamsReport report) {
        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            try(Timer.Context ignored = totalReportTimer.time()) {
                writeStreams(handle, tapUuid, report.streams());
            }
        });
    }

    private void writeStreams(Handle handle, UUID tapUuid, List<RtspStreamReport> streams) {
        PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO rtsp_streams(uuid, tap_uuid, state, " +
                "media_locator, request_uri, client_agent, server_info, authentication, media_description, flags, " +
                "setup_established_at, setup_terminated_at, setup_most_recent_segment_time, setup_tcp_session_key, " +
                "stream_l4_untimed_session_key, setup_connection_status, updated_at, created_at) VALUES(:uuid, :tap_uuid, " +
                ":state, :media_locator::jsonb, :request_uri, :client_agent, :server_info, :authentication, " +
                ":media_description::jsonb, :flags, :setup_established_at, :setup_terminated_at, " +
                ":setup_most_recent_segment_time, :setup_tcp_session_key, :stream_l4_untimed_session_key, " +
                ":setup_connection_status, NOW(), NOW())");

        PreparedBatch updateBatch = handle.prepareBatch("UPDATE rtsp_streams SET state = :state, " +
                "media_locator = :media_locator::jsonb, request_uri = :request_uri, client_agent = :client_agent, " +
                "server_info = :server_info, authentication = :authentication, " +
                "media_description = :media_description::jsonb, flags = :flags, " +
                "setup_terminated_at = :setup_terminated_at, setup_connection_status = :setup_connection_status, " +
                "setup_most_recent_segment_time = :setup_most_recent_segment_time, " +
                "stream_l4_untimed_session_key = :stream_l4_untimed_session_key, updated_at = NOW() WHERE id = :id");

        for (RtspStreamReport stream : streams) {
            String setupTcpSessionKey = Tools.buildL4Key(
                    stream.setupEstablishedAt(),
                    stream.setupSourceAddress(),
                    stream.setupDestinationAddress(),
                    stream.setupSourcePort(),
                    stream.setupDestinationPort()
            );

            String streamL4UntimedSessionKey = null;
            if (stream.mediaLocator() != null && stream.mediaLocator().containsKey("type")) {
                String mediaLocatorType = (String) stream.mediaLocator().get("type");
                switch (mediaLocatorType) {
                    case "Interleaved":
                        streamL4UntimedSessionKey = Tools.buildUntimedL4Key(
                                stream.setupSourceAddress(),
                                stream.setupDestinationAddress(),
                                stream.setupSourcePort(),
                                stream.setupDestinationPort()
                        );
                        break;
                    case "Udp":
                        streamL4UntimedSessionKey = Tools.buildUntimedL4Key(
                                stream.setupSourceAddress(),
                                stream.setupDestinationAddress(),
                                (int) stream.mediaLocator().get("client_rtp_port"),
                                (int) stream.mediaLocator().get("server_rtp_port")
                        );
                        break;
                }
            }

            String mediaLocatorJson = om.writeValueAsString(stream.mediaLocator());
            String mediaDescriptionJson = om.writeValueAsString(stream.mediaDescription());
            String[] flagsArr = stream.flags().toArray(new String[0]);

            Optional<Long> existingSession = handle.createQuery("SELECT id FROM rtsp_streams " +
                            "WHERE setup_tcp_session_key = :setup_tcp_session_key " +
                            "AND setup_established_at = :setup_established_at " +
                            "AND tap_uuid = :tap_uuid AND setup_connection_status = :setup_connection_status")
                    .bind("setup_tcp_session_key", setupTcpSessionKey)
                    .bind("setup_established_at", stream.setupEstablishedAt())
                    .bind("tap_uuid", tapUuid)
                    .bind("setup_connection_status", "Active")
                    .mapTo(Long.class)
                    .findOne();

            if (existingSession.isEmpty()) {
                insertBatch
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tapUuid)
                        .bind("state", stream.state())
                        .bind("media_locator", mediaLocatorJson)
                        .bind("request_uri", stream.requestUri())
                        .bind("client_agent", stream.clientAgent())
                        .bind("server_info", stream.serverInfo())
                        .bind("authentication", stream.authentication())
                        .bind("media_description", mediaDescriptionJson)
                        .bindBySqlType("flags", flagsArr, Types.ARRAY)
                        .bind("setup_established_at", stream.setupEstablishedAt())
                        .bind("setup_terminated_at", stream.setupTerminatedAt())
                        .bind("setup_most_recent_segment_time", stream.setupMostRecentSegmentTime())
                        .bind("setup_tcp_session_key", setupTcpSessionKey)
                        .bind("stream_l4_untimed_session_key", streamL4UntimedSessionKey)
                        .bind("setup_connection_status", stream.setupConnectionStatus())
                        .add();
            } else {
                // Update existing open RTSP session.
                updateBatch
                        .bind("id", existingSession)
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tapUuid)
                        .bind("state", stream.state())
                        .bind("media_locator", mediaLocatorJson)
                        .bind("request_uri", stream.requestUri())
                        .bind("client_agent", stream.clientAgent())
                        .bind("server_info", stream.serverInfo())
                        .bind("authentication", stream.authentication())
                        .bind("media_description", mediaDescriptionJson)
                        .bindBySqlType("flags", flagsArr, Types.ARRAY)
                        .bind("setup_terminated_at", stream.setupTerminatedAt())
                        .bind("setup_most_recent_segment_time", stream.setupMostRecentSegmentTime())
                        .bind("setup_connection_status", stream.setupConnectionStatus())
                        .bind("stream_l4_untimed_session_key", streamL4UntimedSessionKey)
                        .add();
            }
        }

        insertBatch.execute();
        updateBatch.execute();
    }

    @Override
    public void retentionClean() {
        // NOOP
    }
}
