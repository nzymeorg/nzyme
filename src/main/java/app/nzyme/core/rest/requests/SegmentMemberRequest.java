package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class SegmentMemberRequest {

    @Nullable
    public abstract String type();

    @Nullable
    public abstract String network();

    @Nullable
    public abstract Long asn();

    @JsonCreator
    public static SegmentMemberRequest create(@Schema(description = "CIDR or ASN.") @JsonProperty("type") String type,
                                              @Schema(description = "Network in CIDR notation or a single address, for members of type CIDR.") @JsonProperty("network") String network,
                                              @Schema(description = "Autonomous system number, for members of type ASN.") @JsonProperty("asn") Long asn) {
        return builder()
                .type(type)
                .network(network)
                .asn(asn)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentMemberRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);
        public abstract Builder network(String network);
        public abstract Builder asn(Long asn);

        public abstract SegmentMemberRequest build();
    }

}
