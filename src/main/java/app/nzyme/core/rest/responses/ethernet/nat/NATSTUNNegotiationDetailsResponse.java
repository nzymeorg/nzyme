package app.nzyme.core.rest.responses.ethernet.nat;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class NATSTUNNegotiationDetailsResponse {

    @JsonProperty("negotiation_key")
    public abstract String negotiationKey();

    @JsonProperty("negotiation_key_sha256")
    public abstract String negotiationKeySha256();

    @JsonProperty("transport")
    public abstract String transport();

    @JsonProperty("successful")
    public abstract boolean successful();

    @JsonProperty("is_turn")
    public abstract boolean isTurn();

    @Nullable @JsonProperty("source")
    public abstract L4AddressResponse source();
    @Nullable @JsonProperty("destination")
    public abstract L4AddressResponse destination();

    @JsonProperty("mapped_addresses")
    public abstract List<L4AddressResponse> mappedAddresses();

    @JsonProperty("peer_addresses")
    public abstract List<L4AddressResponse> peerAddresses();

    @JsonProperty("relayed_addresses")
    public abstract List<L4AddressResponse> relayedAddresses();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_activity")
    public abstract DateTime lastActivity();

    public static NATSTUNNegotiationDetailsResponse create(String negotiationKey, String negotiationKeySha256, String transport, boolean successful, boolean isTurn, L4AddressResponse source, L4AddressResponse destination, List<L4AddressResponse> mappedAddresses, List<L4AddressResponse> peerAddresses, List<L4AddressResponse> relayedAddresses, DateTime firstSeen, DateTime lastActivity) {
        return builder()
                .negotiationKey(negotiationKey)
                .negotiationKeySha256(negotiationKeySha256)
                .transport(transport)
                .successful(successful)
                .isTurn(isTurn)
                .source(source)
                .destination(destination)
                .mappedAddresses(mappedAddresses)
                .peerAddresses(peerAddresses)
                .relayedAddresses(relayedAddresses)
                .firstSeen(firstSeen)
                .lastActivity(lastActivity)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NATSTUNNegotiationDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder negotiationKey(String negotiationKey);

        public abstract Builder negotiationKeySha256(String negotiationKeySha256);

        public abstract Builder transport(String transport);

        public abstract Builder successful(boolean successful);

        public abstract Builder isTurn(boolean isTurn);

        public abstract Builder source(L4AddressResponse source);

        public abstract Builder destination(L4AddressResponse destination);

        public abstract Builder mappedAddresses(List<L4AddressResponse> mappedAddresses);

        public abstract Builder peerAddresses(List<L4AddressResponse> peerAddresses);

        public abstract Builder relayedAddresses(List<L4AddressResponse> relayedAddresses);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastActivity(DateTime lastActivity);

        public abstract NATSTUNNegotiationDetailsResponse build();
    }
}
