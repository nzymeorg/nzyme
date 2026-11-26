package app.nzyme.core.rest.responses.dot11.signal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class FrameSignalReferenceResponse {

    @JsonProperty("source")
    public abstract String source();

    @JsonProperty("buckets")
    public abstract List<FrameSignalReferenceBucketResponse> buckets();

    public static FrameSignalReferenceResponse create(String source, List<FrameSignalReferenceBucketResponse> buckets) {
        return builder()
                .source(source)
                .buckets(buckets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalReferenceResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder source(String source);

        public abstract Builder buckets(List<FrameSignalReferenceBucketResponse> buckets);

        public abstract FrameSignalReferenceResponse build();
    }

}
