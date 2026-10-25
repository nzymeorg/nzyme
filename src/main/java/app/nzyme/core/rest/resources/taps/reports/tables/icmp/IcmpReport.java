package app.nzyme.core.rest.resources.taps.reports.tables.icmp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class IcmpReport {

    public abstract List<IcmpSummaryReport> summaries();
    public abstract List<IcmpErrorReport> errors();

    @JsonCreator
    public static IcmpReport create(@JsonProperty("summaries") List<IcmpSummaryReport> summaries,
                                    @JsonProperty("errors") List<IcmpErrorReport> errors) {
        return builder()
                .summaries(summaries)
                .errors(errors)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder summaries(List<IcmpSummaryReport> summaries);
        public abstract Builder errors(List<IcmpErrorReport> errors);
        public abstract IcmpReport build();
    }
}
