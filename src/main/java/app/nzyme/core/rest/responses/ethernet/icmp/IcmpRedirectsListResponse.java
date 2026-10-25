package app.nzyme.core.rest.responses.ethernet.icmp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class IcmpRedirectsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("redirects")
    public abstract List<IcmpRedirectResponse> redirects();

    public static IcmpRedirectsListResponse create(long total, List<IcmpRedirectResponse> redirects) {
        return builder()
                .total(total)
                .redirects(redirects)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpRedirectsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder redirects(List<IcmpRedirectResponse> redirects);
        public abstract IcmpRedirectsListResponse build();
    }
}
