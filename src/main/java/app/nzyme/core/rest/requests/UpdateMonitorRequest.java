package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;


@Schema(description = "Body for updating a monitor.")
@AutoValue
public abstract class UpdateMonitorRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract Integer triggerCondition();

    @Nullable
    public abstract Integer interval();

    @Nullable
    public abstract Integer lookback();

    @Nullable
    public abstract String filters();

    @Nullable
    public abstract List<String> taps();

    @JsonCreator
    public static UpdateMonitorRequest create(@Schema(description = "New name of the monitor.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("name") String name,
                                              @Schema(description = "New description of the monitor.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("description") String description,
                                              @Schema(description = "Number of matching results that triggers the monitor. Must be 0 or higher.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("trigger_condition") Integer triggerCondition,
                                              @Schema(description = "How often the monitor runs, in minutes. Must be 1 or higher.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("interval") Integer interval,
                                              @Schema(description = "How far back the monitor looks on each run, in minutes. Must be 1 or higher.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("lookback") Integer lookback,
                                              @Schema(description = "JSON encoded filter definition as produced by the web interface filter builder.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("filters") String filters,
                                              @Schema(description = "Tap UUIDs the monitor runs against.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("taps") List<String> taps) {
        return builder()
                .name(name)
                .description(description)
                .triggerCondition(triggerCondition)
                .interval(interval)
                .lookback(lookback)
                .filters(filters)
                .taps(taps)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateMonitorRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(@NotEmpty String name);

        public abstract Builder description(String description);

        public abstract Builder triggerCondition(@Min(0) Integer triggerCondition);

        public abstract Builder interval(@Min(1) Integer interval);

        public abstract Builder lookback(@Min(1) Integer lookback);

        public abstract Builder filters(String filters);

        public abstract Builder taps(List<String> taps);

        public abstract UpdateMonitorRequest build();
    }
}
