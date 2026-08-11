package app.nzyme.core.rest.responses.ethernet.nat;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class NATTraversalDiscoveryDetailsResponse {

    @JsonProperty("session_key")
    public abstract String sessionKey();
    @JsonProperty("transport")
    public abstract String transport();
    @JsonProperty("mapped_addresses")
    public abstract List<L4AddressResponse> mappedAddresses();
    @JsonProperty("most_recent_segment_time")
    public abstract DateTime mostRecentSegmentTime();
    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();
    @Nullable @JsonProperty("terminated_at")
    public abstract DateTime terminatedAt();
    @Nullable @JsonProperty("source")
    public abstract L4AddressResponse source();
    @Nullable @JsonProperty("destination")
    public abstract L4AddressResponse destination();

    public static NATTraversalDiscoveryDetailsResponse create(String sessionKey, String transport, List<L4AddressResponse> mappedAddresses, DateTime mostRecentSegmentTime, DateTime firstSeen, DateTime terminatedAt, L4AddressResponse source, L4AddressResponse destination) {
        return builder()
                .sessionKey(sessionKey)
                .transport(transport)
                .mappedAddresses(mappedAddresses)
                .mostRecentSegmentTime(mostRecentSegmentTime)
                .firstSeen(firstSeen)
                .terminatedAt(terminatedAt)
                .source(source)
                .destination(destination)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NATTraversalDiscoveryDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sessionKey(String sessionKey);

        public abstract Builder transport(String transport);

        public abstract Builder mappedAddresses(List<L4AddressResponse> mappedAddresses);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder terminatedAt(DateTime terminatedAt);

        public abstract Builder source(L4AddressResponse source);

        public abstract Builder destination(L4AddressResponse destination);

        public abstract NATTraversalDiscoveryDetailsResponse build();
    }
}
