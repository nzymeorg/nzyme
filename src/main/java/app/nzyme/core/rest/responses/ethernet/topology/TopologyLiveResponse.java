package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.joda.time.DateTime;

@AutoValue
public abstract class TopologyLiveResponse {

    @Schema(description = "Time of the database when the node answered, which all nodes of a cluster share. Sessions with activity in the requested seconds before it are returned, and those a tap with a clock that runs up to a minute ahead reported for the time after it.")
    @JsonProperty("now")
    public abstract DateTime now();

    @JsonProperty("seconds")
    public abstract int seconds();

    @JsonProperty("nodes")
    public abstract List<TopologyNodeResponse> nodes();

    @Schema(description = "Positions of the selected taps. Taps of one location form one site.")
    @JsonProperty("sites")
    public abstract List<TopologySiteResponse> sites();

    @Schema(description = "Selected taps without coordinates and without a location that has coordinates.")
    @JsonProperty("unplaced_taps")
    public abstract List<TopologyTapResponse> unplacedTaps();

    @Schema(description = "Sessions ordered by their most recent activity, descending.")
    @JsonProperty("sessions")
    public abstract List<TopologyLiveSessionResponse> sessions();

    @JsonProperty("limit")
    public abstract int limit();

    @Schema(description = "True if more sessions were active than the limit allows. The returned ones are the most recently active.")
    @JsonProperty("limited")
    public abstract boolean limited();

    @Schema(description = "True if several of the selected taps can have recorded the same traffic and it should be counted once: per source address, destination address, transport and destination port, the most sessions and bytes that any one tap saw.")
    @JsonProperty("merge_taps")
    public abstract boolean mergeTaps();

    public static TopologyLiveResponse create(DateTime now, int seconds, List<TopologyNodeResponse> nodes,
                                              List<TopologySiteResponse> sites,
                                              List<TopologyTapResponse> unplacedTaps,
                                              List<TopologyLiveSessionResponse> sessions, int limit,
                                              boolean limited, boolean mergeTaps) {
        return builder()
                .now(now)
                .seconds(seconds)
                .nodes(nodes)
                .sites(sites)
                .unplacedTaps(unplacedTaps)
                .sessions(sessions)
                .limit(limit)
                .limited(limited)
                .mergeTaps(mergeTaps)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyLiveResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder now(DateTime now);
        public abstract Builder seconds(int seconds);
        public abstract Builder nodes(List<TopologyNodeResponse> nodes);
        public abstract Builder sites(List<TopologySiteResponse> sites);
        public abstract Builder unplacedTaps(List<TopologyTapResponse> unplacedTaps);
        public abstract Builder sessions(List<TopologyLiveSessionResponse> sessions);
        public abstract Builder limit(int limit);
        public abstract Builder limited(boolean limited);
        public abstract Builder mergeTaps(boolean mergeTaps);

        public abstract TopologyLiveResponse build();
    }

}
