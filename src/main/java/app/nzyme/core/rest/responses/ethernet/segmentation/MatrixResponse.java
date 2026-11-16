package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class MatrixResponse {

    @JsonProperty("groups")
    public abstract List<MatrixGroupResponse> groups();

    @JsonProperty("cells")
    public abstract List<MatrixCellResponse> cells();

    @JsonProperty("taps")
    public abstract List<SegmentationTapResponse> taps();

    @Schema(description = "The policies that cells refer to as having them in scope.")
    @JsonProperty("policies")
    public abstract List<PolicyReferenceResponse> policies();

    @Schema(description = "Segments were changed and the stored flows are not matched against them yet.")
    @JsonProperty("restamp_pending")
    public abstract boolean restampPending();

    @Schema(description = "Findings are counted per cell from at most this many findings, the most recent ones.")
    @JsonProperty("findings_limit")
    public abstract int findingsLimit();

    @Schema(description = "The time range has more findings than findings_limit, so finding counts of cells can "
            + "be too low.")
    @JsonProperty("findings_limited")
    public abstract boolean findingsLimited();

    public static MatrixResponse create(List<MatrixGroupResponse> groups,
                                        List<MatrixCellResponse> cells,
                                        List<SegmentationTapResponse> taps,
                                        List<PolicyReferenceResponse> policies,
                                        boolean restampPending,
                                        int findingsLimit,
                                        boolean findingsLimited) {
        return builder()
                .groups(groups)
                .cells(cells)
                .taps(taps)
                .policies(policies)
                .restampPending(restampPending)
                .findingsLimit(findingsLimit)
                .findingsLimited(findingsLimited)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MatrixResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder groups(List<MatrixGroupResponse> groups);
        public abstract Builder cells(List<MatrixCellResponse> cells);
        public abstract Builder taps(List<SegmentationTapResponse> taps);
        public abstract Builder policies(List<PolicyReferenceResponse> policies);
        public abstract Builder restampPending(boolean restampPending);
        public abstract Builder findingsLimit(int findingsLimit);
        public abstract Builder findingsLimited(boolean findingsLimited);

        public abstract MatrixResponse build();
    }

}
