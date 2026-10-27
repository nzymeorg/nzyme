package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.UUID;

@AutoValue
public abstract class DnsTunnelExclusionMatchedFindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("client_address")
    public abstract String clientAddress();

    @JsonProperty("domain")
    public abstract String domain();

    public static DnsTunnelExclusionMatchedFindingResponse create(UUID uuid, String clientAddress, String domain) {
        return builder()
                .uuid(uuid)
                .clientAddress(clientAddress)
                .domain(domain)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelExclusionMatchedFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder clientAddress(String clientAddress);
        public abstract Builder domain(String domain);
        public abstract DnsTunnelExclusionMatchedFindingResponse build();
    }
}
