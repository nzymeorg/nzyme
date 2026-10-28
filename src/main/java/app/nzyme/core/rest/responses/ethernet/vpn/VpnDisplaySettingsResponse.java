package app.nzyme.core.rest.responses.ethernet.vpn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class VpnDisplaySettingsResponse {

    @JsonProperty("exclude_tailscale_by_default")
    public abstract boolean excludeTailscaleByDefault();

    public static VpnDisplaySettingsResponse create(boolean excludeTailscaleByDefault) {
        return builder()
                .excludeTailscaleByDefault(excludeTailscaleByDefault)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_VpnDisplaySettingsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder excludeTailscaleByDefault(boolean excludeTailscaleByDefault);

        public abstract VpnDisplaySettingsResponse build();
    }
}
