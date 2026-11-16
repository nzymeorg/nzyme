package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class FlowsListResponse {

    @Schema(description = "Number of all entries that pass the filters, of which this is one page.")
    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("flows")
    public abstract List<FlowResponse> flows();

    @Schema(description = "Unanswered connections of one source to more than this many ports of one destination "
            + "within one evaluation window are stored as one flow with port -1.")
    @JsonProperty("ports_folded_above")
    public abstract int portsFoldedAbove();

    @Schema(description = "Unanswered connections of one source to more than this many destinations within one "
            + "evaluation window are stored as one flow per destination segment, without an address.")
    @JsonProperty("destinations_folded_above")
    public abstract int destinationsFoldedAbove();

    public static FlowsListResponse create(long total,
                                           List<FlowResponse> flows,
                                           int portsFoldedAbove,
                                           int destinationsFoldedAbove) {
        return builder()
                .total(total)
                .flows(flows)
                .portsFoldedAbove(portsFoldedAbove)
                .destinationsFoldedAbove(destinationsFoldedAbove)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FlowsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder flows(List<FlowResponse> flows);
        public abstract Builder portsFoldedAbove(int portsFoldedAbove);
        public abstract Builder destinationsFoldedAbove(int destinationsFoldedAbove);

        public abstract FlowsListResponse build();
    }

}
