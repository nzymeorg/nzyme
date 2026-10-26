package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.core.ethernet.spoofing.DhcpOfferedParameters;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.List;

@AutoValue
public abstract class DhcpOfferedParametersResponse {

    @Nullable
    @JsonProperty("subnet_mask")
    public abstract String subnetMask();

    @JsonProperty("routers")
    public abstract List<String> routers();

    @JsonProperty("dns_servers")
    public abstract List<String> dnsServers();

    @Nullable
    @JsonProperty("domain_name")
    public abstract String domainName();

    @Nullable
    @JsonProperty("lease_time_seconds")
    public abstract Long leaseTimeSeconds();

    @Nullable
    public static DhcpOfferedParametersResponse fromParameters(@Nullable DhcpOfferedParameters p) {
        if (p == null) {
            return null;
        }

        return create(p.subnetMask(), p.routers(), p.dnsServers(), p.domainName(), p.leaseTimeSeconds());
    }

    public static DhcpOfferedParametersResponse create(String subnetMask, List<String> routers, List<String> dnsServers, String domainName, Long leaseTimeSeconds) {
        return builder()
                .subnetMask(subnetMask)
                .routers(routers)
                .dnsServers(dnsServers)
                .domainName(domainName)
                .leaseTimeSeconds(leaseTimeSeconds)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpOfferedParametersResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder subnetMask(String subnetMask);
        public abstract Builder routers(List<String> routers);
        public abstract Builder dnsServers(List<String> dnsServers);
        public abstract Builder domainName(String domainName);
        public abstract Builder leaseTimeSeconds(Long leaseTimeSeconds);
        public abstract DhcpOfferedParametersResponse build();
    }
}
