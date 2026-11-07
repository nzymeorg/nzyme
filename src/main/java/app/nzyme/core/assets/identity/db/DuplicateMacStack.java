package app.nzyme.core.assets.identity.db;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Stored as JSON on the finding.
public record DuplicateMacStack(@JsonProperty("fingerprint") String fingerprint,
                                @JsonProperty("signatures") List<String> signatures,
                                @JsonProperty("sessions") long sessions,
                                @JsonProperty("first_seen") String firstSeen,
                                @JsonProperty("last_seen") String lastSeen,
                                @JsonProperty("source_addresses") List<String> sourceAddresses,
                                @JsonProperty("destinations") List<DuplicateMacDestination> destinations,
                                @JsonProperty("destination_count") long destinationCount,
                                @JsonProperty("source_port_min") int sourcePortMin,
                                @JsonProperty("source_port_max") int sourcePortMax,
                                @JsonProperty("taps") List<String> taps) {

    @JsonCreator
    public DuplicateMacStack { }

}
