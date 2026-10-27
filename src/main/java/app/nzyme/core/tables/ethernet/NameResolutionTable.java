package app.nzyme.core.tables.ethernet;

import app.nzyme.core.ethernet.spoofing.NameResolutionJson;
import app.nzyme.core.rest.resources.taps.reports.tables.nameres.NameResolutionResponseReport;
import app.nzyme.core.rest.resources.taps.reports.tables.nameres.NameResolutionResponsesReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.UUID;

public class NameResolutionTable implements DataTable {

    private final Timer totalReportTimer;

    private final TablesService tablesService;

    public NameResolutionTable(TablesService tablesService) {
        this.tablesService = tablesService;

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.NAMERES_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, NameResolutionResponsesReport report) {
        if (report.responses().isEmpty()) {
            return;
        }

        try (Timer.Context ignored = totalReportTimer.time()) {
            tablesService.getNzyme().getDatabase().useHandle(handle -> {
                PreparedBatch batch = handle.prepareBatch("INSERT INTO name_resolution_responses(uuid, tap_uuid, " +
                        "protocol, responder_mac, responder_address, name, answer_addresses, response_count, " +
                        "unicast_count, victims, first_seen, last_seen, created_at) VALUES(:uuid, :tap_uuid, " +
                        ":protocol, :responder_mac, :responder_address::inet, :name, :answer_addresses::jsonb, " +
                        ":response_count, :unicast_count, :victims::jsonb, :first_seen, :last_seen, NOW())");

                for (NameResolutionResponseReport r : report.responses()) {
                    batch.bind("uuid", UUID.randomUUID())
                            .bind("tap_uuid", tapUuid)
                            .bind("protocol", r.protocol())
                            .bind("responder_mac", r.responderMac())
                            .bind("responder_address", r.responderAddress())
                            .bind("name", r.name())
                            .bind("answer_addresses", NameResolutionJson.toJson(r.answerAddresses()))
                            .bind("response_count", r.responseCount())
                            .bind("unicast_count", r.unicastCount())
                            .bind("victims", NameResolutionJson.toJson(r.victims()))
                            .bind("first_seen", r.firstSeen())
                            .bind("last_seen", r.lastSeen())
                            .add();
                }

                batch.execute();
            });
        }
    }

    @Override
    public void retentionClean() {
        // NOOP. Cleaned per tenant by the name resolution spoofing detector.
    }

}
