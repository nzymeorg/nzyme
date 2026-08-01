package app.nzyme.core.tables.ethernet;

import app.nzyme.core.rest.resources.taps.reports.tables.nat.NatTraversalReport;
import app.nzyme.core.rest.resources.taps.reports.tables.nat.StunDiscoveryReport;
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

public class NATTable implements DataTable  {

    private final TablesService tablesService;

    private final Timer totalReportTimer;
    private final Timer traversalDiscoveriesTimer;

    private final ObjectMapper om;

    public NATTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.NAT_TOTAL_REPORT_PROCESSING_TIMER);
        this.traversalDiscoveriesTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.NAT_DISCOVERIES_REPORT_PROCESSING_TIMER);
    }

    public void handleTraversalReport(UUID tapUuid, DateTime timestamp, NatTraversalReport report) {
        try(Timer.Context ignored1 = totalReportTimer.time()) {
            tablesService.getNzyme().getDatabase().useHandle(handle -> {
                try (Timer.Context ignored2 = traversalDiscoveriesTimer.time()) {
                    writeStunDiscoveries(handle, tapUuid, report.discoveries());
                }
            });
        }
    }

    public void writeStunDiscoveries(Handle handle, UUID tapUuid, List<StunDiscoveryReport> discoveries) {
        PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO nat_traversal_discoveries(uuid, " +
                "tap_uuid, l4_session_key, transport, mapped_addresses, most_recent_segment_time, first_seen, " +
                "updated_at, created_at) VALUES(:uuid, :tap_uuid, :l4_session_key, :transport, " +
                ":mapped_addresses::jsonb, :most_recent_segment_time, :first_seen, NOW(), NOW())");

        for (StunDiscoveryReport discovery : discoveries) {
            String sessionKey = Tools.buildL4Key(
                    discovery.firstSeen(),
                    discovery.sourceAddress(),
                    discovery.destinationAddress(),
                    discovery.sourcePort(),
                    discovery.destinationPort()
            );

            String mappedAddresses = om.writeValueAsString(discovery.mappedAddresses());

            insertBatch
                    .bind("uuid", UUID.randomUUID())
                    .bind("tap_uuid", tapUuid)
                    .bind("l4_session_key", sessionKey)
                    .bind("transport", discovery.transport())
                    .bind("mapped_addresses", mappedAddresses)
                    .bind("most_recent_segment_time", discovery.lastActivity())
                    .bind("first_seen", discovery.firstSeen())
                    .add();
        }

        insertBatch.execute();
    }

    @Override
    public void retentionClean() {
        // NOOP
    }
}
