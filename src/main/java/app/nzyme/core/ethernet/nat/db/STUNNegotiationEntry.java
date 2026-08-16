package app.nzyme.core.ethernet.nat.db;

import app.nzyme.core.ethernet.l4.db.L4AddressData;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class STUNNegotiationEntry {

    public abstract String negotiationKey();
    public abstract String negotiationKeySha256();
    public abstract boolean isActive();
    public abstract String transport();
    public abstract boolean successful();
    public abstract boolean isTurn();
    @Nullable
    public abstract Long bytesExchanged();
    @Nullable
    public abstract L4AddressData source();
    @Nullable
    public abstract L4AddressData destination();
    public abstract List<L4AddressData> mappedAddresses();
    public abstract List<L4AddressData> peerAddresses();
    public abstract List<L4AddressData> relayedAddresses();
    public abstract DateTime firstSeen();
    public abstract DateTime lastActivity();

    public static STUNNegotiationEntry create(String negotiationKey, String negotiationKeySha256, boolean isActive, String transport, boolean successful, boolean isTurn, Long bytesExchanged, L4AddressData source, L4AddressData destination, List<L4AddressData> mappedAddresses, List<L4AddressData> peerAddresses, List<L4AddressData> relayedAddresses, DateTime firstSeen, DateTime lastActivity) {
        return builder()
                .negotiationKey(negotiationKey)
                .negotiationKeySha256(negotiationKeySha256)
                .isActive(isActive)
                .transport(transport)
                .successful(successful)
                .isTurn(isTurn)
                .bytesExchanged(bytesExchanged)
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
        return new AutoValue_STUNNegotiationEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder negotiationKey(String negotiationKey);

        public abstract Builder negotiationKeySha256(String negotiationKeySha256);

        public abstract Builder isActive(boolean isActive);

        public abstract Builder transport(String transport);

        public abstract Builder successful(boolean successful);

        public abstract Builder isTurn(boolean isTurn);

        public abstract Builder bytesExchanged(Long bytesExchanged);

        public abstract Builder source(L4AddressData source);

        public abstract Builder destination(L4AddressData destination);

        public abstract Builder mappedAddresses(List<L4AddressData> mappedAddresses);

        public abstract Builder peerAddresses(List<L4AddressData> peerAddresses);

        public abstract Builder relayedAddresses(List<L4AddressData> relayedAddresses);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastActivity(DateTime lastActivity);

        public abstract STUNNegotiationEntry build();
    }
}