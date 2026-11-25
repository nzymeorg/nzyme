package app.nzyme.core.ethernet.ndp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NdpPrefix {

    @JsonProperty("prefix")
    public abstract String prefix();

    @JsonProperty("prefix_length")
    public abstract int prefixLength();

    @JsonProperty("on_link")
    public abstract boolean onLink();

    @JsonProperty("autonomous")
    public abstract boolean autonomous();

    @JsonProperty("valid_lifetime")
    public abstract long validLifetime();

    @JsonProperty("preferred_lifetime")
    public abstract long preferredLifetime();

    @JsonCreator
    public static NdpPrefix create(@JsonProperty("prefix") String prefix,
                                   @JsonProperty("prefix_length") int prefixLength,
                                   @JsonProperty("on_link") boolean onLink,
                                   @JsonProperty("autonomous") boolean autonomous,
                                   @JsonProperty("valid_lifetime") long validLifetime,
                                   @JsonProperty("preferred_lifetime") long preferredLifetime) {
        return builder()
                .prefix(prefix)
                .prefixLength(prefixLength)
                .onLink(onLink)
                .autonomous(autonomous)
                .validLifetime(validLifetime)
                .preferredLifetime(preferredLifetime)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NdpPrefix.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder prefix(String prefix);

        public abstract Builder prefixLength(int prefixLength);

        public abstract Builder onLink(boolean onLink);

        public abstract Builder autonomous(boolean autonomous);

        public abstract Builder validLifetime(long validLifetime);

        public abstract Builder preferredLifetime(long preferredLifetime);

        public abstract NdpPrefix build();
    }

}
