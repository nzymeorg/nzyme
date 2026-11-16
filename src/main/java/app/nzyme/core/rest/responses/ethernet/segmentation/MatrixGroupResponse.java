package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class MatrixGroupResponse {

    @Schema(description = "ID of the group as cells refer to it. 0 is everything internal that is in no segment, -1 is everything external that is in no external network, -2 is all broadcast and multicast addresses.")
    @JsonProperty("id")
    public abstract long id();

    @Schema(description = "INTERNAL, EXTERNAL, UNASSIGNED, INTERNET or BROADCAST.")
    @JsonProperty("type")
    public abstract String type();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("segment_uuid")
    public abstract UUID segmentUuid();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @Schema(description = "The networks of a segment or external network, in CIDR notation. Empty for the groups that are no segment.")
    @JsonProperty("networks")
    public abstract List<String> networks();

    @Schema(description = "The autonomous systems of an external network. Empty for everything else.")
    @JsonProperty("asns")
    public abstract List<Long> asns();

    @Schema(description = "The taps that recorded traffic of the group in the time range.")
    @JsonProperty("tap_uuids")
    public abstract List<UUID> tapUuids();

    public static MatrixGroupResponse create(long id,
                                             String type,
                                             String name,
                                             UUID segmentUuid,
                                             String description,
                                             List<String> networks,
                                             List<Long> asns,
                                             List<UUID> tapUuids) {
        return builder()
                .id(id)
                .type(type)
                .name(name)
                .segmentUuid(segmentUuid)
                .description(description)
                .networks(networks)
                .asns(asns)
                .tapUuids(tapUuids)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MatrixGroupResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder type(String type);
        public abstract Builder name(String name);
        public abstract Builder segmentUuid(UUID segmentUuid);
        public abstract Builder description(String description);
        public abstract Builder networks(List<String> networks);
        public abstract Builder asns(List<Long> asns);
        public abstract Builder tapUuids(List<UUID> tapUuids);

        public abstract MatrixGroupResponse build();
    }

}
