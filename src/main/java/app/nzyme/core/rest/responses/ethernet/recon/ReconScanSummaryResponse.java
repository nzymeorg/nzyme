package app.nzyme.core.rest.responses.ethernet.recon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

@Schema(description = "Condensed view of what a scan targeted and found, for the scan details page.")
@AutoValue
public abstract class ReconScanSummaryResponse {

    @Schema(description = "Port scans only: the ports of the target that answered, ascending, with probe count and timing. Limited to 500.")
    @Nullable
    @JsonProperty("answered_ports")
    public abstract List<ReconScanAnsweredPortResponse> answeredPorts();

    @Schema(description = "Port scans only: total number of ports that answered.")
    @Nullable
    @JsonProperty("answered_ports_total")
    public abstract Long answeredPortsTotal();

    @Nullable
    @JsonProperty("unanswered_ports")
    public abstract List<ReconScanAnsweredPortResponse> unansweredPorts();

    @Nullable
    @JsonProperty("unanswered_ports_total")
    public abstract Long unansweredPortsTotal();

    @Schema(description = "Sweeps only: the hosts that answered, each with its answering ports. Limited to 250.")
    @Nullable
    @JsonProperty("answered_hosts")
    public abstract List<ReconScanAnsweredHostResponse> answeredHosts();

    @Schema(description = "Sweeps only: total number of hosts that answered.")
    @Nullable
    @JsonProperty("answered_hosts_total")
    public abstract Long answeredHostsTotal();

    @Schema(description = "Targets by the time they were first probed, per outcome. Keys are bucket timestamps.")
    @JsonProperty("timeline")
    public abstract Map<DateTime, ReconScanTimelineBucketResponse> timeline();

    @Schema(description = "Bucket size of the timeline, minute or hour.")
    @JsonProperty("timeline_bucket")
    public abstract String timelineBucket();

    @Schema(description = "Sweeps only: targets grouped by network, /24 for IPv4 and /64 for IPv6. Limited to 100.")
    @Nullable
    @JsonProperty("networks")
    public abstract List<ReconScanTargetNetworkResponse> networks();

    @Schema(description = "Port scans only: targets grouped into well-known, registered and dynamic port ranges.")
    @Nullable
    @JsonProperty("port_ranges")
    public abstract List<ReconScanPortRangeResponse> portRanges();

    public static ReconScanSummaryResponse create(List<ReconScanAnsweredPortResponse> answeredPorts, Long answeredPortsTotal, List<ReconScanAnsweredPortResponse> unansweredPorts, Long unansweredPortsTotal, List<ReconScanAnsweredHostResponse> answeredHosts, Long answeredHostsTotal, Map<DateTime, ReconScanTimelineBucketResponse> timeline, String timelineBucket, List<ReconScanTargetNetworkResponse> networks, List<ReconScanPortRangeResponse> portRanges) {
        return builder()
                .answeredPorts(answeredPorts)
                .answeredPortsTotal(answeredPortsTotal)
                .unansweredPorts(unansweredPorts)
                .unansweredPortsTotal(unansweredPortsTotal)
                .answeredHosts(answeredHosts)
                .answeredHostsTotal(answeredHostsTotal)
                .timeline(timeline)
                .timelineBucket(timelineBucket)
                .networks(networks)
                .portRanges(portRanges)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconScanSummaryResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder answeredPorts(List<ReconScanAnsweredPortResponse> answeredPorts);
        public abstract Builder answeredPortsTotal(Long answeredPortsTotal);
        public abstract Builder unansweredPorts(List<ReconScanAnsweredPortResponse> unansweredPorts);
        public abstract Builder unansweredPortsTotal(Long unansweredPortsTotal);
        public abstract Builder answeredHosts(List<ReconScanAnsweredHostResponse> answeredHosts);
        public abstract Builder answeredHostsTotal(Long answeredHostsTotal);
        public abstract Builder timeline(Map<DateTime, ReconScanTimelineBucketResponse> timeline);
        public abstract Builder timelineBucket(String timelineBucket);
        public abstract Builder networks(List<ReconScanTargetNetworkResponse> networks);
        public abstract Builder portRanges(List<ReconScanPortRangeResponse> portRanges);
        public abstract ReconScanSummaryResponse build();
    }
}
