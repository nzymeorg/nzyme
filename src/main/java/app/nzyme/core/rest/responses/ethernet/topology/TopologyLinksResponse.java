package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class TopologyLinksResponse {

    @JsonProperty("nodes")
    public abstract List<TopologyNodeResponse> nodes();

    @JsonProperty("services")
    public abstract List<TopologyServiceResponse> services();

    @Schema(description = "Links ordered by bytes, descending.")
    @JsonProperty("links")
    public abstract List<TopologyLinkResponse> links();

    @Schema(description = "Number of links the selection has. More than the returned links if the limit cut the list.")
    @JsonProperty("total_links")
    public abstract long totalLinks();

    @Schema(description = "Sessions of all links of the selection, not only of the returned ones.")
    @JsonProperty("total_sessions")
    public abstract long totalSessions();

    @Schema(description = "Bytes of all links of the selection, not only of the returned ones.")
    @JsonProperty("total_bytes")
    public abstract long totalBytes();

    @JsonProperty("limit")
    public abstract int limit();

    @JsonProperty("series")
    public abstract TopologySeriesConfigurationResponse series();

    public static TopologyLinksResponse create(List<TopologyNodeResponse> nodes, List<TopologyServiceResponse> services,
                                               List<TopologyLinkResponse> links, long totalLinks, long totalSessions,
                                               long totalBytes, int limit, TopologySeriesConfigurationResponse series) {
        return builder()
                .nodes(nodes)
                .services(services)
                .links(links)
                .totalLinks(totalLinks)
                .totalSessions(totalSessions)
                .totalBytes(totalBytes)
                .limit(limit)
                .series(series)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyLinksResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder nodes(List<TopologyNodeResponse> nodes);
        public abstract Builder services(List<TopologyServiceResponse> services);
        public abstract Builder links(List<TopologyLinkResponse> links);
        public abstract Builder totalLinks(long totalLinks);
        public abstract Builder totalSessions(long totalSessions);
        public abstract Builder totalBytes(long totalBytes);
        public abstract Builder limit(int limit);
        public abstract Builder series(TopologySeriesConfigurationResponse series);

        public abstract TopologyLinksResponse build();
    }

}
