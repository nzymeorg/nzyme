package app.nzyme.core.rest.responses.ethernet.assets;

import app.nzyme.core.rest.responses.context.NetworkContextDetailsResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class AssetIpAddressDetailsResponse {

    @JsonProperty("id")
    public abstract UUID id();
    @JsonProperty("address")
    public abstract String address();
    @JsonProperty("source")
    public abstract String source();
    @JsonProperty("context")
    public abstract List<NetworkContextDetailsResponse> context();
    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static AssetIpAddressDetailsResponse create(UUID id, String address, String source, List<NetworkContextDetailsResponse> context, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .id(id)
                .address(address)
                .source(source)
                .context(context)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetIpAddressDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(UUID id);

        public abstract Builder address(String address);

        public abstract Builder source(String source);

        public abstract Builder context(List<NetworkContextDetailsResponse> context);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract AssetIpAddressDetailsResponse build();
    }
}
