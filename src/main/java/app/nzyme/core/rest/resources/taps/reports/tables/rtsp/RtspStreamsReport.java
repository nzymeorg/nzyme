package app.nzyme.core.rest.resources.taps.reports.tables.rtsp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class RtspStreamsReport {

    public abstract List<RtspStreamReport> streams();

    @JsonCreator
    public static RtspStreamsReport create(@JsonProperty("streams") List<RtspStreamReport> sessions) {
        return builder()
                .streams(sessions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_RtspStreamsReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder streams(List<RtspStreamReport> streams);

        public abstract RtspStreamsReport build();
    }
}
