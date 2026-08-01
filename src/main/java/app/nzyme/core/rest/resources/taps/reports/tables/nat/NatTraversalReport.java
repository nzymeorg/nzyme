package app.nzyme.core.rest.resources.taps.reports.tables.nat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NatTraversalReport {

    public abstract List<StunDiscoveryReport> discoveries();

    @JsonCreator
    public static NatTraversalReport create(@JsonProperty("discoveries") List<StunDiscoveryReport> discoveries) {
        return builder()
                .discoveries(discoveries)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NatTraversalReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder discoveries(List<StunDiscoveryReport> discoveries);

        public abstract NatTraversalReport build();
    }
}

