package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetTimelineEventResponse {

    @JsonProperty("type")
    public abstract String type();
    @JsonProperty("timestamp")
    public abstract DateTime timestamp();
    @JsonProperty("value")
    public abstract String value();
    @Nullable
    @JsonProperty("detail")
    public abstract String detail();

    public static AssetTimelineEventResponse create(String type, DateTime timestamp, String value, String detail) {
        return builder()
                .type(type)
                .timestamp(timestamp)
                .value(value)
                .detail(detail)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTimelineEventResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);
        public abstract Builder timestamp(DateTime timestamp);
        public abstract Builder value(String value);
        public abstract Builder detail(String detail);

        public abstract AssetTimelineEventResponse build();
    }

}
