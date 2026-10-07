package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Body for updating the signal track detector configuration of a WiFi network.")
@AutoValue
public abstract class UpdateTrackDetectorConfigurationRequest {

    public abstract UUID tapId();
    public abstract long frameThreshold();
    public abstract long gapThreshold();
    public abstract long signalCenterlineJitter();

    @JsonCreator
    public static UpdateTrackDetectorConfigurationRequest create(@Schema(description = "UUID of the tap this configuration applies to.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                 @JsonProperty("tap_id") @NotNull UUID tapId,
                                                                 @Schema(description = "Minimum number of minutes with recorded frames required to start a new track.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                 @JsonProperty("frame_threshold") long frameThreshold,
                                                                 @Schema(description = "Maximum number of minutes without recorded frames allowed before a track ends.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                 @JsonProperty("gap_threshold")  long gapThreshold,
                                                                 @Schema(description = "Maximum deviation from the track centerline in dBm that still counts as part of the " + "track.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                 @JsonProperty("signal_centerline_jitter") long signalCenterlineJitter) {
        return builder()
                .tapId(tapId)
                .frameThreshold(frameThreshold)
                .gapThreshold(gapThreshold)
                .signalCenterlineJitter(signalCenterlineJitter)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateTrackDetectorConfigurationRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tapId(UUID tapId);

        public abstract Builder frameThreshold(long frameThreshold);

        public abstract Builder gapThreshold(long gapThreshold);

        public abstract Builder signalCenterlineJitter(long signalCenterlineJitter);

        public abstract UpdateTrackDetectorConfigurationRequest build();
    }

}
