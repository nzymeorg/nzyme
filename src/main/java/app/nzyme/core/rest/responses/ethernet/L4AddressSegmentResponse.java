package app.nzyme.core.rest.responses.ethernet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.UUID;

@AutoValue
public abstract class L4AddressSegmentResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @Schema(description = "INTERNAL for a segment of the own network, EXTERNAL for an external network.")
    @JsonProperty("kind")
    public abstract String kind();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @Schema(description = "If the segment has notes. The notes come with the segment, not with every address.")
    @JsonProperty("has_notes")
    public abstract boolean hasNotes();

    public static L4AddressSegmentResponse create(UUID uuid,
                                                  String name,
                                                  String kind,
                                                  String description,
                                                  boolean hasNotes) {
        return builder()
                .uuid(uuid)
                .name(name)
                .kind(kind)
                .description(description)
                .hasNotes(hasNotes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4AddressSegmentResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder kind(String kind);
        public abstract Builder description(String description);
        public abstract Builder hasNotes(boolean hasNotes);

        public abstract L4AddressSegmentResponse build();
    }

}
