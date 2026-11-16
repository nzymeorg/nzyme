package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class SegmentResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @Schema(description = "INTERNAL for a part of the own network, EXTERNAL for a named network outside of it.")
    @JsonProperty("kind")
    public abstract String kind();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @Nullable
    @JsonProperty("notes")
    public abstract String notes();

    @Schema(description = "The taps the segment is limited to, without those the calling user has no access "
            + "to. Not set if it applies to what any tap records.")
    @Nullable
    @JsonProperty("tap_uuids")
    public abstract List<UUID> tapUuids();

    @Schema(description = "Number of taps the segment is limited to that the calling user has no access to.")
    @JsonProperty("inaccessible_taps")
    public abstract int inaccessibleTaps();

    @JsonProperty("members")
    public abstract List<SegmentMemberResponse> members();

    @Schema(description = "The policies that refer to the segment.")
    @JsonProperty("policies")
    public abstract List<PolicyReferenceResponse> policies();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    public static SegmentResponse create(UUID uuid,
                                         String kind,
                                         String name,
                                         String description,
                                         String notes,
                                         List<UUID> tapUuids,
                                         int inaccessibleTaps,
                                         List<SegmentMemberResponse> members,
                                         List<PolicyReferenceResponse> policies,
                                         DateTime createdAt,
                                         DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .kind(kind)
                .name(name)
                .description(description)
                .notes(notes)
                .tapUuids(tapUuids)
                .inaccessibleTaps(inaccessibleTaps)
                .members(members)
                .policies(policies)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder kind(String kind);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder notes(String notes);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder inaccessibleTaps(int inaccessibleTaps);
        public abstract Builder members(List<SegmentMemberResponse> members);
        public abstract Builder policies(List<PolicyReferenceResponse> policies);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract SegmentResponse build();
    }

}
