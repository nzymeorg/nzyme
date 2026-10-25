package app.nzyme.core.rest.responses.ethernet.icmp;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class IcmpUnreachableHostResponse {

    @JsonProperty("source_address")
    public abstract String sourceAddress();

    @Nullable
    @JsonProperty("source_address_data")
    public abstract L4AddressResponse sourceAddressData();

    @Nullable
    @JsonProperty("source_mac")
    public abstract EthernetMacAddressResponse sourceMac();

    @JsonProperty("unreachable_host")
    public abstract String unreachableHost();

    @Nullable
    @JsonProperty("unreachable_host_data")
    public abstract L4AddressResponse unreachableHostData();

    @Nullable
    @JsonProperty("protocol")
    public abstract Integer protocol();

    @Nullable
    @JsonProperty("protocol_name")
    public abstract String protocolName();

    @JsonProperty("code")
    public abstract int code();

    @JsonProperty("reason")
    public abstract String reason();

    @JsonProperty("reporting_router")
    public abstract String reportingRouter();

    @Nullable
    @JsonProperty("reporting_router_data")
    public abstract L4AddressResponse reportingRouterData();

    @Nullable
    @JsonProperty("reporting_router_mac")
    public abstract EthernetMacAddressResponse reportingRouterMac();

    @JsonProperty("packet_count")
    public abstract long packetCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static IcmpUnreachableHostResponse create(String sourceAddress, L4AddressResponse sourceAddressData, EthernetMacAddressResponse sourceMac, String unreachableHost, L4AddressResponse unreachableHostData, Integer protocol, String protocolName, int code, String reason, String reportingRouter, L4AddressResponse reportingRouterData, EthernetMacAddressResponse reportingRouterMac, long packetCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .sourceAddress(sourceAddress)
                .sourceAddressData(sourceAddressData)
                .sourceMac(sourceMac)
                .unreachableHost(unreachableHost)
                .unreachableHostData(unreachableHostData)
                .protocol(protocol)
                .protocolName(protocolName)
                .code(code)
                .reason(reason)
                .reportingRouter(reportingRouter)
                .reportingRouterData(reportingRouterData)
                .reportingRouterMac(reportingRouterMac)
                .packetCount(packetCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpUnreachableHostResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder sourceAddressData(L4AddressResponse sourceAddressData);
        public abstract Builder sourceMac(EthernetMacAddressResponse sourceMac);
        public abstract Builder unreachableHost(String unreachableHost);
        public abstract Builder unreachableHostData(L4AddressResponse unreachableHostData);
        public abstract Builder protocol(Integer protocol);
        public abstract Builder protocolName(String protocolName);
        public abstract Builder code(int code);
        public abstract Builder reason(String reason);
        public abstract Builder reportingRouter(String reportingRouter);
        public abstract Builder reportingRouterData(L4AddressResponse reportingRouterData);
        public abstract Builder reportingRouterMac(EthernetMacAddressResponse reportingRouterMac);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract IcmpUnreachableHostResponse build();
    }
}
