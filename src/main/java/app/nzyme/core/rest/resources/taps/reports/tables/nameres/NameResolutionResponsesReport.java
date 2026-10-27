package app.nzyme.core.rest.resources.taps.reports.tables.nameres;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NameResolutionResponsesReport {

    public abstract List<NameResolutionResponseReport> responses();

    @JsonCreator
    public static NameResolutionResponsesReport create(@JsonProperty("responses") List<NameResolutionResponseReport> responses) {
        return builder()
                .responses(responses)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionResponsesReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder responses(List<NameResolutionResponseReport> responses);

        public abstract NameResolutionResponsesReport build();
    }
}
