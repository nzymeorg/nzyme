package app.nzyme.core.rest.responses.ethernet.l4;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressTypeResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class L4SessionDetailsResponse {

    @JsonProperty("session_key")
    public abstract String sessionKey();

    @JsonProperty("l4_type")
    public abstract L4AddressTypeResponse l4Type();

    @JsonProperty("source")
    public abstract L4AddressResponse source();

    @JsonProperty("destination")
    public abstract L4AddressResponse destination();

    @JsonProperty("bytes_count")
    public abstract long bytesCount();

    @JsonProperty("bytes_rx_count")
    public abstract long bytesRxCount();

    @JsonProperty("bytes_tx_count")
    public abstract long bytesTxCount();

    @JsonProperty("segments_count")
    public abstract long segmentsCount();

    @JsonProperty("start_time")
    public abstract DateTime startTime();

    @JsonProperty("end_time")
    @Nullable
    public abstract DateTime endTime();

    @JsonProperty("most_recent_segment_time")
    public abstract DateTime mostRecentSegmentTime();

    @JsonProperty("duration_ms")
    public abstract long durationMs();

    @JsonProperty("state")
    public abstract String state();

    @JsonProperty("tags")
    @Nullable
    public abstract List<String> tags();

    @JsonProperty("fingerprint")
    @Nullable
    public abstract String fingerprint();

    @Nullable
    @JsonProperty("fingerprint_id")
    public abstract String fingerprintId();

    public static L4SessionDetailsResponse create(String sessionKey, L4AddressTypeResponse l4Type, L4AddressResponse source, L4AddressResponse destination, long bytesCount, long bytesRxCount, long bytesTxCount, long segmentsCount, DateTime startTime, DateTime endTime, DateTime mostRecentSegmentTime, long durationMs, String state, List<String> tags, String fingerprint, String fingerprintId) {
        return builder()
                .sessionKey(sessionKey)
                .l4Type(l4Type)
                .source(source)
                .destination(destination)
                .bytesCount(bytesCount)
                .bytesRxCount(bytesRxCount)
                .bytesTxCount(bytesTxCount)
                .segmentsCount(segmentsCount)
                .startTime(startTime)
                .endTime(endTime)
                .mostRecentSegmentTime(mostRecentSegmentTime)
                .durationMs(durationMs)
                .state(state)
                .tags(tags)
                .fingerprint(fingerprint)
                .fingerprintId(fingerprintId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4SessionDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sessionKey(String sessionKey);

        public abstract Builder l4Type(L4AddressTypeResponse l4Type);

        public abstract Builder source(L4AddressResponse source);

        public abstract Builder destination(L4AddressResponse destination);

        public abstract Builder bytesCount(long bytesCount);

        public abstract Builder bytesRxCount(long bytesRxCount);

        public abstract Builder bytesTxCount(long bytesTxCount);

        public abstract Builder segmentsCount(long segmentsCount);

        public abstract Builder startTime(DateTime startTime);

        public abstract Builder endTime(DateTime endTime);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract Builder durationMs(long durationMs);

        public abstract Builder state(String state);

        public abstract Builder tags(List<String> tags);

        public abstract Builder fingerprint(String fingerprint);
        public abstract Builder fingerprintId(String fingerprintId);

        public abstract L4SessionDetailsResponse build();
    }
}
