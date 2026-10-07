package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Body for updating the UAV monitoring configuration of a tenant.")
@AutoValue
public abstract class UavMonitoringConfigurationRequest {

    public abstract boolean alertOnUnknown();
    public abstract boolean alertOnFriendly();
    public abstract boolean alertOnNeutral();
    public abstract boolean alertOnHostile();

    @JsonCreator
    public static UavMonitoringConfigurationRequest create(@Schema(description = "Set to true to raise an alert for UAVs classified as unknown.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("alert_on_unknown") boolean alertOnUnknown,
                                                           @Schema(description = "Set to true to raise an alert for UAVs classified as friendly.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("alert_on_friendly") boolean alertOnFriendly,
                                                           @Schema(description = "Set to true to raise an alert for UAVs classified as neutral.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("alert_on_neutral") boolean alertOnNeutral,
                                                           @Schema(description = "Set to true to raise an alert for UAVs classified as hostile.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("alert_on_hostile") boolean alertOnHostile) {
        return builder()
                .alertOnUnknown(alertOnUnknown)
                .alertOnFriendly(alertOnFriendly)
                .alertOnNeutral(alertOnNeutral)
                .alertOnHostile(alertOnHostile)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UavMonitoringConfigurationRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder alertOnUnknown(boolean alertOnUnknown);

        public abstract Builder alertOnFriendly(boolean alertOnFriendly);

        public abstract Builder alertOnNeutral(boolean alertOnNeutral);

        public abstract Builder alertOnHostile(boolean alertOnHostile);

        public abstract UavMonitoringConfigurationRequest build();
    }
}
