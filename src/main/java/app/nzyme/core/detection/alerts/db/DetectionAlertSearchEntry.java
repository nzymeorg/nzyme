package app.nzyme.core.detection.alerts.db;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DetectionAlertSearchEntry {

    public abstract DetectionAlertEntry alert();
    public abstract String status();
    public abstract String locationName();
    public abstract long reactivations();

    public static DetectionAlertSearchEntry create(DetectionAlertEntry alert, String status, String locationName, long reactivations) {
        return builder()
                .alert(alert)
                .status(status)
                .locationName(locationName)
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

        public abstract Builder locationName(String locationName);

        public abstract Builder reactivations(long reactivations);

        public abstract DetectionAlertSearchEntry build();
    }
}
