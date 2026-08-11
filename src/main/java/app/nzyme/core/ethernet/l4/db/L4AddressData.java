package app.nzyme.core.ethernet.l4.db;

import app.nzyme.core.ethernet.GeoData;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class L4AddressData {

    @Nullable // Some higher-level protocols don't record a MAC.
    @JsonProperty("mac")
    public abstract String mac();

    @JsonProperty("address")
    public abstract String address();

    @Nullable
    @JsonProperty("port")
    public abstract Integer port();

    @Nullable
    @JsonProperty("geo")
    public abstract GeoData geo();

    @Nullable
    @JsonProperty("attributes")
    public abstract L4AddressAttributes attributes();

    @JsonCreator
    public static L4AddressData create(@JsonProperty("mac") String mac,
                                       @JsonProperty("address") String address,
                                       @JsonProperty("port") Integer port,
                                       @JsonProperty("geo") GeoData geo,
                                       @JsonProperty("attributes") L4AddressAttributes attributes) {
        return builder()
                .mac(mac)
                .address(address)
                .port(port)
                .geo(geo)
                .attributes(attributes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4AddressData.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(String mac);

        public abstract Builder address(String address);

        public abstract Builder port(Integer port);

        public abstract Builder geo(GeoData geo);

        public abstract Builder attributes(L4AddressAttributes attributes);

        public abstract L4AddressData build();
    }
}