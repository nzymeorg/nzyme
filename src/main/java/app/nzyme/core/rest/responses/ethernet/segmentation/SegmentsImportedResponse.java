package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class SegmentsImportedResponse {

    @Schema(description = "Names of the segments that were created.")
    @JsonProperty("created")
    public abstract List<String> created();

    public static SegmentsImportedResponse create(List<String> created) {
        return builder()
                .created(created)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentsImportedResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder created(List<String> created);

        public abstract SegmentsImportedResponse build();
    }

}
