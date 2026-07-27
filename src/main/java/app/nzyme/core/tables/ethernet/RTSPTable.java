package app.nzyme.core.tables.ethernet;

import app.nzyme.core.rest.resources.taps.reports.tables.rtsp.RtspSessionReport;
import app.nzyme.core.rest.resources.taps.reports.tables.rtsp.RtspSessionsReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.Tools;
import com.codahale.metrics.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import tools.jackson.databind.ObjectMapper;

import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RTSPTable implements DataTable  {

    private static final Logger LOG = LogManager.getLogger(RTSPTable.class);

    private final TablesService tablesService;
    private final ObjectMapper om;

    private final Timer totalReportTimer;

    public RTSPTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.RTSP_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, RtspSessionsReport report) {
        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            try(Timer.Context ignored = totalReportTimer.time()) {
                writeSessions(handle, tapUuid, report.sessions());
            }
        });
    }

    private void writeSessions(Handle handle, UUID tapUuid, List<RtspSessionReport> sessions) {
        PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO rtsp_sessions(uuid, tap_uuid, state, " +
                "media_locator, request_uri, client_agent, server_info, authentication, media_description, flags, " +
                "setup_established_at, setup_terminated_at, setup_most_recent_segment_time, setup_tcp_session_key, " +
                "stream_l4_untimed_session_key, setup_connection_status, updated_at, created_at) VALUES(:uuid, :tap_uuid, " +
                ":state, :media_locator::jsonb, :request_uri, :client_agent, :server_info, :authentication, " +
                ":media_description::jsonb, :flags, :setup_established_at, :setup_terminated_at, " +
                ":setup_most_recent_segment_time, :setup_tcp_session_key, :stream_l4_untimed_session_key, " +
                ":setup_connection_status, NOW(), NOW())");

        PreparedBatch updateBatch = handle.prepareBatch("UPDATE rtsp_sessions SET state = :state, " +
                "media_locator = :media_locator::jsonb, request_uri = :request_uri, client_agent = :client_agent, " +
                "server_info = :server_info, authentication = :authentication, " +
                "media_description = :media_description::jsonb, flags = :flags, " +
                "setup_terminated_at = :setup_terminated_at, setup_connection_status = :setup_connection_status, " +
                "setup_most_recent_segment_time = :setup_most_recent_segment_time, " +
                "stream_l4_untimed_session_key = :stream_l4_untimed_session_key, updated_at = NOW() WHERE id = :id");

        for (RtspSessionReport session : sessions) {
            String setupTcpSessionKey = Tools.buildL4Key(
                    session.setupEstablishedAt(),
                    session.setupSourceAddress(),
                    session.setupDestinationAddress(),
                    session.setupSourcePort(),
                    session.setupDestinationPort()
            );

            String streamL4UntimedSessionKey = null;
            if (session.mediaLocator() != null && session.mediaLocator().containsKey("type")) {
                String mediaLocatorType = (String) session.mediaLocator().get("type");
                switch (mediaLocatorType) {
                    case "Interleaved":
                        streamL4UntimedSessionKey = Tools.buildUntimedL4Key(
                                session.setupSourceAddress(),
                                session.setupDestinationAddress(),
                                session.setupSourcePort(),
                                session.setupDestinationPort()
                        );
                        break;
                    case "Udp":
                        streamL4UntimedSessionKey = Tools.buildUntimedL4Key(
                                session.setupSourceAddress(),
                                session.setupDestinationAddress(),
                                (int) session.mediaLocator().get("client_rtp_port"),
                                (int) session.mediaLocator().get("server_rtp_port")
                        );
                        break;
                }
            }

            String mediaLocatorJson = om.writeValueAsString(session.mediaLocator());
            String mediaDescriptionJson = om.writeValueAsString(session.mediaDescription());
            String[] flagsArr = session.flags().toArray(new String[0]);

            Optional<Long> existingSession = handle.createQuery("SELECT id FROM rtsp_sessions " +
                            "WHERE setup_tcp_session_key = :setup_tcp_session_key " +
                            "AND setup_established_at = :setup_established_at " +
                            "AND tap_uuid = :tap_uuid AND setup_connection_status = :setup_connection_status")
                    .bind("setup_tcp_session_key", setupTcpSessionKey)
                    .bind("setup_established_at", session.setupEstablishedAt())
                    .bind("tap_uuid", tapUuid)
                    .bind("setup_connection_status", "Active")
                    .mapTo(Long.class)
                    .findOne();

            if (existingSession.isEmpty()) {
                insertBatch
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tapUuid)
                        .bind("state", session.state())
                        .bind("media_locator", mediaLocatorJson)
                        .bind("request_uri", session.requestUri())
                        .bind("client_agent", session.clientAgent())
                        .bind("server_info", session.serverInfo())
                        .bind("authentication", session.authentication())
                        .bind("media_description", mediaDescriptionJson)
                        .bindBySqlType("flags", flagsArr, Types.ARRAY)
                        .bind("setup_established_at", session.setupEstablishedAt())
                        .bind("setup_terminated_at", session.setupTerminatedAt())
                        .bind("setup_most_recent_segment_time", session.setupMostRecentSegmentTime())
                        .bind("setup_tcp_session_key", setupTcpSessionKey)
                        .bind("stream_l4_untimed_session_key", streamL4UntimedSessionKey)
                        .bind("setup_connection_status", session.setupConnectionStatus())
                        .add();
            } else {
                // Update existing open RTSP session.
                updateBatch
                        .bind("id", existingSession)
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tapUuid)
                        .bind("state", session.state())
                        .bind("media_locator", mediaLocatorJson)
                        .bind("request_uri", session.requestUri())
                        .bind("client_agent", session.clientAgent())
                        .bind("server_info", session.serverInfo())
                        .bind("authentication", session.authentication())
                        .bind("media_description", mediaDescriptionJson)
                        .bindBySqlType("flags", flagsArr, Types.ARRAY)
                        .bind("setup_terminated_at", session.setupTerminatedAt())
                        .bind("setup_most_recent_segment_time", session.setupMostRecentSegmentTime())
                        .bind("setup_connection_status", session.setupConnectionStatus())
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
