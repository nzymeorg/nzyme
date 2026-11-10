package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class TopologyServiceResponse {

    @JsonProperty("id")
    public abstract String id();

    @Schema(description = "TCP or UDP. Not set for a service that is a protocol.")
    @Nullable
    @JsonProperty("l4_type")
    public abstract String l4Type();

    @Schema(description = "Destination port. Not set for a service that is a protocol.")
    @Nullable
    @JsonProperty("port")
    public abstract Integer port();

    @Schema(description = "Protocol tags of the sessions, joined with a slash. For a port, the protocol that moved most of its bytes, if any.")
    @Nullable
    @JsonProperty("protocol")
    public abstract String protocol();

    public static TopologyServiceResponse create(String id, @Nullable String l4Type, @Nullable Integer port,
                                                 @Nullable String protocol) {
        return builder()
                .id(id)
                .l4Type(l4Type)
                .port(port)
                .protocol(protocol)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyServiceResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(String id);
        public abstract Builder l4Type(String l4Type);
        public abstract Builder port(Integer port);
        public abstract Builder protocol(String protocol);

        public abstract TopologyServiceResponse build();
    }

}
