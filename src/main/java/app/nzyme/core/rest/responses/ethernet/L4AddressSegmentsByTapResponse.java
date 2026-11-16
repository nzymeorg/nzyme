package app.nzyme.core.rest.responses.ethernet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@AutoValue
public abstract class L4AddressSegmentsByTapResponse {

    @Schema(description = "The segments the address is in for at least one tap, by name.")
    @JsonProperty("segments")
    public abstract List<L4AddressSegmentResponse> segments();

    @Schema(description = "For at least one tap, the address is in no segment.")
    @JsonProperty("unassigned")
    public abstract boolean unassigned();

    public static L4AddressSegmentsByTapResponse create(List<L4AddressSegmentResponse> segments, boolean unassigned) {
        return builder()
                .segments(segments)
                .unassigned(unassigned)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4AddressSegmentsByTapResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder segments(List<L4AddressSegmentResponse> segments);
        public abstract Builder unassigned(boolean unassigned);

        public abstract L4AddressSegmentsByTapResponse build();
    }

}
