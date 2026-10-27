package app.nzyme.core.rest.responses.ethernet.dns;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class DNSLogDataResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("tap_uuid")
    public abstract UUID tapUUID();

    @JsonProperty("transaction_id")
    public abstract int transactionId();

    @JsonProperty("client")
    public abstract L4AddressResponse clientAddress();

    @JsonProperty("server")
    public abstract L4AddressResponse serverAddress();

    @Nullable
    @JsonProperty("data_value")
    public abstract String dataValue();

    @Nullable
    @JsonProperty("data_value_etld")
    public abstract String dataValueEtld();

    @JsonProperty("data_type")
    public abstract String dataType();

    @JsonProperty("dns_type")
    public abstract String dnsType();

    @Nullable
    @JsonProperty("response_code")
    public abstract String responseCode();

    @JsonProperty("timestamp")
    public abstract DateTime timestamp();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    public static DNSLogDataResponse create(UUID uuid, UUID tapUUID, int transactionId, L4AddressResponse clientAddress, L4AddressResponse serverAddress, String dataValue, String dataValueEtld, String dataType, String dnsType, String responseCode, DateTime timestamp, DateTime createdAt) {
        return builder()
                .uuid(uuid)
                .tapUUID(tapUUID)
                .transactionId(transactionId)
                .clientAddress(clientAddress)
                .serverAddress(serverAddress)
                .dataValue(dataValue)
                .dataValueEtld(dataValueEtld)
                .dataType(dataType)
                .dnsType(dnsType)
                .responseCode(responseCode)
                .timestamp(timestamp)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DNSLogDataResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder tapUUID(UUID tapUUID);

        public abstract Builder transactionId(int transactionId);

        public abstract Builder clientAddress(L4AddressResponse clientAddress);

        public abstract Builder serverAddress(L4AddressResponse serverAddress);

        public abstract Builder dataValue(String dataValue);

        public abstract Builder dataValueEtld(String dataValueEtld);

        public abstract Builder dataType(String dataType);

        public abstract Builder dnsType(String dnsType);

        public abstract Builder responseCode(String responseCode);

        public abstract Builder timestamp(DateTime timestamp);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract DNSLogDataResponse build();
    }
}
