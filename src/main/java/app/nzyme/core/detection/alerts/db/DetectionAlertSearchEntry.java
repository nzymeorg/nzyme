package app.nzyme.core.detection.alerts.db;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DetectionAlertSearchEntry {

    public abstract DetectionAlertEntry alert();
    public abstract String status();
    public abstract long reactivations();

    public static DetectionAlertSearchEntry create(DetectionAlertEntry alert, String status, long reactivations) {
        return builder()
                .alert(alert)
                .status(status)
                .reactivations(reactivations)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DetectionAlertSearchEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder alert(DetectionAlertEntry alert);

        public abstract Builder status(String status);

        public abstract Builder reactivations(long reactivations);

        public abstract DetectionAlertSearchEntry build();
    }
}
