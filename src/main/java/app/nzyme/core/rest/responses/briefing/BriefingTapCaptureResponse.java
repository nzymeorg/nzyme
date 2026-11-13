package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@AutoValue
public abstract class BriefingTapCaptureResponse {

    @JsonProperty("interface_name")
    public abstract String interfaceName();

    @JsonProperty("capture_type")
    public abstract String captureType();

    @JsonProperty("is_running")
    public abstract boolean isRunning();

    @Schema(description = "Packets or frames received since the tap started.")
    @JsonProperty("received")
    public abstract long received();

    @Schema(description = "Packets or frames dropped in the buffer and on the interface during the last reporting interval of the tap. Not a total, so it cannot be compared to the received count.")
    @JsonProperty("dropped")
    public abstract long dropped();

    @Schema(description = "True if the capture dropped enough during the last reporting interval to turn the tap drop health indicator red.")
    @JsonProperty("dropping")
    public abstract boolean dropping();

    public static BriefingTapCaptureResponse create(String interfaceName, String captureType, boolean isRunning, long received, long dropped, boolean dropping) {
        return builder()
                .interfaceName(interfaceName)
                .captureType(captureType)
                .isRunning(isRunning)
                .received(received)
                .dropped(dropped)
                .dropping(dropping)
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

        public abstract Builder dropping(boolean dropping);

        public abstract BriefingTapCaptureResponse build();
    }
}
