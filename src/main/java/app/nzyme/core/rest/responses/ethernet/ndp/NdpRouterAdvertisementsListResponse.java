package app.nzyme.core.rest.responses.ethernet.ndp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NdpRouterAdvertisementsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("router_advertisements")
    public abstract List<NdpRouterAdvertisementResponse> routerAdvertisements();

    public static NdpRouterAdvertisementsListResponse create(long total,
                                                             List<NdpRouterAdvertisementResponse> routerAdvertisements) {
        return builder()
                .total(total)
                .routerAdvertisements(routerAdvertisements)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NdpRouterAdvertisementsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder routerAdvertisements(List<NdpRouterAdvertisementResponse> routerAdvertisements);

        public abstract NdpRouterAdvertisementsListResponse build();
    }

}
