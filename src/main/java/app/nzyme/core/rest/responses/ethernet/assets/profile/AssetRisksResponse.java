package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.Map;

@AutoValue
public abstract class AssetRisksResponse {

    @JsonProperty("signals")
    public abstract List<AssetRiskSignalResponse> signals();
    @JsonProperty("tls_sessions")
    public abstract long tlsSessions();
    @JsonProperty("tls_versions")
    public abstract Map<String, Long> tlsVersions();

    public static AssetRisksResponse create(List<AssetRiskSignalResponse> signals, long tlsSessions, Map<String, Long> tlsVersions) {
        return builder()
                .signals(signals)
                .tlsSessions(tlsSessions)
                .tlsVersions(tlsVersions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetRisksResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder signals(List<AssetRiskSignalResponse> signals);

        public abstract Builder tlsSessions(long tlsSessions);

        public abstract Builder tlsVersions(Map<String, Long> tlsVersions);

        public abstract AssetRisksResponse build();
    }

}
