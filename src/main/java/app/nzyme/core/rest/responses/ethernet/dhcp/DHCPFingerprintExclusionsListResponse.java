package app.nzyme.core.rest.responses.ethernet.dhcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DHCPFingerprintExclusionsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("exclusions")
    public abstract List<DHCPFingerprintExclusionResponse> exclusions();

    public static DHCPFingerprintExclusionsListResponse create(long total, List<DHCPFingerprintExclusionResponse> exclusions) {
        return builder()
                .total(total)
                .exclusions(exclusions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DHCPFingerprintExclusionsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder exclusions(List<DHCPFingerprintExclusionResponse> exclusions);

        public abstract DHCPFingerprintExclusionsListResponse build();
    }
}
