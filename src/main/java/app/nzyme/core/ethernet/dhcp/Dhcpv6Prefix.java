package app.nzyme.core.ethernet.dhcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Dhcpv6Prefix {

    @JsonProperty("prefix")
    public abstract String prefix();

    @JsonProperty("prefix_length")
    public abstract int prefixLength();

    @JsonProperty("preferred_lifetime")
    public abstract long preferredLifetime();

    @JsonProperty("valid_lifetime")
    public abstract long validLifetime();

    @JsonCreator
    public static Dhcpv6Prefix create(@JsonProperty("prefix") String prefix,
                                      @JsonProperty("prefix_length") int prefixLength,
                                      @JsonProperty("preferred_lifetime") long preferredLifetime,
                                      @JsonProperty("valid_lifetime") long validLifetime) {
        return builder()
                .prefix(prefix)
                .prefixLength(prefixLength)
                .preferredLifetime(preferredLifetime)
                .validLifetime(validLifetime)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dhcpv6Prefix.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder prefix(String prefix);

        public abstract Builder prefixLength(int prefixLength);

        public abstract Builder preferredLifetime(long preferredLifetime);

        public abstract Builder validLifetime(long validLifetime);

        public abstract Dhcpv6Prefix build();
    }

}
