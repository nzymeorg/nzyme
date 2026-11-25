package app.nzyme.core.rest.resources.taps.reports.tables.ndp;

import app.nzyme.core.ethernet.ndp.NdpPrefix;
import app.nzyme.core.ethernet.ndp.NdpRoute;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class NdpRouterAdvertisementReport {

    @Nullable
    public abstract String sourceMac();

    @Nullable
    public abstract String destinationMac();

    public abstract String sourceAddress();

    public abstract String destinationAddress();

    public abstract int hopLimit();

    @Nullable
    public abstract String sourceLinkLayerAddress();

    public abstract int currentHopLimit();

    public abstract boolean managed();

    public abstract boolean otherConfiguration();

    public abstract String preference();

    public abstract int routerLifetime();

    public abstract long reachableTime();

    public abstract long retransmitTimer();

    @Nullable
    public abstract Long mtu();

    public abstract List<NdpPrefix> prefixes();

    public abstract List<NdpRoute> routes();

    public abstract List<String> dnsServers();

    @Nullable
    public abstract Long dnsServersLifetime();

    public abstract List<String> dnsSearchList();

    public abstract long packetCount();

    public abstract DateTime firstSeen();

    public abstract DateTime lastSeen();

    @JsonCreator
    public static NdpRouterAdvertisementReport create(@JsonProperty("source_mac") String sourceMac,
                                                      @JsonProperty("destination_mac") String destinationMac,
                                                      @JsonProperty("source_address") String sourceAddress,
                                                      @JsonProperty("destination_address") String destinationAddress,
                                                      @JsonProperty("hop_limit") int hopLimit,
                                                      @JsonProperty("source_link_layer_address") String sourceLinkLayerAddress,
                                                      @JsonProperty("current_hop_limit") int currentHopLimit,
                                                      @JsonProperty("managed") boolean managed,
                                                      @JsonProperty("other_configuration") boolean otherConfiguration,
                                                      @JsonProperty("preference") String preference,
                                                      @JsonProperty("router_lifetime") int routerLifetime,
                                                      @JsonProperty("reachable_time") long reachableTime,
                                                      @JsonProperty("retransmit_timer") long retransmitTimer,
                                                      @JsonProperty("mtu") Long mtu,
                                                      @JsonProperty("prefixes") List<NdpPrefix> prefixes,
                                                      @JsonProperty("routes") List<NdpRoute> routes,
                                                      @JsonProperty("dns_servers") List<String> dnsServers,
                                                      @JsonProperty("dns_servers_lifetime") Long dnsServersLifetime,
                                                      @JsonProperty("dns_search_list") List<String> dnsSearchList,
                                                      @JsonProperty("packet_count") long packetCount,
                                                      @JsonProperty("first_seen") DateTime firstSeen,
                                                      @JsonProperty("last_seen") DateTime lastSeen) {
        return builder()
                .sourceMac(sourceMac)
                .destinationMac(destinationMac)
                .sourceAddress(sourceAddress)
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
        return new AutoValue_NdpRouterAdvertisementReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sourceMac(String sourceMac);

        public abstract Builder destinationMac(String destinationMac);

        public abstract Builder sourceAddress(String sourceAddress);

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

        public abstract NdpRouterAdvertisementReport build();
    }

}
