package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class SegmentsListResponse {

    @Schema(description = "Number of all entries that pass the filters, of which this is one page.")
    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("segments")
    public abstract List<SegmentResponse> segments();

    public static SegmentsListResponse create(long total, List<SegmentResponse> segments) {
        return builder()
                .total(total)
                .segments(segments)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder segments(List<SegmentResponse> segments);

        public abstract SegmentsListResponse build();
    }

}
