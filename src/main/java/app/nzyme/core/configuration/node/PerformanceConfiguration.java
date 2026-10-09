package app.nzyme.core.configuration.node;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class PerformanceConfiguration {

    public abstract int reportProcessorPoolSize();
    public abstract int databasePoolSize();

    public static PerformanceConfiguration create(int reportProcessorPoolSize, int databasePoolSize) {
        return builder()
                .reportProcessorPoolSize(reportProcessorPoolSize)
                .databasePoolSize(databasePoolSize)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PerformanceConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder reportProcessorPoolSize(int reportProcessorPoolSize);
        public abstract Builder databasePoolSize(int databasePoolSize);

        public abstract PerformanceConfiguration build();
    }
}
