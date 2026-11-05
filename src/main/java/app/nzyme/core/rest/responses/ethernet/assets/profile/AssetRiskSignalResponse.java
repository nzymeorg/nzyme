package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetRiskSignalResponse {

    @JsonProperty("signal")
    public abstract String signal();
    @JsonProperty("severity")
    public abstract String severity();
    @JsonProperty("count")
    public abstract long count();
    @Nullable
    @JsonProperty("detail")
    public abstract String detail();
    @Nullable
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static AssetRiskSignalResponse create(String signal, String severity, long count, String detail, DateTime lastSeen) {
        return builder()
                .signal(signal)
                .severity(severity)
                .count(count)
                .detail(detail)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetRiskSignalResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder signal(String signal);
        public abstract Builder severity(String severity);
        public abstract Builder count(long count);
        public abstract Builder detail(String detail);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract AssetRiskSignalResponse build();
    }

}
