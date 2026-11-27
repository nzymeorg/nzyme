package app.nzyme.core.rest.responses.dot11.monitoring.ssids;

import app.nzyme.core.rest.responses.dot11.Dot11MacAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class HiddenAccessPointDetailsResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("organization_id")
    public abstract UUID organizationId();

    @JsonProperty("tenant_id")
    public abstract UUID tenantId();

    @JsonProperty("bssid")
    public abstract Dot11MacAddressResponse bssid();

    @JsonProperty("is_approved")
    public abstract boolean isApproved();

    @JsonProperty("is_ignored")
    public abstract boolean isIgnored();

    @Schema(description = "Up to 5 SSIDs advertised in the last 24 hours by access points with a neighboring "
            + "address, which are likely the same device. A hint only. Refreshed once per hour.")
    @JsonProperty("sibling_ssids")
    public abstract List<String> siblingSsids();

    @Schema(description = "Up to 5 SSIDs the access point itself sent in the last 24 hours, usually in probe "
            + "responses. A hint only. Refreshed once per hour.")
    @JsonProperty("revealed_ssids")
    public abstract List<String> revealedSsids();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static HiddenAccessPointDetailsResponse create(UUID uuid, UUID organizationId, UUID tenantId, Dot11MacAddressResponse bssid, boolean isApproved, boolean isIgnored, List<String> siblingSsids, List<String> revealedSsids, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .bssid(bssid)
                .isApproved(isApproved)
                .isIgnored(isIgnored)
                .siblingSsids(siblingSsids)
                .revealedSsids(revealedSsids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_HiddenAccessPointDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder bssid(Dot11MacAddressResponse bssid);

        public abstract Builder isApproved(boolean isApproved);

        public abstract Builder isIgnored(boolean isIgnored);

        public abstract Builder siblingSsids(List<String> siblingSsids);

        public abstract Builder revealedSsids(List<String> revealedSsids);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract HiddenAccessPointDetailsResponse build();
    }

}
