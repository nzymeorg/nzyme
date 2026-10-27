package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DnsTunnelFindingsDeletedResponse {

    @JsonProperty("deleted")
    public abstract int deleted();

    public static DnsTunnelFindingsDeletedResponse create(int deleted) {
        return builder()
                .deleted(deleted)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelFindingsDeletedResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder deleted(int deleted);
        public abstract DnsTunnelFindingsDeletedResponse build();
    }
}
