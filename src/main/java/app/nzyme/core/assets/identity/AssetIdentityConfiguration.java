package app.nzyme.core.assets.identity;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class AssetIdentityConfiguration {

    public abstract boolean duplicateDetectionEnabled();
    public abstract int duplicateWindowMinutes();
    public abstract int duplicateMinimumSessions();
    public abstract boolean duplicateRequireStrongDifference();
    public abstract boolean duplicateAlertOnNew();

    public abstract boolean changeDetectionEnabled();
    public abstract int changeWindowHours();
    public abstract int changeMinimumAssetAgeHours();
    public abstract int changeScoreThreshold();
    public abstract boolean changeAlertOnNew();

    public abstract boolean successorDetectionEnabled();
    public abstract int successorLookbackHours();
    public abstract int successorQuietMinutes();
    public abstract int successorMinimumConfidence();
    public abstract boolean successorAlertOnNew();

    public abstract int retentionTimeDays();

    public static AssetIdentityConfiguration create(boolean duplicateDetectionEnabled, int duplicateWindowMinutes, int duplicateMinimumSessions, boolean duplicateRequireStrongDifference, boolean duplicateAlertOnNew, boolean changeDetectionEnabled, int changeWindowHours, int changeMinimumAssetAgeHours, int changeScoreThreshold, boolean changeAlertOnNew, boolean successorDetectionEnabled, int successorLookbackHours, int successorQuietMinutes, int successorMinimumConfidence, boolean successorAlertOnNew, int retentionTimeDays) {
        return builder()
                .duplicateDetectionEnabled(duplicateDetectionEnabled)
                .duplicateWindowMinutes(duplicateWindowMinutes)
                .duplicateMinimumSessions(duplicateMinimumSessions)
                .duplicateRequireStrongDifference(duplicateRequireStrongDifference)
                .duplicateAlertOnNew(duplicateAlertOnNew)
                .changeDetectionEnabled(changeDetectionEnabled)
                .changeWindowHours(changeWindowHours)
                .changeMinimumAssetAgeHours(changeMinimumAssetAgeHours)
                .changeScoreThreshold(changeScoreThreshold)
                .changeAlertOnNew(changeAlertOnNew)
                .successorDetectionEnabled(successorDetectionEnabled)
                .successorLookbackHours(successorLookbackHours)
                .successorQuietMinutes(successorQuietMinutes)
                .successorMinimumConfidence(successorMinimumConfidence)
                .successorAlertOnNew(successorAlertOnNew)
                .retentionTimeDays(retentionTimeDays)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetIdentityConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder duplicateDetectionEnabled(boolean duplicateDetectionEnabled);
        public abstract Builder duplicateWindowMinutes(int duplicateWindowMinutes);
        public abstract Builder duplicateMinimumSessions(int duplicateMinimumSessions);
        public abstract Builder duplicateRequireStrongDifference(boolean duplicateRequireStrongDifference);
        public abstract Builder duplicateAlertOnNew(boolean duplicateAlertOnNew);
        public abstract Builder changeDetectionEnabled(boolean changeDetectionEnabled);
        public abstract Builder changeWindowHours(int changeWindowHours);
        public abstract Builder changeMinimumAssetAgeHours(int changeMinimumAssetAgeHours);
        public abstract Builder changeScoreThreshold(int changeScoreThreshold);
        public abstract Builder changeAlertOnNew(boolean changeAlertOnNew);
        public abstract Builder successorDetectionEnabled(boolean successorDetectionEnabled);
        public abstract Builder successorLookbackHours(int successorLookbackHours);
        public abstract Builder successorQuietMinutes(int successorQuietMinutes);
        public abstract Builder successorMinimumConfidence(int successorMinimumConfidence);
        public abstract Builder successorAlertOnNew(boolean successorAlertOnNew);
        public abstract Builder retentionTimeDays(int retentionTimeDays);
        public abstract AssetIdentityConfiguration build();
    }
}
