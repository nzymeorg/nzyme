package app.nzyme.core.rest.responses.shared;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class GeoInformationResponse {

    @Nullable
    @JsonProperty("asn_number")
    public abstract Integer asnNumber();

    @Nullable
    @JsonProperty("asn_name")
    public abstract String asnName();

    @Nullable
    @JsonProperty("asn_domain")
    public abstract String asnDomain();

    @Nullable
    @JsonProperty("city")
    public abstract String city();

    @Nullable
    @JsonProperty("country_code")
    public abstract String countryCode();

    @Nullable
    @JsonProperty("country")
    public abstract String country();

    @Nullable
    @JsonProperty("latitude")
    public abstract Float latitude();

    @Nullable
    @JsonProperty("longitude")
    public abstract Float longitude();

    public static GeoInformationResponse create(Integer asnNumber, String asnName, String asnDomain, String city, String countryCode, String country, Float latitude, Float longitude) {
        return builder()
                .asnNumber(asnNumber)
                .asnName(asnName)
                .asnDomain(asnDomain)
                .city(city)
                .countryCode(countryCode)
                .country(country)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_GeoInformationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder asnNumber(Integer asnNumber);

        public abstract Builder asnName(String asnName);

        public abstract Builder asnDomain(String asnDomain);

        public abstract Builder city(String city);

        public abstract Builder countryCode(String countryCode);

        public abstract Builder country(String country);

        public abstract Builder latitude(Float latitude);

        public abstract Builder longitude(Float longitude);

        public abstract GeoInformationResponse build();
    }
}
