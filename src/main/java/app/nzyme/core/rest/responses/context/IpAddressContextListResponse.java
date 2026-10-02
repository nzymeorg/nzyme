package app.nzyme.core.rest.responses.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class IpAddressContextListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("ip_addresses")
    public abstract List<IpAddressContextDetailsResponse> ipAddresses();

    public static IpAddressContextListResponse create(long total, List<IpAddressContextDetailsResponse> ipAddresses) {
        return builder()
                .total(total)
                .ipAddresses(ipAddresses)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IpAddressContextListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder ipAddresses(List<IpAddressContextDetailsResponse> ipAddresses);

        public abstract IpAddressContextListResponse build();
    }
}
