package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

@AutoValue
public abstract class Dot11DiscoReport {

    public abstract List<Dot11DiscoFrameReport> frames();
    public abstract List<Dot11FrameOverflowReport> overflow();

    // Taps older than the per-frame report send "deauth" and "disassoc" maps instead, which are ignored.
    @JsonCreator
    public static Dot11DiscoReport create(@JsonProperty("frames") @Nullable List<Dot11DiscoFrameReport> frames,
                                          @JsonProperty("overflow") @Nullable List<Dot11FrameOverflowReport> overflow) {
        return builder()
                .frames(frames == null ? Collections.emptyList() : frames)
                .overflow(overflow == null ? Collections.emptyList() : overflow)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11DiscoReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder frames(List<Dot11DiscoFrameReport> frames);

        public abstract Builder overflow(List<Dot11FrameOverflowReport> overflow);

        public abstract Dot11DiscoReport build();
    }
}
