package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class TopologyTimelineBucketResponse {

    @JsonProperty("bucket")
    public abstract DateTime bucket();

    @JsonProperty("sessions_internal")
    public abstract long sessionsInternal();

    @JsonProperty("sessions_outbound")
    public abstract long sessionsOutbound();

    @JsonProperty("sessions_inbound")
    public abstract long sessionsInbound();

    @JsonProperty("sessions_external")
    public abstract long sessionsExternal();

    @JsonProperty("bytes_internal")
    public abstract long bytesInternal();

    @JsonProperty("bytes_outbound")
    public abstract long bytesOutbound();

    @JsonProperty("bytes_inbound")
    public abstract long bytesInbound();

    @JsonProperty("bytes_external")
    public abstract long bytesExternal();

    public static TopologyTimelineBucketResponse create(DateTime bucket, long sessionsInternal, long sessionsOutbound,
                                                        long sessionsInbound, long sessionsExternal, long bytesInternal,
                                                        long bytesOutbound, long bytesInbound, long bytesExternal) {
        return builder()
                .bucket(bucket)
                .sessionsInternal(sessionsInternal)
                .sessionsOutbound(sessionsOutbound)
                .sessionsInbound(sessionsInbound)
                .sessionsExternal(sessionsExternal)
                .bytesInternal(bytesInternal)
                .bytesOutbound(bytesOutbound)
                .bytesInbound(bytesInbound)
                .bytesExternal(bytesExternal)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyTimelineBucketResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);
        public abstract Builder sessionsInternal(long sessionsInternal);
        public abstract Builder sessionsOutbound(long sessionsOutbound);
        public abstract Builder sessionsInbound(long sessionsInbound);
        public abstract Builder sessionsExternal(long sessionsExternal);
        public abstract Builder bytesInternal(long bytesInternal);
        public abstract Builder bytesOutbound(long bytesOutbound);
        public abstract Builder bytesInbound(long bytesInbound);
        public abstract Builder bytesExternal(long bytesExternal);

        public abstract TopologyTimelineBucketResponse build();
    }

}
