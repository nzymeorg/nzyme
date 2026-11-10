package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class TopologyGeoResponse {

    @Schema(description = "Positions of the selected taps. Taps of one location form one site.")
    @JsonProperty("sites")
    public abstract List<TopologySiteResponse> sites();

    @Schema(description = "Selected taps without coordinates and without a location that has coordinates.")
    @JsonProperty("unplaced_taps")
    public abstract List<TopologyTapResponse> unplacedTaps();

    @Schema(description = "Flows ordered by bytes, descending.")
    @JsonProperty("flows")
    public abstract List<TopologyGeoFlowResponse> flows();

    @JsonProperty("total_flows")
    public abstract long totalFlows();

    @JsonProperty("total_sessions")
    public abstract long totalSessions();

    @JsonProperty("total_bytes")
    public abstract long totalBytes();

    @JsonProperty("limit")
    public abstract int limit();

    @JsonProperty("series")
    public abstract TopologySeriesConfigurationResponse series();

    public static TopologyGeoResponse create(List<TopologySiteResponse> sites, List<TopologyTapResponse> unplacedTaps,
                                             List<TopologyGeoFlowResponse> flows, long totalFlows, long totalSessions,
                                             long totalBytes, int limit, TopologySeriesConfigurationResponse series) {
        return builder()
                .sites(sites)
                .unplacedTaps(unplacedTaps)
                .flows(flows)
                .totalFlows(totalFlows)
                .totalSessions(totalSessions)
                .totalBytes(totalBytes)
                .limit(limit)
                .series(series)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyGeoResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sites(List<TopologySiteResponse> sites);
        public abstract Builder unplacedTaps(List<TopologyTapResponse> unplacedTaps);
        public abstract Builder flows(List<TopologyGeoFlowResponse> flows);
        public abstract Builder totalFlows(long totalFlows);
        public abstract Builder totalSessions(long totalSessions);
        public abstract Builder totalBytes(long totalBytes);
        public abstract Builder limit(int limit);
        public abstract Builder series(TopologySeriesConfigurationResponse series);

        public abstract TopologyGeoResponse build();
    }

}
