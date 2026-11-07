package app.nzyme.core.rest.responses.ethernet.assets.identity;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class IdentityChangeFindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("mac")
    public abstract String mac();

    @Nullable
    @JsonProperty("mac_data")
    public abstract EthernetMacAddressResponse macData();

    @JsonProperty("score")
    public abstract int score();

    @JsonProperty("threshold")
    public abstract int threshold();

    @JsonProperty("changes")
    public abstract List<IdentityChangeResponse> changes();

    @Nullable
    @JsonProperty("previous_fingerprint")
    public abstract String previousFingerprint();

    @Nullable
    @JsonProperty("new_fingerprint")
    public abstract String newFingerprint();

    @JsonProperty("recent")
    public abstract boolean recent();

    @JsonProperty("exclusions")
    public abstract List<AssetIdentityExclusionReferenceResponse> exclusions();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    public static IdentityChangeFindingResponse create(UUID uuid, String mac, EthernetMacAddressResponse macData, int score, int threshold, List<IdentityChangeResponse> changes, String previousFingerprint, String newFingerprint, boolean recent, List<AssetIdentityExclusionReferenceResponse> exclusions, DateTime firstSeen, DateTime lastSeen, DateTime createdAt) {
        return builder()
                .uuid(uuid)
                .mac(mac)
                .macData(macData)
                .score(score)
                .threshold(threshold)
                .changes(changes)
                .previousFingerprint(previousFingerprint)
                .newFingerprint(newFingerprint)
                .recent(recent)
                .exclusions(exclusions)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IdentityChangeFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder mac(String mac);
        public abstract Builder macData(EthernetMacAddressResponse macData);
        public abstract Builder score(int score);
        public abstract Builder threshold(int threshold);
        public abstract Builder changes(List<IdentityChangeResponse> changes);
        public abstract Builder previousFingerprint(String previousFingerprint);
        public abstract Builder newFingerprint(String newFingerprint);
        public abstract Builder recent(boolean recent);
        public abstract Builder exclusions(List<AssetIdentityExclusionReferenceResponse> exclusions);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract IdentityChangeFindingResponse build();
    }
}
