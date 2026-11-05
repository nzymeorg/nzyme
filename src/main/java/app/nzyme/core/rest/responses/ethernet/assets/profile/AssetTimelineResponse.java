package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class AssetTimelineResponse {

    @JsonProperty("total")
    public abstract long total();
    @JsonProperty("events")
    public abstract List<AssetTimelineEventResponse> events();
    @JsonProperty("attribute_retention_days")
    public abstract int attributeRetentionDays();

    public static AssetTimelineResponse create(long total, List<AssetTimelineEventResponse> events, int attributeRetentionDays) {
        return builder()
                .total(total)
                .events(events)
                .attributeRetentionDays(attributeRetentionDays)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTimelineResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder events(List<AssetTimelineEventResponse> events);
        public abstract Builder attributeRetentionDays(int attributeRetentionDays);

        public abstract AssetTimelineResponse build();
    }

}
