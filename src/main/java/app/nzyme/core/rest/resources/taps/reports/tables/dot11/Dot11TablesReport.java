package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;
import java.util.Map;
import jakarta.annotation.Nullable;
import java.util.Collections;

@AutoValue
public abstract class Dot11TablesReport {

    public abstract Map<String, Dot11BSSIDReport> bssids();
    public abstract Map<String, Dot11ClientReport> clients();
    public abstract Dot11DiscoReport disco();
    public abstract List<Dot11ConnectionFrameReport> connectionFrames();
    public abstract List<Dot11FrameOverflowReport> connectionFrameOverflow();
    public abstract List<Dot11ChannelSwitchReport> channelSwitches();
    public abstract List<Dot11AlertReport> alerts();

    @JsonCreator
    public static Dot11TablesReport create(@JsonProperty("bssids") Map<String, Dot11BSSIDReport> bssids,
                                           @JsonProperty("clients") Map<String, Dot11ClientReport> clients,
                                           @JsonProperty("disco") Dot11DiscoReport disco,
                                           @JsonProperty("connection_frames") List<Dot11ConnectionFrameReport> connectionFrames,
                                           @JsonProperty("connection_frame_overflow") @Nullable List<Dot11FrameOverflowReport> connectionFrameOverflow,
                                           @JsonProperty("channel_switches") List<Dot11ChannelSwitchReport> channelSwitches,
                                           @JsonProperty("alerts") List<Dot11AlertReport> alerts) {
        return builder()
                .bssids(bssids)
                .clients(clients)
                .disco(disco)
                .connectionFrames(connectionFrames)
                .connectionFrameOverflow(connectionFrameOverflow == null ? Collections.emptyList() : connectionFrameOverflow)
                .channelSwitches(channelSwitches)
                .alerts(alerts)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11TablesReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bssids(Map<String, Dot11BSSIDReport> bssids);

        public abstract Builder clients(Map<String, Dot11ClientReport> clients);

        public abstract Builder disco(Dot11DiscoReport disco);

        public abstract Builder connectionFrames(List<Dot11ConnectionFrameReport> connectionFrames);

        public abstract Builder connectionFrameOverflow(List<Dot11FrameOverflowReport> connectionFrameOverflow);
        public abstract Builder channelSwitches(List<Dot11ChannelSwitchReport> channelSwitches);

        public abstract Builder alerts(List<Dot11AlertReport> alerts);

        public abstract Dot11TablesReport build();
    }
}
