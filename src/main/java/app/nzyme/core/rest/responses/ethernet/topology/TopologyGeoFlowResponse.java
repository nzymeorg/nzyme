package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class TopologyGeoFlowResponse {

    @Schema(description = "ID of the site the observing tap belongs to. Not set if the tap has no known position.")
    @Nullable
    @JsonProperty("site")
    public abstract String site();

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @Schema(description = "OUTBOUND if the internal side initiated the sessions, INBOUND if the external side did.")
    @JsonProperty("direction")
    public abstract String direction();

    @Schema(description = "Coordinates of the external addresses, rounded to two decimals. Not set if GeoIP knows no coordinates.")
    @Nullable
    @JsonProperty("latitude")
    public abstract Double latitude();

    @Nullable
    @JsonProperty("longitude")
    public abstract Double longitude();

    @Nullable
    @JsonProperty("city")
    public abstract String city();

    @Nullable
    @JsonProperty("country_code")
    public abstract String countryCode();

    @JsonProperty("sessions")
    public abstract long sessions();

    @Schema(description = "Bytes the internal side sent.")
    @JsonProperty("bytes_tx")
    public abstract long bytesTx();

    @Schema(description = "Bytes the internal side received.")
    @JsonProperty("bytes_rx")
    public abstract long bytesRx();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @Schema(description = "The flow over time, one entry per bucket with traffic: bucket index, sessions, bytes sent, bytes received.")
    @JsonProperty("series")
    public abstract List<long[]> series();

    public static TopologyGeoFlowResponse create(@Nullable String site, UUID tapUuid, String direction,
                                                 @Nullable Double latitude, @Nullable Double longitude,
                                                 @Nullable String city, @Nullable String countryCode, long sessions,
                                                 long bytesTx, long bytesRx, DateTime firstSeen, DateTime lastSeen,
                                                 List<long[]> series) {
        return builder()
                .site(site)
                .tapUuid(tapUuid)
                .direction(direction)
                .latitude(latitude)
                .longitude(longitude)
                .city(city)
                .countryCode(countryCode)
                .sessions(sessions)
                .bytesTx(bytesTx)
                .bytesRx(bytesRx)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .series(series)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyGeoFlowResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder site(String site);
        public abstract Builder tapUuid(UUID tapUuid);
        public abstract Builder direction(String direction);
        public abstract Builder latitude(Double latitude);
        public abstract Builder longitude(Double longitude);
        public abstract Builder city(String city);
        public abstract Builder countryCode(String countryCode);
        public abstract Builder sessions(long sessions);
        public abstract Builder bytesTx(long bytesTx);
        public abstract Builder bytesRx(long bytesRx);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder series(List<long[]> series);

        public abstract TopologyGeoFlowResponse build();
    }

}
