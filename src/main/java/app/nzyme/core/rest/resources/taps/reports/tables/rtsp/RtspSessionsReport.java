package app.nzyme.core.rest.resources.taps.reports.tables.rtsp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class RtspSessionsReport {

    public abstract List<RtspSessionReport> sessions();

    @JsonCreator
    public static RtspSessionsReport create(@JsonProperty("sessions") List<RtspSessionReport> sessions) {
        return builder()
                .sessions(sessions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_RtspSessionsReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sessions(List<RtspSessionReport> sessions);

        public abstract RtspSessionsReport build();
    }
}
