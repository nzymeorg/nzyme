package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class AssetInboundPortResponse {

    @JsonProperty("value")
    public abstract String value();
    @JsonProperty("transport")
    public abstract String transport();
    @JsonProperty("port")
    public abstract int port();
    @JsonProperty("count")
    public abstract long count();
    @JsonProperty("bytes")
    public abstract long bytes();
    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();
    @JsonProperty("protocols")
    public abstract List<AssetInboundPortProtocolResponse> protocols();

    public static AssetInboundPortResponse create(String value, String transport, int port, long count, long bytes, DateTime firstSeen, DateTime lastSeen, List<AssetInboundPortProtocolResponse> protocols) {
        return builder()
                .value(value)
                .transport(transport)
                .port(port)
                .count(count)
                .bytes(bytes)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .protocols(protocols)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetInboundPortResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder value(String value);
        public abstract Builder transport(String transport);
        public abstract Builder port(int port);
        public abstract Builder count(long count);
        public abstract Builder bytes(long bytes);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder protocols(List<AssetInboundPortProtocolResponse> protocols);

        public abstract AssetInboundPortResponse build();
    }

}
