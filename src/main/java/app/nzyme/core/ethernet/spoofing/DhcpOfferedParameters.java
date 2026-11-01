package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.ethernet.dhcp.db.DHCPServerResponseEntry;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@AutoValue
public abstract class DhcpOfferedParameters {

    private static final ObjectMapper OM = new ObjectMapper();

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

    public static DhcpOfferedParameters fromResponse(DHCPServerResponseEntry r) {
        return create(r.subnetMask(), r.routers(), r.dnsServers(), r.domainName(), r.leaseTimeSeconds());
    }

    public boolean deviatesFrom(DhcpOfferedParameters baseline) {
        if (subnetMask() != null && !subnetMask().equals(baseline.subnetMask())) {
            return true;
        }

        if (!routers().isEmpty() && !Sets.newHashSet(routers()).equals(Sets.newHashSet(baseline.routers()))) {
            return true;
        }

        if (!dnsServers().isEmpty()
                && !Sets.newHashSet(dnsServers()).equals(Sets.newHashSet(baseline.dnsServers()))) {
            return true;
        }

        String domain = normalizeDomain(domainName());
        return domain != null && !domain.equals(normalizeDomain(baseline.domainName()));
    }

    public DhcpOfferedParameters mergedWith(@Nullable DhcpOfferedParameters previous) {
        if (previous == null) {
            return this;
        }

        return create(
                subnetMask() != null ? subnetMask() : previous.subnetMask(),
                routers().isEmpty() ? previous.routers() : routers(),
                dnsServers().isEmpty() ? previous.dnsServers() : dnsServers(),
                normalizeDomain(domainName()) != null ? domainName() : previous.domainName(),
                leaseTimeSeconds() != null ? leaseTimeSeconds() : previous.leaseTimeSeconds()
        );
    }

    public String configurationKey() {
        return subnetMask() + "|" + String.join(",", routers().stream().sorted().toList()) + "|"
                + String.join(",", dnsServers().stream().sorted().toList()) + "|" + normalizeDomain(domainName());
    }

    @Nullable
    private static String normalizeDomain(@Nullable String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }

        return domain.trim().toLowerCase();
    }

    public String toJson() {
        return OM.writeValueAsString(this);
    }

    @Nullable
    public static DhcpOfferedParameters fromJson(@Nullable String json) {
        if (json == null || json.isBlank() || json.equals("null")) {
            return null;
        }

        return OM.readValue(json, DhcpOfferedParameters.class);
    }

    @JsonCreator
    public static DhcpOfferedParameters create(@JsonProperty("subnet_mask") String subnetMask,
                                               @JsonProperty("routers") List<String> routers,
                                               @JsonProperty("dns_servers") List<String> dnsServers,
                                               @JsonProperty("domain_name") String domainName,
                                               @JsonProperty("lease_time_seconds") Long leaseTimeSeconds) {
        return builder()
                .subnetMask(subnetMask)
                .routers(routers == null ? List.of() : routers)
                .dnsServers(dnsServers == null ? List.of() : dnsServers)
                .domainName(domainName)
                .leaseTimeSeconds(leaseTimeSeconds)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpOfferedParameters.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder subnetMask(String subnetMask);
        public abstract Builder routers(List<String> routers);
        public abstract Builder dnsServers(List<String> dnsServers);
        public abstract Builder domainName(String domainName);
        public abstract Builder leaseTimeSeconds(Long leaseTimeSeconds);
        public abstract DhcpOfferedParameters build();
    }
}
