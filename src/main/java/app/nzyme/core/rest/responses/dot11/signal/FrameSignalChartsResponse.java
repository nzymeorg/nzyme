package app.nzyme.core.rest.responses.dot11.signal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class FrameSignalChartsResponse {

    @JsonProperty("source_count")
    public abstract long sourceCount();

    @JsonProperty("max_sources")
    public abstract int maxSources();

    @JsonProperty("sources")
    public abstract List<FrameSignalSourceResponse> sources();

    @JsonProperty("threshold")
    public abstract int threshold();

    @JsonProperty("charts")
    public abstract List<FrameSignalChartResponse> charts();

    @JsonProperty("bucket_seconds")
    public abstract long bucketSeconds();

    @JsonProperty("default_charts")
    public abstract int defaultCharts();

    @JsonProperty("max_frame_records_per_chart")
    public abstract int maxFrameRecordsPerChart();

    @JsonProperty("max_frame_records")
    public abstract int maxFrameRecords();

    @JsonProperty("is_truncated")
    public abstract boolean isTruncated();

    public static FrameSignalChartsResponse create(long sourceCount, int maxSources, List<FrameSignalSourceResponse> sources, int threshold, List<FrameSignalChartResponse> charts, long bucketSeconds, int defaultCharts, int maxFrameRecordsPerChart, int maxFrameRecords, boolean isTruncated) {
        return builder()
                .sourceCount(sourceCount)
                .maxSources(maxSources)
                .sources(sources)
                .threshold(threshold)
                .charts(charts)
                .bucketSeconds(bucketSeconds)
                .defaultCharts(defaultCharts)
                .maxFrameRecordsPerChart(maxFrameRecordsPerChart)
                .maxFrameRecords(maxFrameRecords)
                .isTruncated(isTruncated)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalChartsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sourceCount(long sourceCount);

        public abstract Builder maxSources(int maxSources);

        public abstract Builder sources(List<FrameSignalSourceResponse> sources);

        public abstract Builder threshold(int threshold);

        public abstract Builder charts(List<FrameSignalChartResponse> charts);

        public abstract Builder bucketSeconds(long bucketSeconds);

        public abstract Builder defaultCharts(int defaultCharts);

        public abstract Builder maxFrameRecordsPerChart(int maxFrameRecordsPerChart);

        public abstract Builder maxFrameRecords(int maxFrameRecords);

        public abstract Builder isTruncated(boolean isTruncated);

        public abstract FrameSignalChartsResponse build();
    }

}
