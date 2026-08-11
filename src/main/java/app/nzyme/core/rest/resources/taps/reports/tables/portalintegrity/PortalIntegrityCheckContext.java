package app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.List;

@AutoValue
public abstract class PortalIntegrityCheckContext {

    public abstract String networkInterface();
    public abstract String mac();
    public abstract String assignedCidr();
    @Nullable
    public abstract String gateway();
    @Nullable
    public abstract String dhcpServer();
    public abstract List<String> dnsServers();

    @JsonCreator
    public static PortalIntegrityCheckContext create(@JsonProperty("network_interface") String networkInterface,
                                                     @JsonProperty("mac") String mac,
                                                     @JsonProperty("assigned_cidr") String assignedCidr,
                                                     @JsonProperty("gateway") String gateway,
                                                     @JsonProperty("dhcp_server") String dhcpServer,
                                                     @JsonProperty("dns_servers") List<String> dnsServers) {
        return builder()
                .networkInterface(networkInterface)
                .mac(mac)
                .assignedCidr(assignedCidr)
                .gateway(gateway)
                .dhcpServer(dhcpServer)
                .dnsServers(dnsServers)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityCheckContext.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder networkInterface(String networkInterface);

        public abstract Builder mac(String mac);

        public abstract Builder assignedCidr(String assignedCidr);

        public abstract Builder gateway(String gateway);

        public abstract Builder dhcpServer(String dhcpServer);

        public abstract Builder dnsServers(List<String> dnsServers);

        public abstract PortalIntegrityCheckContext build();
    }
}
