package app.nzyme.core.rest.resources.taps.reports.tables.tcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class TcpProbesReport {

    public abstract List<TcpProbeReport> probes();

    @JsonCreator
    public static TcpProbesReport create(@JsonProperty("probes") List<TcpProbeReport> probes) {
        return builder()
                .probes(probes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TcpProbesReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder probes(List<TcpProbeReport> probes);

        public abstract TcpProbesReport build();
    }
}
