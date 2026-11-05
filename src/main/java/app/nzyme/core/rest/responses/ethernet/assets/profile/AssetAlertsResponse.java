package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class AssetAlertsResponse {

    @JsonProperty("total")
    public abstract long total();
    @JsonProperty("active")
    public abstract long active();
    @JsonProperty("alerts")
    public abstract List<AssetAlertSummaryResponse> alerts();

    public static AssetAlertsResponse create(long total, long active, List<AssetAlertSummaryResponse> alerts) {
        return builder()
                .total(total)
                .active(active)
                .alerts(alerts)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetAlertsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder active(long active);
        public abstract Builder alerts(List<AssetAlertSummaryResponse> alerts);

        public abstract AssetAlertsResponse build();
    }

}
