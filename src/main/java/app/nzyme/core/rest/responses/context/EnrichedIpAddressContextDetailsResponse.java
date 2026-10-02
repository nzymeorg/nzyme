package app.nzyme.core.rest.responses.context;

import app.nzyme.core.rest.responses.ethernet.assets.AssetDetailsResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.util.List;

@AutoValue
public abstract class EnrichedIpAddressContextDetailsResponse {

    @JsonProperty("context")
    @Nullable
    public abstract IpAddressContextDetailsResponse context();

    @JsonProperty("assets")
    public abstract List<AssetDetailsResponse> assets();

    public static EnrichedIpAddressContextDetailsResponse create(IpAddressContextDetailsResponse context, List<AssetDetailsResponse> assets) {
        return builder()
                .context(context)
                .assets(assets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_EnrichedIpAddressContextDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder context(IpAddressContextDetailsResponse context);

        public abstract Builder assets(List<AssetDetailsResponse> assets);

        public abstract EnrichedIpAddressContextDetailsResponse build();
    }
}
