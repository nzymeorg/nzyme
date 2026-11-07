package app.nzyme.core.rest.responses.ethernet.assets.identity;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;
import org.joda.time.DateTime;

@AutoValue
public abstract class DuplicateMacStackResponse {

    @JsonProperty("fingerprint")
    public abstract String fingerprint();

    @JsonProperty("fingerprint_id")
    public abstract String fingerprintId();

    @JsonProperty("signatures")
    public abstract List<String> signatures();

    @JsonProperty("sessions")
    public abstract long sessions();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @JsonProperty("source_addresses")
    public abstract List<L4AddressResponse> sourceAddresses();

    @JsonProperty("destinations")
    public abstract List<DuplicateMacDestinationResponse> destinations();

    @JsonProperty("destination_count")
    public abstract long destinationCount();

    @JsonProperty("source_port_min")
    public abstract int sourcePortMin();

    @JsonProperty("source_port_max")
    public abstract int sourcePortMax();

    @JsonProperty("taps")
    public abstract List<String> taps();

    public static DuplicateMacStackResponse create(String fingerprint, String fingerprintId, List<String> signatures, long sessions, DateTime firstSeen, DateTime lastSeen, List<L4AddressResponse> sourceAddresses, List<DuplicateMacDestinationResponse> destinations, long destinationCount, int sourcePortMin, int sourcePortMax, List<String> taps) {
        return builder()
                .fingerprint(fingerprint)
                .fingerprintId(fingerprintId)
                .signatures(signatures)
                .sessions(sessions)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .sourceAddresses(sourceAddresses)
                .destinations(destinations)
                .destinationCount(destinationCount)
                .sourcePortMin(sourcePortMin)
                .sourcePortMax(sourcePortMax)
                .taps(taps)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DuplicateMacStackResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder fingerprint(String fingerprint);
        public abstract Builder fingerprintId(String fingerprintId);
        public abstract Builder signatures(List<String> signatures);
        public abstract Builder sessions(long sessions);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder sourceAddresses(List<L4AddressResponse> sourceAddresses);
        public abstract Builder destinations(List<DuplicateMacDestinationResponse> destinations);
        public abstract Builder destinationCount(long destinationCount);
        public abstract Builder sourcePortMin(int sourcePortMin);
        public abstract Builder sourcePortMax(int sourcePortMax);
        public abstract Builder taps(List<String> taps);
        public abstract DuplicateMacStackResponse build();
    }
}
