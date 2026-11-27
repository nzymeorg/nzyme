package app.nzyme.core.rest.responses.dot11;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@AutoValue
public abstract class AccessPointSiblingsResponse {

    @JsonProperty("siblings")
    public abstract List<AccessPointSiblingResponse> siblings();

    @Schema(description = "True if more than 50 siblings were recorded. The list then holds the 50 seen last.")
    @JsonProperty("limited")
    public abstract boolean limited();

    public static AccessPointSiblingsResponse create(List<AccessPointSiblingResponse> siblings, boolean limited) {
        return builder()
                .siblings(siblings)
                .limited(limited)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AccessPointSiblingsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder siblings(List<AccessPointSiblingResponse> siblings);

        public abstract Builder limited(boolean limited);

        public abstract AccessPointSiblingsResponse build();
    }

}
