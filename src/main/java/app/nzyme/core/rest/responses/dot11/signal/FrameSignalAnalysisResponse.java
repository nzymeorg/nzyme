package app.nzyme.core.rest.responses.dot11.signal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import org.joda.time.DateTime;

@AutoValue
public abstract class FrameSignalAnalysisResponse {

    @JsonProperty("source_count")
    public abstract long sourceCount();

    @JsonProperty("max_sources")
    public abstract int maxSources();

    @JsonProperty("sources")
    public abstract List<FrameSignalSourceResponse> sources();

    @JsonProperty("threshold")
    public abstract int threshold();

    @JsonProperty("finding_count")
    public abstract long findingCount();

    @JsonProperty("findings")
    public abstract List<FrameSignalFindingResponse> findings();

    @JsonProperty("is_truncated")
    public abstract boolean isTruncated();

    @JsonProperty("max_frame_records")
    public abstract int maxFrameRecords();

    @JsonProperty("max_charts")
    public abstract int maxCharts();

    @JsonProperty("access_point_reference_minutes")
    public abstract int accessPointReferenceMinutes();

    @JsonProperty("client_comparison_minutes")
    public abstract int clientComparisonMinutes();

    @Nullable
    @JsonProperty("first_frame")
    public abstract DateTime firstFrame();

    @Nullable
    @JsonProperty("last_frame")
    public abstract DateTime lastFrame();

    public static FrameSignalAnalysisResponse create(long sourceCount, int maxSources, List<FrameSignalSourceResponse> sources, int threshold, long findingCount, List<FrameSignalFindingResponse> findings, boolean isTruncated, int maxFrameRecords, int maxCharts, int accessPointReferenceMinutes, int clientComparisonMinutes, DateTime firstFrame, DateTime lastFrame) {
        return builder()
                .sourceCount(sourceCount)
                .maxSources(maxSources)
                .sources(sources)
                .threshold(threshold)
                .findingCount(findingCount)
                .findings(findings)
                .isTruncated(isTruncated)
                .maxFrameRecords(maxFrameRecords)
                .maxCharts(maxCharts)
                .accessPointReferenceMinutes(accessPointReferenceMinutes)
                .clientComparisonMinutes(clientComparisonMinutes)
                .firstFrame(firstFrame)
                .lastFrame(lastFrame)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalAnalysisResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sourceCount(long sourceCount);

        public abstract Builder maxSources(int maxSources);

        public abstract Builder sources(List<FrameSignalSourceResponse> sources);

        public abstract Builder threshold(int threshold);

        public abstract Builder findingCount(long findingCount);

        public abstract Builder findings(List<FrameSignalFindingResponse> findings);

        public abstract Builder isTruncated(boolean isTruncated);

        public abstract Builder maxFrameRecords(int maxFrameRecords);

        public abstract Builder maxCharts(int maxCharts);

        public abstract Builder accessPointReferenceMinutes(int accessPointReferenceMinutes);

        public abstract Builder clientComparisonMinutes(int clientComparisonMinutes);

        public abstract Builder firstFrame(DateTime firstFrame);

        public abstract Builder lastFrame(DateTime lastFrame);

        public abstract FrameSignalAnalysisResponse build();
    }

}
