package app.nzyme.core.assets.identity.db;

import com.fasterxml.jackson.annotation.JsonProperty;

// Stored as JSON on the finding.
public record DuplicateMacDestination(@JsonProperty("address") String address,
                                      @JsonProperty("port") int port,
                                      @JsonProperty("sessions") long sessions,
                                      @JsonProperty("bytes") long bytes) {
}
