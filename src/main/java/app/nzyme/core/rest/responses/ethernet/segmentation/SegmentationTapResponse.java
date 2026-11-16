package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.UUID;

@AutoValue
public abstract class SegmentationTapResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("location_uuid")
    public abstract UUID locationUuid();

    @Nullable
    @JsonProperty("location_name")
    public abstract String locationName();

    public static SegmentationTapResponse create(UUID uuid,
                                                 String name,
                                                 UUID locationUuid,
                                                 String locationName) {
        return builder()
                .uuid(uuid)
                .name(name)
                .locationUuid(locationUuid)
                .locationName(locationName)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentationTapResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder locationUuid(UUID locationUuid);
        public abstract Builder locationName(String locationName);

        public abstract SegmentationTapResponse build();
    }

}
