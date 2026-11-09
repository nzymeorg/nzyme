package app.nzyme.core.database.maintenance;

import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

public record DatabaseStatistics(DateTime collectedAt,
                                 ServerStatistics server,
                                 List<ServerSetting> settings,
                                 List<TableStatistics> tables,
                                 List<IndexStatistics> indexes) {

    public record ServerStatistics(String version,
                                   int versionNumber,
                                   long databaseSizeBytes,
                                   long heapBytes,
                                   long indexBytes,
                                   int connections,
                                   int maxConnections,
                                   // Negative if there were no reads in the window.
                                   double cacheHitRatioRecent,
                                   double cacheHitRatioTotal,
                                   long tempBytesPerMinute,
                                   double tempFilesPerMinute,
                                   // Negative if there were no checkpoints in the window.
                                   double checkpointsRequestedRatioRecent,
                                   long checkpointsRecent,
                                   double deadlocksPerMinute,
                                   // Negative if there were no updates in the window.
                                   double hotUpdateRatioRecent,
                                   long xidAge,
                                   long oldestTransactionSeconds,
                                   boolean ioTimingEnabled,
                                   double ioReadWaitMsPerMinute,
                                   double ioWriteWaitMsPerMinute,
                                   @Nullable DateTime statisticsSince,
                                   DateTime serverStartedAt,
                                   int windowMinutes) {

        public static final int WARM_UP_MINUTES = 15;

        public boolean isWarmingUp() {
            return serverStartedAt.isAfter(DateTime.now().minusMinutes(WARM_UP_MINUTES));
        }
    }

    public enum SettingSeverity {
        REQUIRED,
        RECOMMENDED
    }

    public enum SettingScope {
        SERVER,
        SESSION,
        TABLE
    }

    public record ServerSetting(String name,
                                String currentValue,
                                String recommendedValue,
                                boolean ok,
                                SettingSeverity severity,
                                SettingScope scope,
                                String description,
                                @Nullable String configLines,
                                boolean pendingRestart) {
        public ServerSetting(String name, String currentValue, String recommendedValue, boolean ok,
                             SettingSeverity severity, SettingScope scope, String description,
                             @Nullable String configLines) {
            this(name, currentValue, recommendedValue, ok, severity, scope, description, configLines, false);
        }

        public ServerSetting withPendingRestart(boolean pendingRestart) {
            return new ServerSetting(name, currentValue, recommendedValue, ok, severity, scope, description,
                    configLines, pendingRestart);
        }
    }

    public record TableStatistics(String name,
                                  @Nullable TableClass tableClass,
                                  long heapBytes,
                                  long indexBytes,
                                  long totalBytes,
                                  long liveTuples,
                                  long deadTuples,
                                  double deadRatioPercent,
                                  @Nullable DateTime lastVacuum,
                                  @Nullable DateTime lastAnalyze,
                                  long insertsTotal,
                                  long updatesTotal,
                                  long hotUpdatesTotal,
                                  long deletesTotal,
                                  long vacuumCount) { }

    public record IndexStatistics(String name,
                                  String table,
                                  long bytes,
                                  long scans,
                                  boolean primary,
                                  boolean unique,
                                  boolean valid,
                                  // Null for non-btree indexes and indexes without planner statistics.
                                  @Nullable Double bloatPercent,
                                  long bloatBytes) { }

}
