package app.nzyme.core.rest.responses.dot11.disco;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class DiscoHistogramValueResponse {

    @JsonProperty("timestamp")
    public abstract DateTime timestamp();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("deauthentication_count")
    public abstract long deauthenticationCount();

    @JsonProperty("disassociation_count")
    public abstract long disassociationCount();

    @JsonProperty("broadcast_count")
    public abstract long broadcastCount();

    public static DiscoHistogramValueResponse create(DateTime timestamp, long frameCount, long deauthenticationCount, long disassociationCount, long broadcastCount) {
        return builder()
                .timestamp(timestamp)
                .frameCount(frameCount)
                .deauthenticationCount(deauthenticationCount)
                .disassociationCount(disassociationCount)
                .broadcastCount(broadcastCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoHistogramValueResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder timestamp(DateTime timestamp);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder deauthenticationCount(long deauthenticationCount);

        public abstract Builder disassociationCount(long disassociationCount);

        public abstract Builder broadcastCount(long broadcastCount);

        public abstract DiscoHistogramValueResponse build();
    }
}
