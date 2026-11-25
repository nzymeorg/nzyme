package app.nzyme.core.rest.responses.ethernet.ndp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NdpStatisticsBucketResponse {

    @JsonProperty("neighbor_solicitations")
    public abstract long neighborSolicitations();

    @JsonProperty("neighbor_advertisements")
    public abstract long neighborAdvertisements();

    @JsonProperty("router_solicitations")
    public abstract long routerSolicitations();

    public static NdpStatisticsBucketResponse create(long neighborSolicitations,
                                                     long neighborAdvertisements,
                                                     long routerSolicitations) {
        return builder()
                .neighborSolicitations(neighborSolicitations)
                .neighborAdvertisements(neighborAdvertisements)
                .routerSolicitations(routerSolicitations)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NdpStatisticsBucketResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder neighborSolicitations(long neighborSolicitations);

        public abstract Builder neighborAdvertisements(long neighborAdvertisements);

        public abstract Builder routerSolicitations(long routerSolicitations);

        public abstract NdpStatisticsBucketResponse build();
    }

}
