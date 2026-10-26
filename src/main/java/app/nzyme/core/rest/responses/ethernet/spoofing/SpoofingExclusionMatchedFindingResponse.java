package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.UUID;

@AutoValue
public abstract class SpoofingExclusionMatchedFindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("type")
    public abstract String type();

    @JsonProperty("address")
    public abstract String address();

    @JsonProperty("mac")
    public abstract String mac();

    public static SpoofingExclusionMatchedFindingResponse create(UUID uuid, String type, String address, String mac) {
        return builder()
                .uuid(uuid)
                .type(type)
                .address(address)
                .mac(mac)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingExclusionMatchedFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder type(String type);
        public abstract Builder address(String address);
        public abstract Builder mac(String mac);
        public abstract SpoofingExclusionMatchedFindingResponse build();
    }
}
