package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class AssetInboundPortsResponse {

    // Totals cover every port of the asset, not only the page.
    @JsonProperty("total")
    public abstract long total();
    @JsonProperty("total_sessions")
    public abstract long totalSessions();
    @JsonProperty("total_bytes")
    public abstract long totalBytes();
    @JsonProperty("max_sessions")
    public abstract long maxSessions();
    @JsonProperty("max_bytes")
    public abstract long maxBytes();
    @JsonProperty("ports")
    public abstract List<AssetInboundPortResponse> ports();

    public static AssetInboundPortsResponse create(long total, long totalSessions, long totalBytes, long maxSessions, long maxBytes, List<AssetInboundPortResponse> ports) {
        return builder()
                .total(total)
                .totalSessions(totalSessions)
                .totalBytes(totalBytes)
                .maxSessions(maxSessions)
                .maxBytes(maxBytes)
                .ports(ports)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetInboundPortsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder totalSessions(long totalSessions);
        public abstract Builder totalBytes(long totalBytes);
        public abstract Builder maxSessions(long maxSessions);
        public abstract Builder maxBytes(long maxBytes);
        public abstract Builder ports(List<AssetInboundPortResponse> ports);

        public abstract AssetInboundPortsResponse build();
    }

}
