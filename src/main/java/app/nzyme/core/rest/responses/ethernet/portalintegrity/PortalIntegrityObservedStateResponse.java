package app.nzyme.core.rest.responses.ethernet.portalintegrity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class PortalIntegrityObservedStateResponse {

    @Schema(description = "Number of reports that were looked at.")
    @JsonProperty("report_count")
    public abstract int reportCount();

    @Schema(description = "Only this many of the latest reports the probe sent for the control URL in the time range are looked at, over both IP versions together.")
    @JsonProperty("report_limit")
    public abstract int reportLimit();

    @Schema(description = "True if the time range has more reports than were looked at.")
    @JsonProperty("reports_limited")
    public abstract boolean reportsLimited();

    @Schema(description = "What the reports showed, most recently seen first.")
    @JsonProperty("outcomes")
    public abstract List<PortalIntegrityObservedOutcomeResponse> outcomes();

    @Schema(description = "Only this many different outcomes are returned, the ones seen last. Failed requests do not count.")
    @JsonProperty("outcome_limit")
    public abstract int outcomeLimit();

    @Schema(description = "True if the reports showed more different outcomes than are returned.")
    @JsonProperty("outcomes_limited")
    public abstract boolean outcomesLimited();

    public static PortalIntegrityObservedStateResponse create(int reportCount, int reportLimit, boolean reportsLimited, List<PortalIntegrityObservedOutcomeResponse> outcomes, int outcomeLimit, boolean outcomesLimited) {
        return builder()
                .reportCount(reportCount)
                .reportLimit(reportLimit)
                .reportsLimited(reportsLimited)
                .outcomes(outcomes)
                .outcomeLimit(outcomeLimit)
                .outcomesLimited(outcomesLimited)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityObservedStateResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder reportCount(int reportCount);

        public abstract Builder reportLimit(int reportLimit);

        public abstract Builder reportsLimited(boolean reportsLimited);

        public abstract Builder outcomes(List<PortalIntegrityObservedOutcomeResponse> outcomes);

        public abstract Builder outcomeLimit(int outcomeLimit);

        public abstract Builder outcomesLimited(boolean outcomesLimited);

        public abstract PortalIntegrityObservedStateResponse build();
    }
}
