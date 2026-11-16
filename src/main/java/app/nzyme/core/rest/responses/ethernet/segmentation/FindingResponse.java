package app.nzyme.core.rest.responses.ethernet.segmentation;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class FindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("policy_uuid")
    public abstract UUID policyUuid();

    @JsonProperty("policy_name")
    public abstract String policyName();

    @Schema(description = "CROSSED if connections were answered, ATTEMPTED if they were rejected or got no reply.")
    @JsonProperty("finding_class")
    public abstract String findingClass();

    @JsonProperty("source_key")
    public abstract String sourceKey();

    @JsonProperty("source_label")
    public abstract String sourceLabel();

    @Schema(description = "Set for internal sources. External sources are folded per network.")
    @Nullable
    @JsonProperty("source_address")
    public abstract L4AddressResponse sourceAddress();

    @JsonProperty("source_internal")
    public abstract boolean sourceInternal();

    @Nullable
    @JsonProperty("source_segment_name")
    public abstract String sourceSegmentName();

    @Nullable
    @JsonProperty("source_asn")
    public abstract Long sourceAsn();

    @Nullable
    @JsonProperty("source_country_code")
    public abstract String sourceCountryCode();

    @Schema(description = "A sample of the addresses of a folded external source.")
    @JsonProperty("source_addresses")
    public abstract List<String> sourceAddresses();

    @Schema(description = "The most recent targets.")
    @JsonProperty("targets")
    public abstract List<FindingTargetResponse> targets();

    @JsonProperty("target_count")
    public abstract long targetCount();

    @Schema(description = "At most this many targets are kept per finding, the most recent ones.")
    @JsonProperty("targets_limit")
    public abstract int targetsLimit();

    @Schema(description = "At most this many addresses of a folded external source are kept.")
    @JsonProperty("source_addresses_limit")
    public abstract int sourceAddressesLimit();

    @JsonProperty("session_count")
    public abstract long sessionCount();

    @JsonProperty("tap_uuids")
    public abstract List<UUID> tapUuids();

    @Schema(description = "The policy was in audit when the finding was last seen. Such a finding raised no alert.")
    @JsonProperty("audit")
    public abstract boolean audit();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static FindingResponse create(UUID uuid,
                                         UUID policyUuid,
                                         String policyName,
                                         String findingClass,
                                         String sourceKey,
                                         String sourceLabel,
                                         L4AddressResponse sourceAddress,
                                         boolean sourceInternal,
                                         String sourceSegmentName,
                                         Long sourceAsn,
                                         String sourceCountryCode,
                                         List<String> sourceAddresses,
                                         List<FindingTargetResponse> targets,
                                         long targetCount,
                                         int targetsLimit,
                                         int sourceAddressesLimit,
                                         long sessionCount,
                                         List<UUID> tapUuids,
                                         boolean audit,
                                         DateTime firstSeen,
                                         DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .policyUuid(policyUuid)
                .policyName(policyName)
                .findingClass(findingClass)
                .sourceKey(sourceKey)
                .sourceLabel(sourceLabel)
                .sourceAddress(sourceAddress)
                .sourceInternal(sourceInternal)
                .sourceSegmentName(sourceSegmentName)
                .sourceAsn(sourceAsn)
                .sourceCountryCode(sourceCountryCode)
                .sourceAddresses(sourceAddresses)
                .targets(targets)
                .targetCount(targetCount)
                .targetsLimit(targetsLimit)
                .sourceAddressesLimit(sourceAddressesLimit)
                .sessionCount(sessionCount)
                .tapUuids(tapUuids)
                .audit(audit)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder policyUuid(UUID policyUuid);
        public abstract Builder policyName(String policyName);
        public abstract Builder findingClass(String findingClass);
        public abstract Builder sourceKey(String sourceKey);
        public abstract Builder sourceLabel(String sourceLabel);
        public abstract Builder sourceAddress(L4AddressResponse sourceAddress);
        public abstract Builder sourceInternal(boolean sourceInternal);
        public abstract Builder sourceSegmentName(String sourceSegmentName);
        public abstract Builder sourceAsn(Long sourceAsn);
        public abstract Builder sourceCountryCode(String sourceCountryCode);
        public abstract Builder sourceAddresses(List<String> sourceAddresses);
        public abstract Builder targets(List<FindingTargetResponse> targets);
        public abstract Builder targetCount(long targetCount);
        public abstract Builder targetsLimit(int targetsLimit);
        public abstract Builder sourceAddressesLimit(int sourceAddressesLimit);
        public abstract Builder sessionCount(long sessionCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder audit(boolean audit);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract FindingResponse build();
    }

}
