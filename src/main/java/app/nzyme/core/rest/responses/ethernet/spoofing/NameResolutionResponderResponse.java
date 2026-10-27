package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class NameResolutionResponderResponse {

    @JsonProperty("mac")
    public abstract String mac();

    @Nullable
    @JsonProperty("mac_data")
    public abstract EthernetMacAddressResponse macData();

    @JsonProperty("addresses")
    public abstract List<String> addresses();

    @JsonProperty("protocols")
    public abstract List<String> protocols();

    @JsonProperty("name_count")
    public abstract long nameCount();

    @JsonProperty("self_pointing_name_count")
    public abstract long selfPointingNameCount();

    @JsonProperty("response_count")
    public abstract long responseCount();

    @JsonProperty("unicast_count")
    public abstract long unicastCount();

    @JsonProperty("names_sample")
    public abstract List<String> namesSample();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static NameResolutionResponderResponse create(String mac, EthernetMacAddressResponse macData, List<String> addresses, List<String> protocols, long nameCount, long selfPointingNameCount, long responseCount, long unicastCount, List<String> namesSample, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .mac(mac)
                .macData(macData)
                .addresses(addresses)
                .protocols(protocols)
                .nameCount(nameCount)
                .selfPointingNameCount(selfPointingNameCount)
                .responseCount(responseCount)
                .unicastCount(unicastCount)
                .namesSample(namesSample)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionResponderResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(String mac);
        public abstract Builder macData(EthernetMacAddressResponse macData);
        public abstract Builder addresses(List<String> addresses);
        public abstract Builder protocols(List<String> protocols);
        public abstract Builder nameCount(long nameCount);
        public abstract Builder selfPointingNameCount(long selfPointingNameCount);
        public abstract Builder responseCount(long responseCount);
        public abstract Builder unicastCount(long unicastCount);
        public abstract Builder namesSample(List<String> namesSample);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract NameResolutionResponderResponse build();
    }
}
