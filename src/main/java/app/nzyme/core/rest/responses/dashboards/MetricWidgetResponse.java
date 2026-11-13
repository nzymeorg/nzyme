package app.nzyme.core.rest.responses.dashboards;

import app.nzyme.core.rest.responses.briefing.BriefingHistogramBucketResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.List;

@AutoValue
public abstract class MetricWidgetResponse {

    @JsonProperty("metric")
    public abstract String metric();

    @JsonProperty("title")
    public abstract String title();

    @Nullable
    @JsonProperty("unit")
    public abstract String unit();

    @JsonProperty("aggregation")
    public abstract String aggregation();

    @JsonProperty("current")
    public abstract double current();

    @Nullable
    @JsonProperty("previous")
    public abstract Double previous();

    @JsonProperty("samples")
    public abstract List<BriefingHistogramBucketResponse> samples();

    public static MetricWidgetResponse create(String metric, String title, String unit, String aggregation, double current, Double previous, List<BriefingHistogramBucketResponse> samples) {
        return builder()
                .metric(metric)
                .title(title)
                .unit(unit)
                .aggregation(aggregation)
                .current(current)
                .previous(previous)
                .samples(samples)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MetricWidgetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder metric(String metric);

        public abstract Builder title(String title);

        public abstract Builder unit(String unit);

        public abstract Builder aggregation(String aggregation);

        public abstract Builder current(double current);

        public abstract Builder previous(Double previous);

        public abstract Builder samples(List<BriefingHistogramBucketResponse> samples);

        public abstract MetricWidgetResponse build();
    }

}
