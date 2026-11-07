package app.nzyme.core.rest.responses.ethernet.assets.identity;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.spoofing.SpoofingTapResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class DuplicateMacFindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("mac")
    public abstract String mac();

    @Nullable
    @JsonProperty("mac_data")
    public abstract EthernetMacAddressResponse macData();

    @JsonProperty("stacks")
    public abstract List<DuplicateMacStackResponse> stacks();

    @JsonProperty("stack_count")
    public abstract int stackCount();

    @JsonProperty("session_count")
    public abstract long sessionCount();

    @JsonProperty("recent")
    public abstract boolean recent();

    @JsonProperty("window_minutes")
    public abstract int windowMinutes();

    @JsonProperty("taps")
    public abstract List<SpoofingTapResponse> taps();

    @JsonProperty("exclusions")
    public abstract List<AssetIdentityExclusionReferenceResponse> exclusions();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    public static DuplicateMacFindingResponse create(UUID uuid, String mac, EthernetMacAddressResponse macData, List<DuplicateMacStackResponse> stacks, int stackCount, long sessionCount, boolean recent, int windowMinutes, List<SpoofingTapResponse> taps, List<AssetIdentityExclusionReferenceResponse> exclusions, DateTime firstSeen, DateTime lastSeen, DateTime createdAt) {
        return builder()
                .uuid(uuid)
                .mac(mac)
                .macData(macData)
                .stacks(stacks)
                .stackCount(stackCount)
                .sessionCount(sessionCount)
                .recent(recent)
                .windowMinutes(windowMinutes)
                .taps(taps)
                .exclusions(exclusions)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DuplicateMacFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder mac(String mac);
        public abstract Builder macData(EthernetMacAddressResponse macData);
        public abstract Builder stacks(List<DuplicateMacStackResponse> stacks);
        public abstract Builder stackCount(int stackCount);
        public abstract Builder sessionCount(long sessionCount);
        public abstract Builder recent(boolean recent);
        public abstract Builder windowMinutes(int windowMinutes);
        public abstract Builder taps(List<SpoofingTapResponse> taps);
        public abstract Builder exclusions(List<AssetIdentityExclusionReferenceResponse> exclusions);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract DuplicateMacFindingResponse build();
    }
}
