package app.nzyme.core.rest.responses.ethernet.recon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class ReconTargetPortResponse {

    @JsonProperty("l4_type")
    public abstract String l4Type();

    @JsonProperty("port")
    public abstract int port();

    @JsonProperty("source_count")
    public abstract long sourceCount();

    @JsonProperty("answered")
    public abstract boolean answered();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static ReconTargetPortResponse create(String l4Type, int port, long sourceCount, boolean answered, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .l4Type(l4Type)
                .port(port)
                .sourceCount(sourceCount)
                .answered(answered)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconTargetPortResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder l4Type(String l4Type);
        public abstract Builder port(int port);
        public abstract Builder sourceCount(long sourceCount);
        public abstract Builder answered(boolean answered);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract ReconTargetPortResponse build();
    }
}
