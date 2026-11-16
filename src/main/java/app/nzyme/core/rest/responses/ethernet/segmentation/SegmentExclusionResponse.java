package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class SegmentExclusionResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @Schema(description = "The excluded destination in CIDR notation. A single address has a /32 or, for IPv6, a /128 prefix.")
    @JsonProperty("destination")
    public abstract String destination();

    @JsonProperty("note")
    public abstract String note();

    @Schema(description = "Number of sessions that were not recorded because of the exclusion. Where several taps "
            + "recorded an attempt, the tap that saw the most counts. Not set, like the time of the last match, for a "
            + "caller without access to every tap that records the segment.")
    @Nullable
    @JsonProperty("match_count")
    public abstract Long matchCount();

    @Nullable
    @JsonProperty("last_matched_at")
    public abstract DateTime lastMatchedAt();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    public static SegmentExclusionResponse create(UUID uuid,
                                                  String destination,
                                                  String note,
                                                  Long matchCount,
                                                  DateTime lastMatchedAt,
                                                  DateTime createdAt) {
        return builder()
                .uuid(uuid)
                .destination(destination)
                .note(note)
                .matchCount(matchCount)
                .lastMatchedAt(lastMatchedAt)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentExclusionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder destination(String destination);
        public abstract Builder note(String note);
        public abstract Builder matchCount(Long matchCount);
        public abstract Builder lastMatchedAt(DateTime lastMatchedAt);
        public abstract Builder createdAt(DateTime createdAt);

        public abstract SegmentExclusionResponse build();
    }

}
