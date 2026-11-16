package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class SegmentRequest {

    @Nullable
    public abstract String kind();

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract String notes();

    @Nullable
    public abstract List<UUID> tapUuids();

    @Nullable
    public abstract List<SegmentMemberRequest> members();

    @JsonCreator
    public static SegmentRequest create(@Schema(description = "INTERNAL or EXTERNAL. Only used when creating.") @JsonProperty("kind") String kind,
                                        @Schema(description = "Name of up to 12 characters.") @JsonProperty("name") String name,
                                        @Schema(description = "Description of up to 32 characters.") @JsonProperty("description") String description,
                                        @JsonProperty("notes") String notes,
                                        @Schema(description = "Limits the segment to what these taps record. Leave out to apply it to all taps.") @JsonProperty("tap_uuids") List<UUID> tapUuids,
                                        @JsonProperty("members") List<SegmentMemberRequest> members) {
        return builder()
                .kind(kind)
                .name(name)
                .description(description)
                .notes(notes)
                .tapUuids(tapUuids)
                .members(members)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder kind(String kind);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder notes(String notes);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder members(List<SegmentMemberRequest> members);

        public abstract SegmentRequest build();
    }

}
