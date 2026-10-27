package app.nzyme.core.rest.resources.taps.reports.tables.nameres;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class NameResolutionVictimReport {

    @JsonProperty("mac")
    public abstract String mac();

    @Nullable
    @JsonProperty("address")
    public abstract String address();

    @JsonProperty("count")
    public abstract long count();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @JsonCreator
    public static NameResolutionVictimReport create(@JsonProperty("mac") String mac,
                                                    @JsonProperty("address") String address,
                                                    @JsonProperty("count") long count,
                                                    @JsonProperty("first_seen") DateTime firstSeen,
                                                    @JsonProperty("last_seen") DateTime lastSeen) {
        return builder()
                .mac(mac)
                .address(address)
                .count(count)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionVictimReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(String mac);

        public abstract Builder address(String address);

        public abstract Builder count(long count);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract NameResolutionVictimReport build();
    }
}
