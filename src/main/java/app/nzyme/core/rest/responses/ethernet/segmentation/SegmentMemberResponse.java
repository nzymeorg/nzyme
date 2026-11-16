package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class SegmentMemberResponse {

    @Schema(description = "CIDR for a network, ASN for an autonomous system.")
    @JsonProperty("type")
    public abstract String type();

    @Schema(description = "Network in CIDR notation. Set for members of type CIDR.")
    @Nullable
    @JsonProperty("network")
    public abstract String network();

    @Schema(description = "Autonomous system number. Set for members of type ASN.")
    @Nullable
    @JsonProperty("asn")
    public abstract Long asn();

    public static SegmentMemberResponse create(String type,
                                               String network,
                                               Long asn) {
        return builder()
                .type(type)
                .network(network)
                .asn(asn)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentMemberResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);
        public abstract Builder network(String network);
        public abstract Builder asn(Long asn);

        public abstract SegmentMemberResponse build();
    }

}
