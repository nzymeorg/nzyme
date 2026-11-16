package app.nzyme.core.rest.responses.ethernet.segmentation;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class FindingTargetResponse {

    @Nullable
    @JsonProperty("address")
    public abstract L4AddressResponse address();

    @JsonProperty("address_text")
    public abstract String addressText();

    @JsonProperty("internal")
    public abstract boolean internal();

    @Nullable
    @JsonProperty("segment_name")
    public abstract String segmentName();

    @JsonProperty("l4_type")
    public abstract String l4Type();

    @JsonProperty("port")
    public abstract int port();

    @Nullable
    @JsonProperty("protocol")
    public abstract String protocol();

    @JsonProperty("outcome")
    public abstract String outcome();

    @JsonProperty("sessions")
    public abstract long sessions();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static FindingTargetResponse create(L4AddressResponse address,
                                               String addressText,
                                               boolean internal,
                                               String segmentName,
                                               String l4Type,
                                               int port,
                                               String protocol,
                                               String outcome,
                                               long sessions,
                                               DateTime firstSeen,
                                               DateTime lastSeen) {
        return builder()
                .address(address)
                .addressText(addressText)
                .internal(internal)
                .segmentName(segmentName)
                .l4Type(l4Type)
                .port(port)
                .protocol(protocol)
                .outcome(outcome)
                .sessions(sessions)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FindingTargetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(L4AddressResponse address);
        public abstract Builder addressText(String addressText);
        public abstract Builder internal(boolean internal);
        public abstract Builder segmentName(String segmentName);
        public abstract Builder l4Type(String l4Type);
        public abstract Builder port(int port);
        public abstract Builder protocol(String protocol);
        public abstract Builder outcome(String outcome);
        public abstract Builder sessions(long sessions);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract FindingTargetResponse build();
    }

}
