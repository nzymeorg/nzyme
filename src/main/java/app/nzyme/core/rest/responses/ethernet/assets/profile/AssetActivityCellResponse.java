package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class AssetActivityCellResponse {

    @JsonProperty("day_of_week")
    public abstract int dayOfWeek();
    @JsonProperty("hour")
    public abstract int hour();
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

    public static AssetActivityCellResponse create(int dayOfWeek, int hour, long events, long bytes, long arpPackets, long dhcpTransactions, long dnsQueries, long tcpSessions, long udpSessions) {
        return builder()
                .dayOfWeek(dayOfWeek)
                .hour(hour)
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
        return new AutoValue_AssetActivityCellResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder dayOfWeek(int dayOfWeek);
        public abstract Builder hour(int hour);
        public abstract Builder events(long events);
        public abstract Builder bytes(long bytes);

        public abstract Builder arpPackets(long arpPackets);

        public abstract Builder dhcpTransactions(long dhcpTransactions);

        public abstract Builder dnsQueries(long dnsQueries);

        public abstract Builder tcpSessions(long tcpSessions);

        public abstract Builder udpSessions(long udpSessions);

        public abstract AssetActivityCellResponse build();
    }

}
