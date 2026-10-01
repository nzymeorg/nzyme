package app.nzyme.core.rest.responses.ethernet.ipaddresses;

import app.nzyme.core.rest.responses.ethernet.assets.AssetsListResponse;
import app.nzyme.core.rest.responses.shared.GeoInformationResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;


@AutoValue
public abstract class IPAddressDetailsResponse {

    @JsonProperty("address")
    public abstract String address();

    @JsonProperty("assets")
    public abstract AssetsListResponse assets();

    @Nullable @JsonProperty("geo")
    public abstract GeoInformationResponse geo();

    public static IPAddressDetailsResponse create(String address, AssetsListResponse assets, GeoInformationResponse geo) {
        return builder()
                .address(address)
                .assets(assets)
                .geo(geo)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IPAddressDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(String address);

        public abstract Builder assets(AssetsListResponse assets);

        public abstract Builder geo(GeoInformationResponse geo);

        public abstract IPAddressDetailsResponse build();
    }
}
