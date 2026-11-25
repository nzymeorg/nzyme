package app.nzyme.core.rest.responses.ethernet.ndp;

import app.nzyme.core.ethernet.ndp.NdpPrefix;
import app.nzyme.core.ethernet.ndp.NdpRoute;
import app.nzyme.core.rest.responses.ethernet.InternalAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class NdpRouterAdvertisementResponse {

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @JsonProperty("router")
    public abstract InternalAddressResponse router();

    @JsonProperty("destination_address")
    public abstract String destinationAddress();

    @JsonProperty("hop_limit")
    public abstract int hopLimit();

    @JsonProperty("source_link_layer_address")
    @Nullable
    public abstract String sourceLinkLayerAddress();

    @JsonProperty("current_hop_limit")
    public abstract int currentHopLimit();

    @JsonProperty("managed")
    public abstract boolean managed();

    @JsonProperty("other_configuration")
    public abstract boolean otherConfiguration();

    @JsonProperty("preference")
    public abstract String preference();

    @JsonProperty("router_lifetime")
    public abstract int routerLifetime();

    @JsonProperty("reachable_time")
    public abstract long reachableTime();

    @JsonProperty("retransmit_timer")
    public abstract long retransmitTimer();

    @JsonProperty("mtu")
    @Nullable
    public abstract Long mtu();

    @JsonProperty("prefixes")
    public abstract List<NdpPrefix> prefixes();

    @JsonProperty("routes")
    public abstract List<NdpRoute> routes();

    @JsonProperty("dns_servers")
    public abstract List<String> dnsServers();

    @JsonProperty("dns_servers_lifetime")
    @Nullable
    public abstract Long dnsServersLifetime();

    @JsonProperty("dns_search_list")
    public abstract List<String> dnsSearchList();

    @JsonProperty("packet_count")
    public abstract long packetCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static NdpRouterAdvertisementResponse create(UUID tapUuid,
                                                        InternalAddressResponse router,
                                                        String destinationAddress,
                                                        int hopLimit,
                                                        String sourceLinkLayerAddress,
                                                        int currentHopLimit,
                                                        boolean managed,
                                                        boolean otherConfiguration,
                                                        String preference,
                                                        int routerLifetime,
                                                        long reachableTime,
                                                        long retransmitTimer,
                                                        Long mtu,
                                                        List<NdpPrefix> prefixes,
                                                        List<NdpRoute> routes,
                                                        List<String> dnsServers,
                                                        Long dnsServersLifetime,
                                                        List<String> dnsSearchList,
                                                        long packetCount,
                                                        DateTime firstSeen,
                                                        DateTime lastSeen) {
        return builder()
                .tapUuid(tapUuid)
                .router(router)
                .destinationAddress(destinationAddress)
                .hopLimit(hopLimit)
                .sourceLinkLayerAddress(sourceLinkLayerAddress)
                .currentHopLimit(currentHopLimit)
                .managed(managed)
                .otherConfiguration(otherConfiguration)
                .preference(preference)
                .routerLifetime(routerLifetime)
                .reachableTime(reachableTime)
                .retransmitTimer(retransmitTimer)
                .mtu(mtu)
                .prefixes(prefixes)
                .routes(routes)
                .dnsServers(dnsServers)
                .dnsServersLifetime(dnsServersLifetime)
                .dnsSearchList(dnsSearchList)
                .packetCount(packetCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NdpRouterAdvertisementResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder router(InternalAddressResponse router);

        public abstract Builder destinationAddress(String destinationAddress);

        public abstract Builder hopLimit(int hopLimit);

        public abstract Builder sourceLinkLayerAddress(String sourceLinkLayerAddress);

        public abstract Builder currentHopLimit(int currentHopLimit);

        public abstract Builder managed(boolean managed);

        public abstract Builder otherConfiguration(boolean otherConfiguration);

        public abstract Builder preference(String preference);

        public abstract Builder routerLifetime(int routerLifetime);

        public abstract Builder reachableTime(long reachableTime);

        public abstract Builder retransmitTimer(long retransmitTimer);

        public abstract Builder mtu(Long mtu);

        public abstract Builder prefixes(List<NdpPrefix> prefixes);

        public abstract Builder routes(List<NdpRoute> routes);

        public abstract Builder dnsServers(List<String> dnsServers);

        public abstract Builder dnsServersLifetime(Long dnsServersLifetime);

        public abstract Builder dnsSearchList(List<String> dnsSearchList);

        public abstract Builder packetCount(long packetCount);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract NdpRouterAdvertisementResponse build();
    }

}
