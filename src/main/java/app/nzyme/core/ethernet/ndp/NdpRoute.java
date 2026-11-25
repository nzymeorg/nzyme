package app.nzyme.core.ethernet.ndp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NdpRoute {

    @JsonProperty("prefix")
    public abstract String prefix();

    @JsonProperty("prefix_length")
    public abstract int prefixLength();

    @JsonProperty("preference")
    public abstract String preference();

    @JsonProperty("lifetime")
    public abstract long lifetime();

    @JsonCreator
    public static NdpRoute create(@JsonProperty("prefix") String prefix,
                                  @JsonProperty("prefix_length") int prefixLength,
                                  @JsonProperty("preference") String preference,
                                  @JsonProperty("lifetime") long lifetime) {
        return builder()
                .prefix(prefix)
                .prefixLength(prefixLength)
                .preference(preference)
                .lifetime(lifetime)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NdpRoute.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder prefix(String prefix);

        public abstract Builder prefixLength(int prefixLength);

        public abstract Builder preference(String preference);

        public abstract Builder lifetime(long lifetime);

        public abstract NdpRoute build();
    }

}
