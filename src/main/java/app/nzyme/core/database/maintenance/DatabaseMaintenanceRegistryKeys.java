package app.nzyme.core.database.maintenance;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseMaintenanceRegistryKeys {

    private static final String PREFIX = "database_maintenance_";

    public static final RegistryKey TABLE_TUNING_ENABLED = bool(PREFIX + "table_tuning_enabled", true, false);
    public static final RegistryKey AUTO_VACUUM_ENABLED = bool(PREFIX + "auto_vacuum_enabled", true, false);
    public static final RegistryKey AUTO_REINDEX_ENABLED = bool(PREFIX + "auto_reindex_enabled", true, false);
    public static final RegistryKey REINDEX_BLOAT_THRESHOLD_PERCENT =
            number(PREFIX + "reindex_bloat_threshold_percent", 60, 20, 95, false);
    public static final RegistryKey REINDEX_MINIMUM_INDEX_MB =
            number(PREFIX + "reindex_minimum_index_mb", 100, 1, 1_000_000, false);
    public static final RegistryKey MAINTENANCE_WINDOW_START_HOUR =
            number(PREFIX + "window_start_hour", 2, 0, 23, false);
    public static final RegistryKey MAINTENANCE_WINDOW_HOURS =
            number(PREFIX + "window_hours", 4, 1, 24, false);
    public static final RegistryKey SESSION_TUNING_ENABLED = bool(PREFIX + "session_tuning_enabled", true, true);
    public static final RegistryKey SESSION_WORK_MEM_MB =
            number(PREFIX + "session_work_mem_mb", 64, 4, 4096, true);

    public static RegistryKey vacuumScalePercent(TableClass c) {
        return number(PREFIX + c.getKey() + "_vacuum_scale_percent", c.getDefaultVacuumScalePercent(), 1, 100, false);
    }

    public static RegistryKey vacuumThreshold(TableClass c) {
        return number(PREFIX + c.getKey() + "_vacuum_threshold", c.getDefaultVacuumThreshold(), 0, 10_000_000, false);
    }

    public static RegistryKey analyzeScalePercent(TableClass c) {
        return number(PREFIX + c.getKey() + "_analyze_scale_percent", c.getDefaultAnalyzeScalePercent(), 1, 100, false);
    }

    public static RegistryKey fillfactor(TableClass c) {
        return number(PREFIX + c.getKey() + "_fillfactor", c.getDefaultFillfactor(), 10, 100, false);
    }

    public static List<RegistryKey> allKeys() {
        List<RegistryKey> keys = new ArrayList<>(List.of(
                TABLE_TUNING_ENABLED, AUTO_VACUUM_ENABLED, AUTO_REINDEX_ENABLED, REINDEX_BLOAT_THRESHOLD_PERCENT,
                REINDEX_MINIMUM_INDEX_MB, MAINTENANCE_WINDOW_START_HOUR, MAINTENANCE_WINDOW_HOURS,
                SESSION_TUNING_ENABLED, SESSION_WORK_MEM_MB
        ));

        for (TableClass c : TableClass.values()) {
            keys.add(vacuumScalePercent(c));
            keys.add(vacuumThreshold(c));
            keys.add(analyzeScalePercent(c));
            keys.add(fillfactor(c));
        }

        return keys;
    }

    private static RegistryKey bool(String key, boolean defaultValue, boolean requiresRestart) {
        return RegistryKey.create(
                key,
                Optional.of(new ArrayList<>() {{
                    add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
                }}),
                Optional.of(String.valueOf(defaultValue)),
                requiresRestart
        );
    }

    private static RegistryKey number(String key, int defaultValue, int min, int max, boolean requiresRestart) {
        return RegistryKey.create(
                key,
                Optional.of(new ArrayList<>() {{
                    add(ConfigurationEntryConstraint.createNumberRangeConstraint(min, max));
                }}),
                Optional.of(String.valueOf(defaultValue)),
                requiresRestart
        );
    }

}
