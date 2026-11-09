package app.nzyme.core.rest.responses.system.database.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class DatabaseTableResponse {

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("table_class")
    public abstract String tableClass();

    @Nullable
    @JsonProperty("table_class_title")
    public abstract String tableClassTitle();

    @JsonProperty("heap_bytes")
    public abstract long heapBytes();

    @JsonProperty("index_bytes")
    public abstract long indexBytes();

    @JsonProperty("total_bytes")
    public abstract long totalBytes();

    @JsonProperty("live_tuples")
    public abstract long liveTuples();

    @JsonProperty("dead_tuples")
    public abstract long deadTuples();

    @JsonProperty("dead_ratio_percent")
    public abstract double deadRatioPercent();

    @Nullable
    @JsonProperty("last_vacuum")
    public abstract DateTime lastVacuum();

    @Nullable
    @JsonProperty("last_analyze")
    public abstract DateTime lastAnalyze();

    @JsonProperty("inserts_total")
    public abstract long insertsTotal();

    @JsonProperty("updates_total")
    public abstract long updatesTotal();

    @JsonProperty("hot_updates_total")
    public abstract long hotUpdatesTotal();

    @JsonProperty("deletes_total")
    public abstract long deletesTotal();

    @JsonProperty("vacuum_count")
    public abstract long vacuumCount();

    public static DatabaseTableResponse create(String name, String tableClass, String tableClassTitle, long heapBytes, long indexBytes, long totalBytes, long liveTuples, long deadTuples, double deadRatioPercent, DateTime lastVacuum, DateTime lastAnalyze, long insertsTotal, long updatesTotal, long hotUpdatesTotal, long deletesTotal, long vacuumCount) {
        return builder()
                .name(name)
                .tableClass(tableClass)
                .tableClassTitle(tableClassTitle)
                .heapBytes(heapBytes)
                .indexBytes(indexBytes)
                .totalBytes(totalBytes)
                .liveTuples(liveTuples)
                .deadTuples(deadTuples)
                .deadRatioPercent(deadRatioPercent)
                .lastVacuum(lastVacuum)
                .lastAnalyze(lastAnalyze)
                .insertsTotal(insertsTotal)
                .updatesTotal(updatesTotal)
                .hotUpdatesTotal(hotUpdatesTotal)
                .deletesTotal(deletesTotal)
                .vacuumCount(vacuumCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseTableResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder tableClass(String tableClass);
        public abstract Builder tableClassTitle(String tableClassTitle);
        public abstract Builder heapBytes(long heapBytes);
        public abstract Builder indexBytes(long indexBytes);
        public abstract Builder totalBytes(long totalBytes);
        public abstract Builder liveTuples(long liveTuples);
        public abstract Builder deadTuples(long deadTuples);
        public abstract Builder deadRatioPercent(double deadRatioPercent);
        public abstract Builder lastVacuum(DateTime lastVacuum);
        public abstract Builder lastAnalyze(DateTime lastAnalyze);
        public abstract Builder insertsTotal(long insertsTotal);
        public abstract Builder updatesTotal(long updatesTotal);
        public abstract Builder hotUpdatesTotal(long hotUpdatesTotal);
        public abstract Builder deletesTotal(long deletesTotal);
        public abstract Builder vacuumCount(long vacuumCount);

        public abstract DatabaseTableResponse build();
    }

}
