package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class FindingsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("findings")
    public abstract List<FindingResponse> findings();

    public static FindingsListResponse create(long total,
                                              List<FindingResponse> findings) {
        return builder()
                .total(total)
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FindingsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder findings(List<FindingResponse> findings);

        public abstract FindingsListResponse build();
    }

}
