package app.nzyme.core.rest.responses.ethernet.assets.profile;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import javax.annotation.Nullable;

@AutoValue
public abstract class AssetAttributeResponse {

    @JsonProperty("dimension")
    public abstract String dimension();
    @JsonProperty("value")
    public abstract String value();
    @JsonProperty("count")
    public abstract long count();
    @JsonProperty("bytes")
    public abstract long bytes();
    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();
    @Nullable
    @JsonProperty("l4_address")
    public abstract L4AddressResponse l4Address();
    @Nullable
    @JsonProperty("mac_address")
    public abstract EthernetMacAddressResponse macAddress();

    public static AssetAttributeResponse create(String dimension, String value, long count, long bytes, DateTime firstSeen, DateTime lastSeen, L4AddressResponse l4Address, EthernetMacAddressResponse macAddress) {
        return builder()
                .dimension(dimension)
                .value(value)
                .count(count)
                .bytes(bytes)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .l4Address(l4Address)
                .macAddress(macAddress)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetAttributeResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder dimension(String dimension);
        public abstract Builder value(String value);
        public abstract Builder count(long count);

        public abstract Builder bytes(long bytes);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder l4Address(L4AddressResponse l4Address);

        public abstract Builder macAddress(EthernetMacAddressResponse macAddress);

        public abstract AssetAttributeResponse build();
    }

}
