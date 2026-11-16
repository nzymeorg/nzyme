package app.nzyme.core.rest.responses.ethernet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

@AutoValue
public abstract class L4AddressContextResponse {

    @JsonProperty("networks")
    public abstract List<L4AddressNetworkContextResponse> networks();

    @Schema(description = "The segment or external network the address is in. Not set if it is in none, or if that "
            + "depends on the tap that recorded the address.")
    @Nullable
    @JsonProperty("segment")
    public abstract L4AddressSegmentResponse segment();

    @Schema(description = "Only set if segments are limited to taps and the taps of the tenant do not agree on the "
            + "segment of the address. The address is shown without knowing which tap recorded it, so no single "
            + "segment can be named.")
    @Nullable
    @JsonProperty("segment_by_tap")
    public abstract L4AddressSegmentsByTapResponse segmentByTap();

    public static L4AddressContextResponse create(List<L4AddressNetworkContextResponse> networks) {
        return create(networks, null, null);
    }

    public static L4AddressContextResponse create(List<L4AddressNetworkContextResponse> networks,
                                                  @Nullable L4AddressSegmentResponse segment,
                                                  @Nullable L4AddressSegmentsByTapResponse segmentByTap) {
        return builder()
                .networks(networks)
                .segment(segment)
                .segmentByTap(segmentByTap)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4AddressContextResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder networks(List<L4AddressNetworkContextResponse> networks);
        public abstract Builder segment(L4AddressSegmentResponse segment);
        public abstract Builder segmentByTap(L4AddressSegmentsByTapResponse segmentByTap);

        public abstract L4AddressContextResponse build();
    }
}
