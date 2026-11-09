package app.nzyme.core.rest.responses.system.database.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class DatabaseServerResponse {

    @JsonProperty("version")
    public abstract String version();

    @JsonProperty("version_number")
    public abstract int versionNumber();

    @JsonProperty("database_size_bytes")
    public abstract long databaseSizeBytes();

    @JsonProperty("heap_bytes")
    public abstract long heapBytes();

    @JsonProperty("index_bytes")
    public abstract long indexBytes();

    @JsonProperty("connections")
    public abstract int connections();

    @JsonProperty("max_connections")
    public abstract int maxConnections();

    @JsonProperty("cache_hit_ratio_recent")
    public abstract double cacheHitRatioRecent();

    @JsonProperty("cache_hit_ratio_total")
    public abstract double cacheHitRatioTotal();

    @JsonProperty("temp_bytes_per_minute")
    public abstract long tempBytesPerMinute();

    @JsonProperty("temp_files_per_minute")
    public abstract double tempFilesPerMinute();

    @JsonProperty("checkpoints_requested_ratio_recent")
    public abstract double checkpointsRequestedRatioRecent();

    @JsonProperty("checkpoints_recent")
    public abstract long checkpointsRecent();

    @JsonProperty("deadlocks_per_minute")
    public abstract double deadlocksPerMinute();

    @JsonProperty("hot_update_ratio_recent")
    public abstract double hotUpdateRatioRecent();

    @JsonProperty("xid_age")
    public abstract long xidAge();

    @JsonProperty("oldest_transaction_seconds")
    public abstract long oldestTransactionSeconds();

    @JsonProperty("io_timing_enabled")
    public abstract boolean ioTimingEnabled();

    @JsonProperty("io_read_wait_ms_per_minute")
    public abstract double ioReadWaitMsPerMinute();

    @JsonProperty("io_write_wait_ms_per_minute")
    public abstract double ioWriteWaitMsPerMinute();

    @Nullable
    @JsonProperty("statistics_since")
    public abstract DateTime statisticsSince();

    @JsonProperty("window_minutes")
    public abstract int windowMinutes();

    @JsonProperty("server_started_at")
    public abstract DateTime serverStartedAt();

    @JsonProperty("warming_up")
    public abstract boolean warmingUp();

    public static DatabaseServerResponse create(String version, int versionNumber, long databaseSizeBytes, long heapBytes, long indexBytes, int connections, int maxConnections, double cacheHitRatioRecent, double cacheHitRatioTotal, long tempBytesPerMinute, double tempFilesPerMinute, double checkpointsRequestedRatioRecent, long checkpointsRecent, double deadlocksPerMinute, double hotUpdateRatioRecent, long xidAge, long oldestTransactionSeconds, boolean ioTimingEnabled, double ioReadWaitMsPerMinute, double ioWriteWaitMsPerMinute, DateTime statisticsSince, int windowMinutes, DateTime serverStartedAt, boolean warmingUp) {
        return builder()
                .version(version)
                .versionNumber(versionNumber)
                .databaseSizeBytes(databaseSizeBytes)
                .heapBytes(heapBytes)
                .indexBytes(indexBytes)
                .connections(connections)
                .maxConnections(maxConnections)
                .cacheHitRatioRecent(cacheHitRatioRecent)
                .cacheHitRatioTotal(cacheHitRatioTotal)
                .tempBytesPerMinute(tempBytesPerMinute)
                .tempFilesPerMinute(tempFilesPerMinute)
                .checkpointsRequestedRatioRecent(checkpointsRequestedRatioRecent)
                .checkpointsRecent(checkpointsRecent)
                .deadlocksPerMinute(deadlocksPerMinute)
                .hotUpdateRatioRecent(hotUpdateRatioRecent)
                .xidAge(xidAge)
                .oldestTransactionSeconds(oldestTransactionSeconds)
                .ioTimingEnabled(ioTimingEnabled)
                .ioReadWaitMsPerMinute(ioReadWaitMsPerMinute)
                .ioWriteWaitMsPerMinute(ioWriteWaitMsPerMinute)
                .statisticsSince(statisticsSince)
                .windowMinutes(windowMinutes)
                .serverStartedAt(serverStartedAt)
                .warmingUp(warmingUp)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseServerResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder version(String version);
        public abstract Builder versionNumber(int versionNumber);
        public abstract Builder databaseSizeBytes(long databaseSizeBytes);
        public abstract Builder heapBytes(long heapBytes);
        public abstract Builder indexBytes(long indexBytes);
        public abstract Builder connections(int connections);
        public abstract Builder maxConnections(int maxConnections);
        public abstract Builder cacheHitRatioRecent(double cacheHitRatioRecent);
        public abstract Builder cacheHitRatioTotal(double cacheHitRatioTotal);
        public abstract Builder tempBytesPerMinute(long tempBytesPerMinute);
        public abstract Builder tempFilesPerMinute(double tempFilesPerMinute);
        public abstract Builder checkpointsRequestedRatioRecent(double checkpointsRequestedRatioRecent);
        public abstract Builder checkpointsRecent(long checkpointsRecent);
        public abstract Builder deadlocksPerMinute(double deadlocksPerMinute);
        public abstract Builder hotUpdateRatioRecent(double hotUpdateRatioRecent);
        public abstract Builder xidAge(long xidAge);
        public abstract Builder oldestTransactionSeconds(long oldestTransactionSeconds);
        public abstract Builder ioTimingEnabled(boolean ioTimingEnabled);
        public abstract Builder ioReadWaitMsPerMinute(double ioReadWaitMsPerMinute);
        public abstract Builder ioWriteWaitMsPerMinute(double ioWriteWaitMsPerMinute);
        public abstract Builder statisticsSince(DateTime statisticsSince);
        public abstract Builder windowMinutes(int windowMinutes);
        public abstract Builder serverStartedAt(DateTime serverStartedAt);
        public abstract Builder warmingUp(boolean warmingUp);

        public abstract DatabaseServerResponse build();
    }

}
