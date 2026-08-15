package app.nzyme.core.rest.resources.taps.reports.tables.nat;

import app.nzyme.core.rest.resources.taps.reports.tables.SocketAddressReport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class StunNegotiationFlowReport {

    @Nullable
    public abstract String negotiationKey();
    public abstract String sourceAddress();
    @Nullable
    public abstract String sourceMac();
    public abstract int sourcePort();
    public abstract String destinationAddress();
    public abstract int destinationPort();
    public abstract String transport();
    public abstract List<String> ufrags();
    public abstract boolean successful();
    public abstract boolean isTurn();
    public abstract List<String> turnUsernames();
    public abstract List<SocketAddressReport> mappedAddresses();
    public abstract List<SocketAddressReport> relayedAddresses();
    public abstract List<SocketAddressReport> peerAddresses();
    public abstract DateTime firstSeen();
    public abstract DateTime lastActivity();

    @JsonCreator
    public static StunNegotiationFlowReport create(@JsonProperty("negotiation_key") String negotiationKey,
                                                   @JsonProperty("source_address") String sourceAddress,
                                                   @JsonProperty("source_mac") String sourceMac,
                                                   @JsonProperty("source_port") int sourcePort,
                                                   @JsonProperty("destination_address") String destinationAddress,
                                                   @JsonProperty("destination_port") int destinationPort,
                                                   @JsonProperty("transport") String transport,
                                                   @JsonProperty("ufrags") List<String> ufrags,
                                                   @JsonProperty("successful") boolean successful,
                                                   @JsonProperty("is_turn") boolean isTurn,
                                                   @JsonProperty("turn_usernames") List<String> turnUsernames,
                                                   @JsonProperty("mapped_addresses") List<SocketAddressReport> mappedAddresses,
                                                   @JsonProperty("relayed_addresses") List<SocketAddressReport> relayedAddresses,
                                                   @JsonProperty("peer_addresses") List<SocketAddressReport> peerAddresses,
                                                   @JsonProperty("first_seen") DateTime firstSeen,
                                                   @JsonProperty("last_activity") DateTime lastActivity) {
        return builder()
                .negotiationKey(negotiationKey)
                .sourceAddress(sourceAddress)
                .sourceMac(sourceMac)
                .sourcePort(sourcePort)
                .destinationAddress(destinationAddress)
                .destinationPort(destinationPort)
                .transport(transport)
                .ufrags(ufrags)
                .successful(successful)
                .isTurn(isTurn)
                .turnUsernames(turnUsernames)
                .mappedAddresses(mappedAddresses)
                .relayedAddresses(relayedAddresses)
                .peerAddresses(peerAddresses)
                .firstSeen(firstSeen)
                .lastActivity(lastActivity)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_StunNegotiationFlowReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder negotiationKey(String negotiationKey);

        public abstract Builder sourceAddress(String sourceAddress);

        public abstract Builder sourceMac(String sourceMac);

        public abstract Builder sourcePort(int sourcePort);

        public abstract Builder destinationAddress(String destinationAddress);

        public abstract Builder destinationPort(int destinationPort);

        public abstract Builder transport(String transport);

        public abstract Builder ufrags(List<String> ufrags);

        public abstract Builder successful(boolean successful);

        public abstract Builder isTurn(boolean isTurn);

        public abstract Builder turnUsernames(List<String> turnUsernames);

        public abstract Builder mappedAddresses(List<SocketAddressReport> mappedAddresses);

        public abstract Builder relayedAddresses(List<SocketAddressReport> relayedAddresses);

        public abstract Builder peerAddresses(List<SocketAddressReport> peerAddresses);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastActivity(DateTime lastActivity);

        public abstract StunNegotiationFlowReport build();
    }
}
