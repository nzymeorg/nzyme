package app.nzyme.core.rest.responses.ethernet.icmp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class IcmpStatisticsBucketResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("echo_requests")
    public abstract long echoRequests();

    @JsonProperty("echo_replies")
    public abstract long echoReplies();

    @JsonProperty("unreachable")
    public abstract long unreachable();

    @JsonProperty("time_exceeded")
    public abstract long timeExceeded();

    @JsonProperty("redirects")
    public abstract long redirects();

    @JsonProperty("other")
    public abstract long other();

    public static IcmpStatisticsBucketResponse create(long total, long echoRequests, long echoReplies, long unreachable, long timeExceeded, long redirects, long other) {
        return builder()
                .total(total)
                .echoRequests(echoRequests)
                .echoReplies(echoReplies)
                .unreachable(unreachable)
                .timeExceeded(timeExceeded)
                .redirects(redirects)
                .other(other)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpStatisticsBucketResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder echoRequests(long echoRequests);
        public abstract Builder echoReplies(long echoReplies);
        public abstract Builder unreachable(long unreachable);
        public abstract Builder timeExceeded(long timeExceeded);
        public abstract Builder redirects(long redirects);
        public abstract Builder other(long other);
        public abstract IcmpStatisticsBucketResponse build();
    }
}
