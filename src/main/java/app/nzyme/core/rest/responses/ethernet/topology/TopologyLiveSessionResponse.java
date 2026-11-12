package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class TopologyLiveSessionResponse {

    @Schema(description = "Identifies the session across requests, together with the tap and the transport.")
    @JsonProperty("session_key")
    public abstract String sessionKey();

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @Schema(description = "TCP or UDP.")
    @JsonProperty("l4_type")
    public abstract String l4Type();

    @Schema(description = "ID of the node that initiated the session.")
    @JsonProperty("source")
    public abstract String source();

    @Schema(description = "ID of the node that accepted the session.")
    @JsonProperty("target")
    public abstract String target();

    @JsonProperty("source_address")
    public abstract String sourceAddress();

    @JsonProperty("destination_address")
    public abstract String destinationAddress();

    @JsonProperty("destination_port")
    public abstract int destinationPort();

    @JsonProperty("start_time")
    public abstract DateTime startTime();

    @JsonProperty("most_recent_segment_time")
    public abstract DateTime mostRecentSegmentTime();

    @Schema(description = "Bytes the source sent since the session started.")
    @JsonProperty("bytes_tx")
    public abstract long bytesTx();

    @Schema(description = "Bytes the source received since the session started.")
    @JsonProperty("bytes_rx")
    public abstract long bytesRx();

    @Schema(description = "Coordinates of the external address, rounded to two decimals. Only set for sessions with exactly one internal endpoint, and if GeoIP knows coordinates.")
    @Nullable
    @JsonProperty("latitude")
    public abstract Double latitude();

    @Nullable
    @JsonProperty("longitude")
    public abstract Double longitude();

    @Schema(description = "City of the external address. Only set for sessions with exactly one internal endpoint.")
    @Nullable
    @JsonProperty("city")
    public abstract String city();

    @Schema(description = "Country of the external address. Only set for sessions with exactly one internal endpoint.")
    @Nullable
    @JsonProperty("country_code")
    public abstract String countryCode();

    public static TopologyLiveSessionResponse create(String sessionKey, UUID tapUuid, String l4Type, String source,
                                                     String target, String sourceAddress,
                                                     String destinationAddress, int destinationPort,
                                                     DateTime startTime, DateTime mostRecentSegmentTime,
                                                     long bytesTx, long bytesRx, @Nullable Double latitude,
                                                     @Nullable Double longitude, @Nullable String city,
                                                     @Nullable String countryCode) {
        return builder()
                .sessionKey(sessionKey)
                .tapUuid(tapUuid)
                .l4Type(l4Type)
                .source(source)
                .target(target)
                .sourceAddress(sourceAddress)
                .destinationAddress(destinationAddress)
                .destinationPort(destinationPort)
                .startTime(startTime)
                .mostRecentSegmentTime(mostRecentSegmentTime)
                .bytesTx(bytesTx)
                .bytesRx(bytesRx)
                .latitude(latitude)
                .longitude(longitude)
                .city(city)
                .countryCode(countryCode)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyLiveSessionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sessionKey(String sessionKey);
        public abstract Builder tapUuid(UUID tapUuid);
        public abstract Builder l4Type(String l4Type);
        public abstract Builder source(String source);
        public abstract Builder target(String target);
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder destinationAddress(String destinationAddress);
        public abstract Builder destinationPort(int destinationPort);
        public abstract Builder startTime(DateTime startTime);
        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);
        public abstract Builder bytesTx(long bytesTx);
        public abstract Builder bytesRx(long bytesRx);
        public abstract Builder latitude(Double latitude);
        public abstract Builder longitude(Double longitude);
        public abstract Builder city(String city);
        public abstract Builder countryCode(String countryCode);

        public abstract TopologyLiveSessionResponse build();
    }

}
