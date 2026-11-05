package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.UUID;
import javax.annotation.Nullable;

@AutoValue
public abstract class AssetCommunicationOriginResponse {

    @JsonProperty("latitude")
    public abstract double latitude();
    @JsonProperty("longitude")
    public abstract double longitude();
    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();
    @JsonProperty("tap_name")
    public abstract String tapName();
    @Nullable
    @JsonProperty("location_name")
    public abstract String locationName();

    public static AssetCommunicationOriginResponse create(double latitude, double longitude, UUID tapUuid, String tapName, String locationName) {
        return builder()
                .latitude(latitude)
                .longitude(longitude)
                .tapUuid(tapUuid)
                .tapName(tapName)
                .locationName(locationName)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetCommunicationOriginResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder latitude(double latitude);
        public abstract Builder longitude(double longitude);
        public abstract Builder tapUuid(UUID tapUuid);
        public abstract Builder tapName(String tapName);
        public abstract Builder locationName(String locationName);

        public abstract AssetCommunicationOriginResponse build();
    }

}
