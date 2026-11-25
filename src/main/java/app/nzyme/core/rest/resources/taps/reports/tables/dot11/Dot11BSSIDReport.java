package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class Dot11BSSIDReport {

    public abstract Map<String, Dot11AdvertisedNetworkReport> advertisedNetworks();
    public abstract Map<String, Dot11ClientStatisticsReport> clients();
    public abstract long hiddenSSIDFrames();
    public abstract Dot11SignalStrengthReport signalStrength();
    public abstract List<String> fingerprints();
    public abstract Map<Integer, Long> declaredFrequencies();

    @JsonCreator
    public static Dot11BSSIDReport create(@JsonProperty("advertised_networks") Map<String, Dot11AdvertisedNetworkReport> advertisedNetworks,
                                          @JsonProperty("clients") Map<String, Dot11ClientStatisticsReport> clients,
                                          @JsonProperty("hidden_ssid_frames") long hiddenSSIDFrames,
                                          @JsonProperty("signal_strength") Dot11SignalStrengthReport signalStrength,
                                          @JsonProperty("fingerprints") List<String> fingerprints,
                                          @JsonProperty("declared_frequencies") Map<Integer, Long> declaredFrequencies) {
        return builder()
                .advertisedNetworks(advertisedNetworks)
                .clients(clients)
                .hiddenSSIDFrames(hiddenSSIDFrames)
                .signalStrength(signalStrength)
                .fingerprints(fingerprints)
                .declaredFrequencies(declaredFrequencies)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11BSSIDReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder advertisedNetworks(Map<String, Dot11AdvertisedNetworkReport> advertisedNetworks);

        public abstract Builder clients(Map<String, Dot11ClientStatisticsReport> clients);

        public abstract Builder hiddenSSIDFrames(long hiddenSSIDFrames);

        public abstract Builder signalStrength(Dot11SignalStrengthReport signalStrength);

        public abstract Builder fingerprints(List<String> fingerprints);

        public abstract Builder declaredFrequencies(Map<Integer, Long> declaredFrequencies);

        public abstract Dot11BSSIDReport build();
    }
}
