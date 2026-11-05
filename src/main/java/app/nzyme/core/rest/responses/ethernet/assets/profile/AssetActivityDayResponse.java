package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class AssetActivityDayResponse {

    @JsonProperty("day")
    public abstract String day();
    @JsonProperty("events")
    public abstract long events();
    @JsonProperty("bytes")
    public abstract long bytes();
    @JsonProperty("arp_packets")
    public abstract long arpPackets();
    @JsonProperty("dhcp_transactions")
    public abstract long dhcpTransactions();
    @JsonProperty("dns_queries")
    public abstract long dnsQueries();
    @JsonProperty("tcp_sessions")
    public abstract long tcpSessions();
    @JsonProperty("udp_sessions")
    public abstract long udpSessions();

    public static AssetActivityDayResponse create(String day, long events, long bytes, long arpPackets, long dhcpTransactions, long dnsQueries, long tcpSessions, long udpSessions) {
        return builder()
                .day(day)
                .events(events)
                .bytes(bytes)
                .arpPackets(arpPackets)
                .dhcpTransactions(dhcpTransactions)
                .dnsQueries(dnsQueries)
                .tcpSessions(tcpSessions)
                .udpSessions(udpSessions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetActivityDayResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder day(String day);
        public abstract Builder events(long events);
        public abstract Builder bytes(long bytes);
        public abstract Builder arpPackets(long arpPackets);
        public abstract Builder dhcpTransactions(long dhcpTransactions);
        public abstract Builder dnsQueries(long dnsQueries);
        public abstract Builder tcpSessions(long tcpSessions);
        public abstract Builder udpSessions(long udpSessions);

        public abstract AssetActivityDayResponse build();
    }

}
