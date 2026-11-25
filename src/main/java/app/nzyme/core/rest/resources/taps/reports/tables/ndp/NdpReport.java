package app.nzyme.core.rest.resources.taps.reports.tables.ndp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NdpReport {

    public abstract List<NdpMessageReport> messages();

    public abstract List<NdpRouterAdvertisementReport> routerAdvertisements();

    @JsonCreator
    public static NdpReport create(@JsonProperty("messages") List<NdpMessageReport> messages,
                                   @JsonProperty("router_advertisements") List<NdpRouterAdvertisementReport> routerAdvertisements) {
        return builder()
                .messages(messages)
                .routerAdvertisements(routerAdvertisements)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NdpReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder messages(List<NdpMessageReport> messages);

        public abstract Builder routerAdvertisements(List<NdpRouterAdvertisementReport> routerAdvertisements);

        public abstract NdpReport build();
    }

}
