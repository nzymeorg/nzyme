package app.nzyme.core.rest.responses.ethernet.l4;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class L4TapSettingsResponse {

    @JsonProperty("cgnat_is_internal")
    public abstract boolean cgnatIsInternal();

    @JsonProperty("internal_networks")
    public abstract List<String> internalNetworks();

    @JsonProperty("taps_total")
    public abstract long tapsTotal();

    @JsonProperty("taps_different")
    public abstract List<TapSetting> tapsDifferent();

    public static L4TapSettingsResponse create(boolean cgnatIsInternal,
                                               List<String> internalNetworks,
                                               long tapsTotal,
                                               List<TapSetting> tapsDifferent) {
        return new AutoValue_L4TapSettingsResponse(cgnatIsInternal, internalNetworks, tapsTotal, tapsDifferent);
    }

    @AutoValue
    public abstract static class TapSetting {

        @JsonProperty("uuid")
        public abstract UUID uuid();

        @JsonProperty("name")
        public abstract String name();

        @JsonProperty("cgnat_is_internal")
        public abstract boolean cgnatIsInternal();

        @JsonProperty("cgnat_is_internal_differs")
        public abstract boolean cgnatIsInternalDiffers();

        @JsonProperty("internal_networks")
        public abstract List<String> internalNetworks();

        @JsonProperty("internal_networks_differ")
        public abstract boolean internalNetworksDiffer();

        public static TapSetting create(UUID uuid,
                                        String name,
                                        boolean cgnatIsInternal,
                                        boolean cgnatIsInternalDiffers,
                                        List<String> internalNetworks,
                                        boolean internalNetworksDiffer) {
            return new AutoValue_L4TapSettingsResponse_TapSetting(
                    uuid, name, cgnatIsInternal, cgnatIsInternalDiffers, internalNetworks, internalNetworksDiffer
            );
        }

    }

}
