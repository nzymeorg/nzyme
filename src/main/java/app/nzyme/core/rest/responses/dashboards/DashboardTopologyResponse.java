package app.nzyme.core.rest.responses.dashboards;

import app.nzyme.core.rest.responses.ethernet.topology.TopologyLiveResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class DashboardTopologyResponse {

    @Schema(description = "How long ago the feed was built, in milliseconds. The time of the feed plus this is the current time of the node.")
    @JsonProperty("age_ms")
    public abstract long ageMs();

    @Schema(description = "True if addresses and names were removed. Node IDs, session keys, addresses and tap UUIDs are then replaced by stable stand-ins, and a node only carries its type, its level, the name of its tap and, for a country, its country code.")
    @JsonProperty("redacted")
    public abstract boolean redacted();

    @Schema(description = "The sessions that were active in the last seconds, like the live sessions of the topology, without sites. Missing if the Ethernet subsystem is disabled or the feed could not be built.")
    @Nullable
    @JsonProperty("feed")
    public abstract TopologyLiveResponse feed();

    public static DashboardTopologyResponse create(long ageMs, boolean redacted, @Nullable TopologyLiveResponse feed) {
        return builder()
                .ageMs(ageMs)
                .redacted(redacted)
                .feed(feed)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardTopologyResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ageMs(long ageMs);

        public abstract Builder redacted(boolean redacted);

        public abstract Builder feed(TopologyLiveResponse feed);

        public abstract DashboardTopologyResponse build();
    }

}
