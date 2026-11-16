package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class SegmentExclusionRequest {

    @Nullable
    public abstract String destination();

    @Nullable
    public abstract String note();

    @JsonCreator
    public static SegmentExclusionRequest create(@Schema(description = "Internal address, or network in CIDR notation that is no larger than a /24 or, for IPv6, a /64.") @JsonProperty("destination") String destination,
                                                 @Schema(description = "Why the destination is excluded. Required, 500 characters at most.") @JsonProperty("note") String note) {
        return builder()
                .destination(destination)
                .note(note)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentExclusionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder destination(String destination);
        public abstract Builder note(String note);

        public abstract SegmentExclusionRequest build();
    }

}
