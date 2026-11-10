package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class TopologyRankingResponse {

    @JsonProperty("rows")
    public abstract List<TopologyRankingRowResponse> rows();

    @Schema(description = "Number of rows the ranking has in total.")
    @JsonProperty("total")
    public abstract long total();

    public static TopologyRankingResponse create(List<TopologyRankingRowResponse> rows, long total) {
        return builder()
                .rows(rows)
                .total(total)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyRankingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder rows(List<TopologyRankingRowResponse> rows);
        public abstract Builder total(long total);

        public abstract TopologyRankingResponse build();
    }

}
