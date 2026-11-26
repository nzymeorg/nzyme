package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.Map;

@AutoValue
public abstract class Dot11ClientReport {

    public abstract Map<String, Long> probeRequestSSIDs();
    public abstract long wildcardProbeRequests();
    public abstract Dot11SignalStrengthReport signalStrength();
    @Nullable
    public abstract Map<Integer, Dot11FrequencySignalStrengthReport> signalByFrequency();

    @JsonCreator
    public static Dot11ClientReport create(@JsonProperty("probe_request_ssids") Map<String, Long> probeRequestSSIDs,
                                           @JsonProperty("wildcard_probe_requests") long wildcardProbeRequests,
                                           @JsonProperty("signal_strength") Dot11SignalStrengthReport signalStrength,
                                           @JsonProperty("signal_by_frequency") @Nullable Map<Integer, Dot11FrequencySignalStrengthReport> signalByFrequency) {
        return builder()
                .probeRequestSSIDs(probeRequestSSIDs)
                .wildcardProbeRequests(wildcardProbeRequests)
                .signalStrength(signalStrength)
                .signalByFrequency(signalByFrequency)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11ClientReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder probeRequestSSIDs(Map<String, Long> probeRequestSSIDs);

        public abstract Builder wildcardProbeRequests(long wildcardProbeRequests);

        public abstract Builder signalStrength(Dot11SignalStrengthReport signalStrength);

        public abstract Builder signalByFrequency(Map<Integer, Dot11FrequencySignalStrengthReport> signalByFrequency);

        public abstract Dot11ClientReport build();
    }
}
