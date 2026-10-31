package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;

@AutoValue
public abstract class BriefingMonitoringResponse {

    @JsonProperty("taps")
    public abstract List<BriefingTapResponse> taps();

    @JsonProperty("taps_online")
    public abstract long tapsOnline();

    @JsonProperty("taps_total")
    public abstract long tapsTotal();

    @JsonProperty("checks")
    public abstract List<BriefingCheckResponse> checks();

    @JsonProperty("health_indicators")
    @Nullable
    public abstract Map<String, String> healthIndicators();

    public static BriefingMonitoringResponse create(List<BriefingTapResponse> taps, long tapsOnline, long tapsTotal, List<BriefingCheckResponse> checks, Map<String, String> healthIndicators) {
        return builder()
                .taps(taps)
                .tapsOnline(tapsOnline)
                .tapsTotal(tapsTotal)
                .checks(checks)
                .healthIndicators(healthIndicators)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingMonitoringResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder taps(List<BriefingTapResponse> taps);

        public abstract Builder tapsOnline(long tapsOnline);

        public abstract Builder tapsTotal(long tapsTotal);

        public abstract Builder checks(List<BriefingCheckResponse> checks);

        public abstract Builder healthIndicators(Map<String, String> healthIndicators);

        public abstract BriefingMonitoringResponse build();
    }
}
