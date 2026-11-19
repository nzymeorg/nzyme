package app.nzyme.core.rest.responses.locations;

import app.nzyme.core.rest.responses.taps.TapHighLevelInformationDetailsResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.List;

@AutoValue
public abstract class NoLocationSummaryResponse {

    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("tap_count")
    public abstract int tapCount();

    @Nullable
    @JsonProperty("alert_count")
    public abstract Long alertCount();

    @JsonProperty("taps")
    public abstract List<TapHighLevelInformationDetailsResponse> taps();

    public static NoLocationSummaryResponse create(String name, int tapCount, @Nullable Long alertCount, List<TapHighLevelInformationDetailsResponse> taps) {
        return builder()
                .name(name)
                .tapCount(tapCount)
                .alertCount(alertCount)
                .taps(taps)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NoLocationSummaryResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder tapCount(int tapCount);

        public abstract Builder alertCount(@Nullable Long alertCount);

        public abstract Builder taps(List<TapHighLevelInformationDetailsResponse> taps);

        public abstract NoLocationSummaryResponse build();
    }

}
