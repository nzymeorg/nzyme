package app.nzyme.core.rest.resources.taps.reports.tables.nat;

import app.nzyme.core.rest.resources.taps.reports.tables.SocketAddressReport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class StunDiscoveryReport {

    public abstract String transport();
    @Nullable
    public abstract String sourceMac();
    public abstract String sourceAddress();
    public abstract int sourcePort();
    public abstract String destinationAddress();
    public abstract int destinationPort();
    public abstract List<SocketAddressReport> mappedAddresses();
    public abstract boolean sawErrorResponse();
    public abstract boolean sawSuccessResponse();
    public abstract DateTime firstSeen();
    public abstract DateTime lastActivity();

    // saw_error_response

    @JsonCreator
    public static StunDiscoveryReport create(@JsonProperty("transport") String transport,
                                             @JsonProperty("source_mac") String sourceMac,
                                             @JsonProperty("source_address") String sourceAddress,
                                             @JsonProperty("source_port") int sourcePort,
                                             @JsonProperty("destination_address") String destinationAddress,
                                             @JsonProperty("destination_port") int destinationPort,
                                             @JsonProperty("mapped_addresses") List<SocketAddressReport> mappedAddresses,
                                             @JsonProperty("saw_error_response") boolean sawErrorResponse,
                                             @JsonProperty("saw_success_response") boolean sawSuccessResponse,
                                             @JsonProperty("first_seen") DateTime firstSeen,
                                             @JsonProperty("last_activity") DateTime lastActivity) {
        return builder()
                .transport(transport)
                .sourceMac(sourceMac)
                .sourceAddress(sourceAddress)
                .sourcePort(sourcePort)
                .destinationAddress(destinationAddress)
                .destinationPort(destinationPort)
                .mappedAddresses(mappedAddresses)
                .sawErrorResponse(sawErrorResponse)
                .sawSuccessResponse(sawSuccessResponse)
                .firstSeen(firstSeen)
                .lastActivity(lastActivity)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_StunDiscoveryReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder transport(String transport);

        public abstract Builder sourceMac(String sourceMac);

        public abstract Builder sourceAddress(String sourceAddress);

        public abstract Builder sourcePort(int sourcePort);

        public abstract Builder destinationAddress(String destinationAddress);

        public abstract Builder destinationPort(int destinationPort);

        public abstract Builder mappedAddresses(List<SocketAddressReport> mappedAddresses);

        public abstract Builder sawErrorResponse(boolean sawErrorResponse);

        public abstract Builder sawSuccessResponse(boolean sawSuccessResponse);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastActivity(DateTime lastActivity);

        public abstract StunDiscoveryReport build();
    }
}
