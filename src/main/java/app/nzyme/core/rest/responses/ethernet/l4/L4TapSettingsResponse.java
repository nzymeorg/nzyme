package app.nzyme.core.rest.responses.ethernet.l4;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class L4TapSettingsResponse {

    @JsonProperty("cgnat_is_internal")
    public abstract boolean cgnatIsInternal();

    @JsonProperty("taps_total")
    public abstract long tapsTotal();

    @JsonProperty("taps_different")
    public abstract List<TapSetting> tapsDifferent();

    public static L4TapSettingsResponse create(boolean cgnatIsInternal, long tapsTotal, List<TapSetting> tapsDifferent) {
        return new AutoValue_L4TapSettingsResponse(cgnatIsInternal, tapsTotal, tapsDifferent);
    }

    @AutoValue
    public abstract static class TapSetting {

        @JsonProperty("uuid")
        public abstract UUID uuid();

        @JsonProperty("name")
        public abstract String name();

        @JsonProperty("cgnat_is_internal")
        public abstract boolean cgnatIsInternal();

        public static TapSetting create(UUID uuid, String name, boolean cgnatIsInternal) {
            return new AutoValue_L4TapSettingsResponse_TapSetting(uuid, name, cgnatIsInternal);
        }

    }

}
