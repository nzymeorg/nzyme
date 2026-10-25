package app.nzyme.core.tables.ethernet;

import app.nzyme.core.rest.resources.taps.reports.tables.icmp.IcmpErrorReport;
import app.nzyme.core.rest.resources.taps.reports.tables.icmp.IcmpReport;
import app.nzyme.core.rest.resources.taps.reports.tables.icmp.IcmpSummaryReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.Optional;
import java.util.UUID;

// Queries and replies are one aggregated row per key per report period; error messages are one row per packet.
public class ICMPTable implements DataTable {

    private final Timer totalReportTimer;

    private final TablesService tablesService;

    public ICMPTable(TablesService tablesService) {
        this.tablesService = tablesService;

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.ICMP_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, IcmpReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            tablesService.getNzyme().getDatabase().useHandle(handle -> {
                Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);
                if (tap.isEmpty()) {
                    throw new RuntimeException("Reporting tap [" + tapUuid + "] not found. Not processing report.");
                }

                PreparedBatch batch = handle.prepareBatch("INSERT INTO icmp_packets(tap_uuid, source_mac, " +
                        "destination_mac, source_address, destination_address, icmp_type, icmp_code, " +
                        "packet_count, bytes_min, bytes_max, ip_ttl, ip_tos, ip_df, redirect_gateway, " +
                        "quoted_protocol, quoted_source_address, quoted_destination_address, quoted_source_port, " +
                        "quoted_destination_port, quoted_ttl, first_seen, last_seen, created_at) " +
                        "VALUES(:tap_uuid, :source_mac, :destination_mac, :source_address::inet, " +
                        ":destination_address::inet, :icmp_type, :icmp_code, :packet_count, :bytes_min, " +
                        ":bytes_max, :ip_ttl, :ip_tos, :ip_df, :redirect_gateway::inet, :quoted_protocol, " +
                        ":quoted_source_address::inet, :quoted_destination_address::inet, :quoted_source_port, " +
                        ":quoted_destination_port, :quoted_ttl, :first_seen, :last_seen, NOW())");

                for (IcmpSummaryReport s : report.summaries()) {
                    batch.bind("tap_uuid", tapUuid)
                            .bind("source_mac", s.sourceMac())
                            .bind("destination_mac", s.destinationMac())
                            .bind("source_address", s.sourceAddress())
                            .bind("destination_address", s.destinationAddress())
                            .bind("icmp_type", s.icmpType())
                            .bind("icmp_code", s.icmpCode())
                            .bind("packet_count", s.packetCount())
                            .bind("bytes_min", s.bytesMin())
                            .bind("bytes_max", s.bytesMax())
                            .bind("ip_ttl", s.ipTtl())
                            .bind("ip_tos", s.ipTos())
                            .bind("ip_df", s.ipDf())
                            .bind("redirect_gateway", (String) null)
                            .bind("quoted_protocol", (Integer) null)
                            .bind("quoted_source_address", (String) null)
                            .bind("quoted_destination_address", (String) null)
                            .bind("quoted_source_port", (Integer) null)
                            .bind("quoted_destination_port", (Integer) null)
                            .bind("quoted_ttl", (Integer) null)
                            .bind("first_seen", s.firstSeen())
                            .bind("last_seen", s.lastSeen())
                            .add();
                }

                for (IcmpErrorReport e : report.errors()) {
                    batch.bind("tap_uuid", tapUuid)
                            .bind("source_mac", e.sourceMac())
                            .bind("destination_mac", e.destinationMac())
                            .bind("source_address", e.sourceAddress())
                            .bind("destination_address", e.destinationAddress())
                            .bind("icmp_type", e.icmpType())
                            .bind("icmp_code", e.icmpCode())
                            .bind("packet_count", 1L)
                            .bind("bytes_min", 0)
                            .bind("bytes_max", 0)
                            .bind("ip_ttl", e.ipTtl())
                            .bind("ip_tos", 0)
                            .bind("ip_df", false)
                            .bind("redirect_gateway", e.redirectGateway())
                            .bind("quoted_protocol", e.quotedProtocol())
                            .bind("quoted_source_address", e.quotedSourceAddress())
                            .bind("quoted_destination_address", e.quotedDestinationAddress())
                            .bind("quoted_source_port", e.quotedSourcePort())
                            .bind("quoted_destination_port", e.quotedDestinationPort())
                            .bind("quoted_ttl", e.quotedTtl())
                            .bind("first_seen", e.timestamp())
                            .bind("last_seen", e.timestamp())
                            .add();
                }

                batch.execute();
            });
        }
    }

    @Override
    public void retentionClean() {
        // NOOP. Database cleaned by category/tenant independently.
    }

}
