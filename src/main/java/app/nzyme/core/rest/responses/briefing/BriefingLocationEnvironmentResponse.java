package app.nzyme.core.rest.responses.briefing;

import app.nzyme.core.rest.responses.locations.LocationEnvironmentConditionDetailsResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.List;

@AutoValue
public abstract class BriefingLocationEnvironmentResponse {

    @JsonProperty("condition")
    @Nullable
    public abstract LocationEnvironmentConditionDetailsResponse condition();

    @JsonProperty("temperature")
    @Nullable
    public abstract Integer temperature();

    @JsonProperty("wind_speed")
    @Nullable
    public abstract Integer windSpeed();

    @JsonProperty("wind_gust")
    @Nullable
    public abstract Integer windGust();

    @JsonProperty("alerts")
    public abstract List<String> alerts();

    public static BriefingLocationEnvironmentResponse create(LocationEnvironmentConditionDetailsResponse condition, Integer temperature, Integer windSpeed, Integer windGust, List<String> alerts) {
        return builder()
                .condition(condition)
                .temperature(temperature)
                .windSpeed(windSpeed)
                .windGust(windGust)
                .alerts(alerts)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingLocationEnvironmentResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder condition(LocationEnvironmentConditionDetailsResponse condition);

        public abstract Builder temperature(Integer temperature);

        public abstract Builder windSpeed(Integer windSpeed);

        public abstract Builder windGust(Integer windGust);

        public abstract Builder alerts(List<String> alerts);

        public abstract BriefingLocationEnvironmentResponse build();
    }
}
