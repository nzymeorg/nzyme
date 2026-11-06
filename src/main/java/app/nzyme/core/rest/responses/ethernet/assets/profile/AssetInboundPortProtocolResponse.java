package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class AssetInboundPortProtocolResponse {

    @JsonProperty("name")
    public abstract String name();
    @JsonProperty("count")
    public abstract long count();

    public static AssetInboundPortProtocolResponse create(String name, long count) {
        return builder()
                .name(name)
                .count(count)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetInboundPortProtocolResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder count(long count);

        public abstract AssetInboundPortProtocolResponse build();
    }

}
