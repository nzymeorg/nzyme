package app.nzyme.core.rest.responses.ethernet.recon;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class ReconProbedHostResponse {

    @JsonProperty("address")
    public abstract String address();

    @Nullable
    @JsonProperty("address_data")
    public abstract L4AddressResponse addressData();

    @JsonProperty("source_count")
    public abstract long sourceCount();

    @JsonProperty("port_count")
    public abstract long portCount();

    @JsonProperty("failed_port_count")
    public abstract long failedPortCount();

    @JsonProperty("pair_count")
    public abstract long pairCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static ReconProbedHostResponse create(String address, L4AddressResponse addressData, long sourceCount, long portCount, long failedPortCount, long pairCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .address(address)
                .addressData(addressData)
                .sourceCount(sourceCount)
                .portCount(portCount)
                .failedPortCount(failedPortCount)
                .pairCount(pairCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconProbedHostResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(String address);
        public abstract Builder addressData(L4AddressResponse addressData);
        public abstract Builder sourceCount(long sourceCount);
        public abstract Builder portCount(long portCount);
        public abstract Builder failedPortCount(long failedPortCount);
        public abstract Builder pairCount(long pairCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract ReconProbedHostResponse build();
    }
}
