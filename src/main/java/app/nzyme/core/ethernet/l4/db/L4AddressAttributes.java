package app.nzyme.core.ethernet.l4.db;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class L4AddressAttributes {

    @JsonProperty("is_site_local")
    public abstract boolean isSiteLocal();
    @JsonProperty("is_loopback")
    public abstract boolean isLoopback();
    @JsonProperty("is_multicast")
    public abstract boolean isMulticast();

    @JsonCreator
    public static L4AddressAttributes create(@JsonProperty("is_site_local") boolean isSiteLocal,
                                             @JsonProperty("is_loopback") boolean isLoopback,
                                             @JsonProperty("is_multicast") boolean isMulticast) {
        return builder()
                .setSiteLocal(isSiteLocal)
                .setLoopback(isLoopback)
                .setMulticast(isMulticast)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4AddressAttributes.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setSiteLocal(boolean newSiteLocal);

        public abstract Builder setLoopback(boolean newLoopback);

        public abstract Builder setMulticast(boolean newMulticast);

        public abstract L4AddressAttributes build();
    }
}