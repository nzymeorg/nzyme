package app.nzyme.core.ethernet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class GeoData {

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
    @JsonProperty("latitude")
    public abstract Float latitude();
    @Nullable
    @JsonProperty("longitude")
    public abstract Float longitude();

    @JsonCreator
    public static GeoData create(@JsonProperty("asn_number") Integer asnNumber,
                                 @JsonProperty("asn_name") String asnName,
                                 @JsonProperty("asn_domain") String asnDomain,
                                 @JsonProperty("city") String city,
                                 @JsonProperty("country_code") String countryCode,
                                 @JsonProperty("latitude") Float latitude,
                                 @JsonProperty("longitude") Float longitude) {
        return builder()
                .asnNumber(asnNumber)
                .asnName(asnName)
                .asnDomain(asnDomain)
                .city(city)
                .countryCode(countryCode)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_GeoData.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder asnNumber(Integer asnNumber);

        public abstract Builder asnName(String asnName);

        public abstract Builder asnDomain(String asnDomain);

        public abstract Builder city(String city);

        public abstract Builder countryCode(String countryCode);

        public abstract Builder latitude(Float latitude);

        public abstract Builder longitude(Float longitude);

        public abstract GeoData build();
    }
}