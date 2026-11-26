package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class Dot11AdvertisedNetworkReport {

    public abstract List<Dot11SecurityInformationReport> security();
    public abstract List<String> fingerprints();
    public abstract List<Double> rates();
    public abstract List<Boolean> wps();
    public abstract Dot11SignalStrengthReport signalStrength();
    public abstract Map<Long, Map<Long, Long>> signalHistogram();
    public abstract List<String> infrastructureTypes();
    public abstract Map<Long, Map<String, Dot11ChannelStatisticsReport>> channelStatistics();
    public abstract long beaconAdvertisements();
    public abstract long probeResponseAdvertisements();
    public abstract Map<Integer, Long> declaredFrequencies();

    @JsonCreator
    public static Dot11AdvertisedNetworkReport create(@JsonProperty("security") List<Dot11SecurityInformationReport> security,
                                                      @JsonProperty("fingerprints") List<String> fingerprints,
                                                      @JsonProperty("rates") List<Double> rates,
                                                      @JsonProperty("wps") List<Boolean> wps,
                                                      @JsonProperty("signal_strength") Dot11SignalStrengthReport signalStrength,
                                                      @JsonProperty("signal_histogram") Map<Long, Map<Long, Long>> signalHistogram,
                                                      @JsonProperty("infrastructure_types") List<String> infrastructureTypes,
                                                      @JsonProperty("channel_statistics") Map<Long, Map<String, Dot11ChannelStatisticsReport>> channelStatistics,
                                                      @JsonProperty("beacon_advertisements") long beaconAdvertisements,
                                                      @JsonProperty("proberesp_advertisements") long probeResponseAdvertisements,
                                                      @JsonProperty("declared_frequencies") Map<Integer, Long> declaredFrequencies) {
        return builder()
                .security(security)
                .fingerprints(fingerprints)
                .rates(rates)
                .wps(wps)
                .signalStrength(signalStrength)
                .signalHistogram(signalHistogram)
                .infrastructureTypes(infrastructureTypes)
                .channelStatistics(channelStatistics)
                .beaconAdvertisements(beaconAdvertisements)
                .probeResponseAdvertisements(probeResponseAdvertisements)
                .declaredFrequencies(declaredFrequencies)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11AdvertisedNetworkReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder security(List<Dot11SecurityInformationReport> security);

        public abstract Builder fingerprints(List<String> fingerprints);

        public abstract Builder rates(List<Double> rates);

        public abstract Builder wps(List<Boolean> wps);

        public abstract Builder signalStrength(Dot11SignalStrengthReport signalStrength);

        public abstract Builder signalHistogram(Map<Long, Map<Long, Long>> signalHistogram);

        public abstract Builder infrastructureTypes(List<String> infrastructureTypes);

        public abstract Builder channelStatistics(Map<Long, Map<String, Dot11ChannelStatisticsReport>> channelStatistics);

        public abstract Builder beaconAdvertisements(long beaconAdvertisements);

        public abstract Builder probeResponseAdvertisements(long probeResponseAdvertisements);

        public abstract Builder declaredFrequencies(Map<Integer, Long> declaredFrequencies);

        public abstract Dot11AdvertisedNetworkReport build();
    }
}
