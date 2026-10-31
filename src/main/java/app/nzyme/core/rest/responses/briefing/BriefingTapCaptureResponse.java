package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class BriefingTapCaptureResponse {

    @JsonProperty("interface_name")
    public abstract String interfaceName();

    @JsonProperty("capture_type")
    public abstract String captureType();

    @JsonProperty("is_running")
    public abstract boolean isRunning();

    @JsonProperty("received")
    public abstract long received();

    @JsonProperty("dropped")
    public abstract long dropped();

    @JsonProperty("drop_ratio")
    public abstract double dropRatio();

    public static BriefingTapCaptureResponse create(String interfaceName, String captureType, boolean isRunning, long received, long dropped, double dropRatio) {
        return builder()
                .interfaceName(interfaceName)
                .captureType(captureType)
                .isRunning(isRunning)
                .received(received)
                .dropped(dropped)
                .dropRatio(dropRatio)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingTapCaptureResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder interfaceName(String interfaceName);

        public abstract Builder captureType(String captureType);

        public abstract Builder isRunning(boolean isRunning);

        public abstract Builder received(long received);

        public abstract Builder dropped(long dropped);

        public abstract Builder dropRatio(double dropRatio);

        public abstract BriefingTapCaptureResponse build();
    }
}
