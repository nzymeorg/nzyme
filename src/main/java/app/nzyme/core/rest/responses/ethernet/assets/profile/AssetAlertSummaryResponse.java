package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetAlertSummaryResponse {

    @JsonProperty("detection_type")
    public abstract String detectionType();
    @Nullable
    @JsonProperty("detection_type_description")
    public abstract String detectionTypeDescription();
    @JsonProperty("role")
    public abstract String role();
    @JsonProperty("count")
    public abstract long count();
    @JsonProperty("active_count")
    public abstract long activeCount();
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static AssetAlertSummaryResponse create(String detectionType, String detectionTypeDescription, String role, long count, long activeCount, DateTime lastSeen) {
        return builder()
                .detectionType(detectionType)
                .detectionTypeDescription(detectionTypeDescription)
                .role(role)
                .count(count)
                .activeCount(activeCount)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetAlertSummaryResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder detectionType(String detectionType);
        public abstract Builder detectionTypeDescription(String detectionTypeDescription);
        public abstract Builder role(String role);
        public abstract Builder count(long count);
        public abstract Builder activeCount(long activeCount);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract AssetAlertSummaryResponse build();
    }

}
