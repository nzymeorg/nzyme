package app.nzyme.core.ethernet.dns.db;

import app.nzyme.core.ethernet.l4.db.L4AddressData;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class DNSLogEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID tapUUID();
    public abstract int transactionId();
    public abstract L4AddressData client();
    public abstract L4AddressData server();
    @Nullable
    public abstract String dataValue();
    @Nullable
    public abstract String dataValueEtld();
    public abstract String dataType();
    public abstract String dnsType();

    @Nullable
    public abstract String responseCode();
    public abstract DateTime timestamp();
    public abstract DateTime createdAt();

    public static DNSLogEntry create(long id, UUID uuid, UUID tapUUID, int transactionId, L4AddressData client, L4AddressData server, String dataValue, String dataValueEtld, String dataType, String dnsType, String responseCode, DateTime timestamp, DateTime createdAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .tapUUID(tapUUID)
                .transactionId(transactionId)
                .client(client)
                .server(server)
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
        return new AutoValue_DNSLogEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder uuid(UUID uuid);

        public abstract Builder tapUUID(UUID tapUUID);

        public abstract Builder transactionId(int transactionId);

        public abstract Builder client(L4AddressData client);

        public abstract Builder server(L4AddressData server);

        public abstract Builder dataValue(String dataValue);

        public abstract Builder dataValueEtld(String dataValueEtld);

        public abstract Builder dataType(String dataType);

        public abstract Builder dnsType(String dnsType);

        public abstract Builder responseCode(String responseCode);

        public abstract Builder timestamp(DateTime timestamp);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract DNSLogEntry build();
    }
}
