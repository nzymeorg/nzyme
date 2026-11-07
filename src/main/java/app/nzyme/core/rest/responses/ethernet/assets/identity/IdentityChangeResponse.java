package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class IdentityChangeResponse {

    @JsonProperty("signal")
    public abstract String signal();

    @JsonProperty("weight")
    public abstract int weight();

    @Nullable
    @JsonProperty("previous_value")
    public abstract String previousValue();

    @JsonProperty("new_value")
    public abstract String newValue();

    @JsonProperty("seen_at")
    public abstract DateTime seenAt();

    public static IdentityChangeResponse create(String signal, int weight, String previousValue, String newValue, DateTime seenAt) {
        return builder()
                .signal(signal)
                .weight(weight)
                .previousValue(previousValue)
                .newValue(newValue)
                .seenAt(seenAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IdentityChangeResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder signal(String signal);
        public abstract Builder weight(int weight);
        public abstract Builder previousValue(String previousValue);
        public abstract Builder newValue(String newValue);
        public abstract Builder seenAt(DateTime seenAt);
        public abstract IdentityChangeResponse build();
    }
}
