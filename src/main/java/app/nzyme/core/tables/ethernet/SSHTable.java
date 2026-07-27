package app.nzyme.core.tables.ethernet;

import app.nzyme.core.rest.resources.taps.reports.tables.ssh.SshSessionReport;
import app.nzyme.core.rest.resources.taps.reports.tables.ssh.SshSessionsReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.Tools;
import com.codahale.metrics.Timer;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SSHTable implements DataTable {

    private final TablesService tablesService;

    private final Timer totalReportTimer;

    public SSHTable(TablesService tablesService) {
        this.tablesService = tablesService;

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.SSH_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, SshSessionsReport report) {
        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            try(Timer.Context ignored = totalReportTimer.time()) {
                writeSessions(handle, tapUuid, report.sessions());
            }
        });
    }

    private void writeSessions(Handle handle, UUID tapUuid, List<SshSessionReport> sessions) {
        PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO ssh_sessions(uuid, tap_uuid, " +
                "tcp_session_key, client_version_version, client_version_software, client_version_comments, " +
                "server_version_version, server_version_software, server_version_comments, connection_status, " +
                "tunneled_bytes, established_at, terminated_at, most_recent_segment_time, updated_at, created_at) " +
                "VALUES(:uuid, :tap_uuid, :tcp_session_key, :client_version_version, :client_version_software, " +
                ":client_version_comments, :server_version_version, :server_version_software, " +
                ":server_version_comments, :connection_status, :tunneled_bytes, :established_at, :terminated_at, " +
                ":most_recent_segment_time, NOW(), NOW())");

        PreparedBatch updateBatch = handle.prepareBatch("UPDATE ssh_sessions " +
                "SET connection_status = :connection_status, tunneled_bytes = :tunneled_bytes, " +
                "terminated_at = :terminated_at, most_recent_segment_time = :most_recent_segment_time, " +
                "updated_at = NOW() WHERE id = :id");

        for (SshSessionReport session : sessions) {
            String tcpSessionKey = Tools.buildL4Key(
                    session.establishedAt(),
                    session.sourceAddress(),
                    session.destinationAddress(),
                    session.sourcePort(),
                    session.destinationPort());

            Optional<Long> existingSession = handle.createQuery("SELECT id FROM ssh_sessions " +
                            "WHERE tcp_session_key = :tcp_session_key AND established_at = :established_at " +
                            "AND tap_uuid = :tap_uuid AND connection_status = :connection_status")
                    .bind("tcp_session_key", tcpSessionKey)
                    .bind("established_at", session.establishedAt())
                    .bind("tap_uuid", tapUuid)
                    .bind("connection_status", "Active")
                    .mapTo(Long.class)
                    .findOne();

            if (existingSession.isEmpty()) {
                insertBatch
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tapUuid)
                        .bind("tcp_session_key", tcpSessionKey)
                        .bind("client_version_version", session.clientVersion().version())
                        .bind("client_version_software", session.clientVersion().software())
                        .bind("client_version_comments", session.clientVersion().comments())
                        .bind("server_version_version", session.serverVersion().version())
                        .bind("server_version_software", session.serverVersion().software())
                        .bind("server_version_comments", session.serverVersion().comments())
                        .bind("connection_status", session.connectionStatus())
                        .bind("tunneled_bytes", session.tunneledBytes())
                        .bind("established_at", session.establishedAt())
                        .bind("terminated_at", session.terminatedAt())
                        .bind("most_recent_segment_time", session.mostRecentSegmentTime())
                        .add();
            } else {
                // Update existing open SSH session.
                updateBatch
                        .bind("connection_status", session.connectionStatus())
                        .bind("tunneled_bytes", session.tunneledBytes())
                        .bind("terminated_at", session.terminatedAt())
                        .bind("most_recent_segment_time", session.mostRecentSegmentTime())
                        .bind("id", existingSession)
                        .add();
            }
        }

        insertBatch.execute();
        updateBatch.execute();
    }

    @Override
    public void retentionClean() {
        // NOOP. Remove from plugin APIs if there remains no use. Database cleaned by category/tenant independently.
    }

}
