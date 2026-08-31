package app.nzyme.core.rest.responses.ethernet.portalintegrity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class PortalIntegrityReportDetailsResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();
    @JsonProperty("control_url")
    public abstract String controlUrl();
    @JsonProperty("probe_interface")
    public abstract String probeInterface();
    @JsonProperty("probe_mac")
    public abstract String probeMac();
    @JsonProperty("probe_name")
    public abstract String probeName();
    @JsonProperty("assigned_address")
    public abstract String assignedAddress();
    @Nullable @JsonProperty("gateway_address")
    public abstract String gatewayAddress();
    @Nullable @JsonProperty("dhcp_server_address")
    public abstract String dhcpServerAddress();
    @JsonProperty("dns_servers")
    public abstract List<String> dnsServers();
    @JsonProperty("hop_count")
    public abstract int hopCount();
    @Nullable @JsonProperty("last_hop_url")
    public abstract String lastHopUrl();
    @Nullable @JsonProperty("error")
    public abstract String error();
    @JsonProperty("verdict")
    public abstract String verdict();
    @JsonProperty("verdict_reasons")
    public abstract List<String> verdictReasons();
    @JsonProperty("probed_at")
    public abstract DateTime probedAt();

    @Nullable @JsonProperty("hops")
    public abstract List<PortalIntegrityReportHopDetailsResponse> hops();

    public static PortalIntegrityReportDetailsResponse create(UUID uuid, String controlUrl, String probeInterface, String probeMac, String probeName, String assignedAddress, String gatewayAddress, String dhcpServerAddress, List<String> dnsServers, int hopCount, String lastHopUrl, String error, String verdict, List<String> verdictReasons, DateTime probedAt, List<PortalIntegrityReportHopDetailsResponse> hops) {
        return builder()
                .uuid(uuid)
                .controlUrl(controlUrl)
                .probeInterface(probeInterface)
                .probeMac(probeMac)
                .probeName(probeName)
                .assignedAddress(assignedAddress)
                .gatewayAddress(gatewayAddress)
                .dhcpServerAddress(dhcpServerAddress)
                .dnsServers(dnsServers)
                .hopCount(hopCount)
                .lastHopUrl(lastHopUrl)
                .error(error)
                .verdict(verdict)
                .verdictReasons(verdictReasons)
                .probedAt(probedAt)
                .hops(hops)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityReportDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder controlUrl(String controlUrl);

        public abstract Builder probeInterface(String probeInterface);

        public abstract Builder probeMac(String probeMac);

        public abstract Builder probeName(String probeName);

        public abstract Builder assignedAddress(String assignedAddress);

        public abstract Builder gatewayAddress(String gatewayAddress);

        public abstract Builder dhcpServerAddress(String dhcpServerAddress);

        public abstract Builder dnsServers(List<String> dnsServers);

        public abstract Builder hopCount(int hopCount);

        public abstract Builder lastHopUrl(String lastHopUrl);

        public abstract Builder error(String error);

        public abstract Builder verdict(String verdict);

        public abstract Builder verdictReasons(List<String> verdictReasons);

        public abstract Builder probedAt(DateTime probedAt);

        public abstract Builder hops(List<PortalIntegrityReportHopDetailsResponse> hops);

        public abstract PortalIntegrityReportDetailsResponse build();
    }
}
