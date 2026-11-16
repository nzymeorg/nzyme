package app.nzyme.core.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

public record Finding(long id,
                      UUID uuid,
                      UUID organizationId,
                      UUID tenantId,
                      long policyId,
                      UUID policyUuid,
                      String policyName,
                      FindingClass findingClass,
                      String sourceKey,
                      @Nullable String sourceAddress,
                      boolean sourceInternal,
                      @Nullable Long sourceAsn,
                      @Nullable String sourceCountryCode,
                      // A sample of the addresses of a folded external source.
                      List<String> sourceAddresses,
                      // The most recent targets. Capped, see targetCount for how many there were.
                      List<Target> targets,
                      long targetCount,
                      long sessionCount,
                      List<UUID> tapUuids,
                      boolean audit,
                      DateTime firstSeen,
                      DateTime lastSeen,
                      DateTime createdAt,
                      DateTime updatedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Target(@JsonProperty("address") String address,
                         @JsonProperty("internal") boolean internal,
                         @JsonProperty("asn") @Nullable Long asn,
                         @JsonProperty("country_code") @Nullable String countryCode,
                         @JsonProperty("l4_type") String l4Type,
                         @JsonProperty("port") int port,
                         @JsonProperty("protocol") @Nullable String protocol,
                         @JsonProperty("outcome") Outcome outcome,
                         @JsonProperty("sessions") long sessions,
                         @JsonProperty("first_seen") long firstSeen,
                         @JsonProperty("last_seen") long lastSeen) {

        public String identity() {
            return address + "|" + l4Type + "|" + port + "|" + (protocol == null ? "" : protocol) + "|" + outcome;
        }

    }

}
