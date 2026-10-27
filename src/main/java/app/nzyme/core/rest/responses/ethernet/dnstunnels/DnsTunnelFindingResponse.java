package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@AutoValue
public abstract class DnsTunnelFindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("client_address")
    public abstract String clientAddress();

    @Nullable
    @JsonProperty("client_address_data")
    public abstract L4AddressResponse clientAddressData();

    @Nullable
    @JsonProperty("client_mac")
    public abstract String clientMac();

    @Nullable
    @JsonProperty("client_mac_data")
    public abstract EthernetMacAddressResponse clientMacData();

    @JsonProperty("domain")
    public abstract String domain();

    @JsonProperty("server_addresses")
    public abstract List<String> serverAddresses();

    @JsonProperty("indicators")
    public abstract List<String> indicators();

    @JsonProperty("query_count")
    public abstract long queryCount();

    @JsonProperty("distinct_name_count")
    public abstract long distinctNameCount();

    @JsonProperty("negative_count")
    public abstract long negativeCount();

    @JsonProperty("average_entropy")
    public abstract double averageEntropy();

    @JsonProperty("maximum_entropy")
    public abstract double maximumEntropy();

    @JsonProperty("average_name_length")
    public abstract double averageNameLength();

    @JsonProperty("maximum_name_length")
    public abstract int maximumNameLength();

    @JsonProperty("record_types")
    public abstract Map<String, Long> recordTypes();

    @JsonProperty("names")
    public abstract List<String> names();

    @JsonProperty("name_count")
    public abstract int nameCount();

    @JsonProperty("recent")
    public abstract boolean recent();

    @JsonProperty("window_minutes")
    public abstract int windowMinutes();

    @JsonProperty("taps")
    public abstract List<DnsTunnelTapResponse> taps();

    @JsonProperty("exclusions")
    public abstract List<DnsTunnelExclusionReferenceResponse> exclusions();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static DnsTunnelFindingResponse create(UUID uuid, String clientAddress, L4AddressResponse clientAddressData, String clientMac, EthernetMacAddressResponse clientMacData, String domain, List<String> serverAddresses, List<String> indicators, long queryCount, long distinctNameCount, long negativeCount, double averageEntropy, double maximumEntropy, double averageNameLength, int maximumNameLength, Map<String, Long> recordTypes, List<String> names, int nameCount, boolean recent, int windowMinutes, List<DnsTunnelTapResponse> taps, List<DnsTunnelExclusionReferenceResponse> exclusions, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .clientAddress(clientAddress)
                .clientAddressData(clientAddressData)
                .clientMac(clientMac)
                .clientMacData(clientMacData)
                .domain(domain)
                .serverAddresses(serverAddresses)
                .indicators(indicators)
                .queryCount(queryCount)
                .distinctNameCount(distinctNameCount)
                .negativeCount(negativeCount)
                .averageEntropy(averageEntropy)
                .maximumEntropy(maximumEntropy)
                .averageNameLength(averageNameLength)
                .maximumNameLength(maximumNameLength)
                .recordTypes(recordTypes)
                .names(names)
                .nameCount(nameCount)
                .recent(recent)
                .windowMinutes(windowMinutes)
                .taps(taps)
                .exclusions(exclusions)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder clientAddress(String clientAddress);
        public abstract Builder clientAddressData(L4AddressResponse clientAddressData);
        public abstract Builder clientMac(String clientMac);
        public abstract Builder clientMacData(EthernetMacAddressResponse clientMacData);
        public abstract Builder domain(String domain);
        public abstract Builder serverAddresses(List<String> serverAddresses);
        public abstract Builder indicators(List<String> indicators);
        public abstract Builder queryCount(long queryCount);
        public abstract Builder distinctNameCount(long distinctNameCount);
        public abstract Builder negativeCount(long negativeCount);
        public abstract Builder averageEntropy(double averageEntropy);
        public abstract Builder maximumEntropy(double maximumEntropy);
        public abstract Builder averageNameLength(double averageNameLength);
        public abstract Builder maximumNameLength(int maximumNameLength);
        public abstract Builder recordTypes(Map<String, Long> recordTypes);
        public abstract Builder names(List<String> names);
        public abstract Builder nameCount(int nameCount);
        public abstract Builder recent(boolean recent);
        public abstract Builder windowMinutes(int windowMinutes);
        public abstract Builder taps(List<DnsTunnelTapResponse> taps);
        public abstract Builder exclusions(List<DnsTunnelExclusionReferenceResponse> exclusions);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract DnsTunnelFindingResponse build();
    }
}
