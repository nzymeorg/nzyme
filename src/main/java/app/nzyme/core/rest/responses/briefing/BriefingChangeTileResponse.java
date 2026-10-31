package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;

@AutoValue
public abstract class BriefingChangeTileResponse {

    @JsonProperty("key")
    public abstract String key();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("current")
    public abstract long current();

    @JsonProperty("previous")
    @Nullable
    public abstract Long previous();

    @JsonProperty("unit")
    @Nullable
    public abstract String unit();

    @JsonProperty("higher_is_worse")
    @Nullable
    public abstract Boolean higherIsWorse();

    @JsonProperty("link")
    @Nullable
    public abstract BriefingLinkResponse link();

    @JsonProperty("samples")
    public abstract List<BriefingSampleResponse> samples();

    @JsonProperty("breakdown")
    @Nullable
    public abstract List<BriefingBreakdownEntryResponse> breakdown();

    public static BriefingChangeTileResponse create(String key, String title, long current, Long previous, String unit, Boolean higherIsWorse, BriefingLinkResponse link, List<BriefingSampleResponse> samples, List<BriefingBreakdownEntryResponse> breakdown) {
        return builder()
                .key(key)
                .title(title)
                .current(current)
                .previous(previous)
                .unit(unit)
                .higherIsWorse(higherIsWorse)
                .link(link)
                .samples(samples)
                .breakdown(breakdown)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingChangeTileResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder key(String key);

        public abstract Builder title(String title);

        public abstract Builder current(long current);

        public abstract Builder previous(Long previous);

        public abstract Builder unit(String unit);

        public abstract Builder higherIsWorse(Boolean higherIsWorse);

        public abstract Builder link(BriefingLinkResponse link);

        public abstract Builder samples(List<BriefingSampleResponse> samples);

        public abstract Builder breakdown(List<BriefingBreakdownEntryResponse> breakdown);

        public abstract BriefingChangeTileResponse build();
    }
}
