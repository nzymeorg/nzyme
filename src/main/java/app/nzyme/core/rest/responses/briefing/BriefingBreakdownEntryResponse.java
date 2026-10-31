package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class BriefingBreakdownEntryResponse {

    @JsonProperty("label")
    public abstract String label();

    @JsonProperty("count")
    public abstract long count();

    public static BriefingBreakdownEntryResponse create(String label, long count) {
        return builder()
                .label(label)
                .count(count)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingBreakdownEntryResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder label(String label);

        public abstract Builder count(long count);

        public abstract BriefingBreakdownEntryResponse build();
    }
}
