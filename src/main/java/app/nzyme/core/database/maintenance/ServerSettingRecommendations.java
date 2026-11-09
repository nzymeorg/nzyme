package app.nzyme.core.database.maintenance;

import app.nzyme.core.database.maintenance.DatabaseStatistics.ServerSetting;
import app.nzyme.core.database.maintenance.DatabaseStatistics.SettingScope;
import app.nzyme.core.database.maintenance.DatabaseStatistics.SettingSeverity;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

public class ServerSettingRecommendations {

    // Set by the SQL: trim_array() needs 14, gen_random_uuid() defaults and pg_trgm without superuser need 13, REINDEX CONCURRENTLY needs 12.
    public static final int REQUIRED_VERSION = 140000;

    // Upstream support: 14 ends November 2026, 15 November 2027. Also the oldest release in the installation guides.
    public static final int RECOMMENDED_VERSION = 150000;

    public static String majorVersion(int versionNumber) {
        return String.valueOf(versionNumber / 10000);
    }

    public static final long GB = 1024L * 1024L * 1024L;
    public static final long MB = 1024L * 1024L;

    public static final List<String> SETTING_NAMES = List.of(
            "autovacuum", "shared_buffers", "effective_cache_size", "maintenance_work_mem", "work_mem",
            "autovacuum_vacuum_cost_delay", "autovacuum_vacuum_cost_limit", "vacuum_cost_limit",
            "autovacuum_max_workers", "max_wal_size", "checkpoint_timeout", "random_page_cost", "track_io_timing",
            "jit", "max_connections"
    );

    public record Setting(String name, String setting, @Nullable String unit, String source,
                          boolean pendingRestart) {
        public Setting(String name, String setting, @Nullable String unit, String source) {
            this(name, setting, unit, source, false);
        }
    }

    public record Input(Map<String, Setting> settings,
                        int versionNumber,
                        String version,
                        OptionalLong hostMemoryBytes,
                        boolean sessionTuningEnabled,
                        int sessionWorkMemMb,
                        boolean tableTuningEnabled) { }

    public static List<ServerSetting> evaluate(Input in) {
        List<ServerSetting> result = new java.util.ArrayList<>();

        result.add(new ServerSetting(
                "server_version",
                in.version(),
                "PostgreSQL " + majorVersion(RECOMMENDED_VERSION) + " or newer",
                in.versionNumber() >= RECOMMENDED_VERSION,
                SettingSeverity.REQUIRED,
                SettingScope.SERVER,
                "Releases older than " + majorVersion(RECOMMENDED_VERSION) + " are at or past the end of upstream " +
                        "support. Nzyme requires at least " + majorVersion(REQUIRED_VERSION) + ".",
                null
        ));

        boolean autovacuum = bool(in, "autovacuum", true);
        result.add(new ServerSetting(
                "autovacuum",
                autovacuum ? "on" : "off",
                "on",
                autovacuum,
                SettingSeverity.REQUIRED,
                SettingScope.SERVER,
                "Reclaims the space of updated and deleted rows and keeps planner statistics current. Required.",
                autovacuum ? null : "autovacuum = on"
        ));

        long sharedBuffers = bytes(in, "shared_buffers", 128 * MB);
        long recommendedSharedBuffers = in.hostMemoryBytes().isPresent()
                ? Math.max(GB, roundToGb(in.hostMemoryBytes().getAsLong() / 4))
                : GB;
        boolean sharedBuffersOk = sharedBuffers >= GB;
        result.add(new ServerSetting(
                "shared_buffers",
                human(sharedBuffers),
                in.hostMemoryBytes().isPresent()
                        ? human(recommendedSharedBuffers) + " (a quarter of the " +
                        human(in.hostMemoryBytes().getAsLong()) + " of this machine)"
                        : "A quarter of the memory of the database server, at least 1GB",
                sharedBuffersOk,
                SettingSeverity.REQUIRED,
                SettingScope.SERVER,
                "Memory for caching table and index pages. The cache hit ratio should stay above 95 percent. " +
                        "Requires a PostgreSQL restart.",
                sharedBuffersOk ? null : "shared_buffers = " + humanConfig(recommendedSharedBuffers)
        ));

        Setting ecs = in.settings().get("effective_cache_size");
        long effectiveCacheSize = bytes(in, "effective_cache_size", 4 * GB);
        boolean ecsOk = ecs != null && !"default".equals(ecs.source());
        long recommendedEcs = in.hostMemoryBytes().isPresent()
                ? Math.max(GB, roundToGb(in.hostMemoryBytes().getAsLong() * 3 / 4))
                : 3 * GB;
        result.add(new ServerSetting(
                "effective_cache_size",
                human(effectiveCacheSize) + (ecsOk ? "" : " (default)"),
                in.hostMemoryBytes().isPresent()
                        ? human(recommendedEcs) + " (three quarters of the memory of this machine)"
                        : "Half to three quarters of the memory of the database server",
                ecsOk,
                SettingSeverity.RECOMMENDED,
                SettingScope.SERVER,
                "Planner estimate of the data cached by the operating system. Does not allocate memory. Too low " +
                        "a value makes the planner avoid indexes.",
                ecsOk ? null : "effective_cache_size = " + humanConfig(recommendedEcs)
        ));

        long mwm = bytes(in, "maintenance_work_mem", 64 * MB);
        boolean mwmOk = mwm >= 256 * MB;
        result.add(new ServerSetting(
                "maintenance_work_mem",
                human(mwm),
                "512MB to 1GB",
                mwmOk,
                SettingSeverity.REQUIRED,
                SettingScope.SERVER,
                "Memory for a single vacuum or index build. Too low a value makes vacuum scan the indexes of large " +
                        "tables several times per run.",
                mwmOk ? null : "maintenance_work_mem = 512MB"
        ));

        double costDelayMs = millis(in, "autovacuum_vacuum_cost_delay", 2.0);
        long autovacuumCostLimit = number(in, "autovacuum_vacuum_cost_limit", -1);
        long effectiveCostLimit = autovacuumCostLimit > 0 ? autovacuumCostLimit : number(in, "vacuum_cost_limit", 200);
        boolean throttleOk = costDelayMs <= 1.0 || effectiveCostLimit >= 1000;
        result.add(new ServerSetting(
                "autovacuum_vacuum_cost_delay",
                formatMillis(costDelayMs) + " delay, cost limit " + effectiveCostLimit,
                "1ms or less, or a cost limit of 1000 or more",
                throttleOk,
                SettingSeverity.REQUIRED,
                SettingScope.SERVER,
                "Throttling of autovacuum. The defaults pause it every few pages. Nzyme requires it to run at " +
                        "nearly full speed.",
                throttleOk ? null : "autovacuum_vacuum_cost_delay = 1ms\nautovacuum_vacuum_cost_limit = 2000"
        ));

        long workers = number(in, "autovacuum_max_workers", 3);
        boolean workersOk = workers >= 4;
        result.add(new ServerSetting(
                "autovacuum_max_workers",
                String.valueOf(workers),
                "4 to 6",
                workersOk,
                SettingSeverity.REQUIRED,
                SettingScope.SERVER,
                "Number of tables autovacuum cleans at the same time. Requires a PostgreSQL restart.",
                workersOk ? null : "autovacuum_max_workers = 6"
        ));

        long maxWal = bytes(in, "max_wal_size", GB);
        boolean maxWalOk = maxWal >= 4 * GB;
        result.add(new ServerSetting(
                "max_wal_size",
                human(maxWal),
                "4GB to 8GB",
                maxWalOk,
                SettingSeverity.REQUIRED,
                SettingScope.SERVER,
                "Write-ahead log volume that forces a checkpoint. Too low a value causes constant checkpoints " +
                        "under Nzyme write load. The share of forced checkpoints should stay below 10 percent.",
                maxWalOk ? null : "max_wal_size = 8GB"
        ));

        double checkpointTimeout = seconds(in, "checkpoint_timeout", 300);
        boolean checkpointOk = checkpointTimeout >= 900;
        result.add(new ServerSetting(
                "checkpoint_timeout",
                formatSeconds(checkpointTimeout),
                "15min",
                checkpointOk,
                SettingSeverity.RECOMMENDED,
                SettingScope.SERVER,
                "Maximum time between two checkpoints. Longer intervals reduce repeated writes of hot pages and " +
                        "increase crash recovery time.",
                checkpointOk ? null : "checkpoint_timeout = 15min"
        ));

        double rpc = decimal(in, "random_page_cost", 4.0);
        boolean rpcOk = rpc <= 1.5;
        result.add(new ServerSetting(
                "random_page_cost",
                formatDecimal(rpc),
                "1.1 on SSD storage",
                rpcOk,
                SettingSeverity.RECOMMENDED,
                SettingScope.SERVER,
                "Planner cost of a random disk read relative to a sequential read. The default of 4 describes " +
                        "spinning disks. Keep 4 on spinning disks.",
                rpcOk ? null : "random_page_cost = 1.1"
        ));

        boolean trackIo = bool(in, "track_io_timing", false);
        result.add(new ServerSetting(
                "track_io_timing",
                trackIo ? "on" : "off",
                "on",
                trackIo,
                SettingSeverity.RECOMMENDED,
                SettingScope.SERVER,
                "Measures time spent waiting for disk. Required for the disk wait metric and the Database " +
                        "Performance indicator.",
                trackIo ? null : "track_io_timing = on"
        ));

        long maxConnections = number(in, "max_connections", 100);
        result.add(new ServerSetting(
                "max_connections",
                String.valueOf(maxConnections),
                "Larger than the sum of the connection pool sizes of all Nzyme nodes, plus a margin of 20",
                true,
                SettingSeverity.RECOMMENDED,
                SettingScope.SERVER,
                "Number of connections the server accepts. Each Nzyme node opens up to its configured database " +
                        "pool size.",
                null
        ));

        long workMem = bytes(in, "work_mem", 4 * MB);
        result.add(new ServerSetting(
                "work_mem",
                human(workMem) + " on the server, " + (in.sessionTuningEnabled()
                        ? in.sessionWorkMemMb() + "MB on Nzyme connections" : "not changed by Nzyme"),
                in.sessionTuningEnabled() ? in.sessionWorkMemMb() + "MB on Nzyme connections" : "64MB",
                in.sessionTuningEnabled() || workMem >= 32 * MB,
                SettingSeverity.RECOMMENDED,
                SettingScope.SESSION,
                "Memory for a single sort or hash operation before it spills to temporary files. Set by Nzyme on " +
                        "its own connections. Raise it in the database settings if temporary file volume is high.",
                null
        ));

        boolean jit = bool(in, "jit", true);
        result.add(new ServerSetting(
                "jit",
                (jit ? "on" : "off") + " on the server, " + (in.sessionTuningEnabled()
                        ? "off on Nzyme connections" : "not changed by Nzyme"),
                "off on Nzyme connections",
                in.sessionTuningEnabled() || !jit,
                SettingSeverity.RECOMMENDED,
                SettingScope.SESSION,
                "Just-in-time compilation of query expressions. Slows down short queries. Disabled by Nzyme on its " +
                        "own connections.",
                null
        ));

        result.add(new ServerSetting(
                "table storage parameters",
                in.tableTuningEnabled() ? "managed by Nzyme" : "disabled in the maintenance settings",
                "managed by Nzyme",
                in.tableTuningEnabled(),
                SettingSeverity.RECOMMENDED,
                SettingScope.TABLE,
                "Autovacuum thresholds and page fill factors per table class. Applied by Nzyme.",
                null
        ));

        List<ServerSetting> marked = new java.util.ArrayList<>();
        for (ServerSetting s : result) {
            Setting live = in.settings().get(s.name());
            marked.add(live != null && live.pendingRestart() ? s.withPendingRestart(true) : s);
        }

        return marked;
    }

    private static boolean bool(Input in, String name, boolean defaultValue) {
        Setting s = in.settings().get(name);
        if (s == null) {
            return defaultValue;
        }

        return "on".equalsIgnoreCase(s.setting()) || "true".equalsIgnoreCase(s.setting());
    }

    private static long number(Input in, String name, long defaultValue) {
        Setting s = in.settings().get(name);
        if (s == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(s.setting());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double decimal(Input in, String name, double defaultValue) {
        Setting s = in.settings().get(name);
        if (s == null) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(s.setting());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // pg_settings reports memory as a number with a unit of 8kB, kB, MB or GB.
    public static long bytes(Input in, String name, long defaultValue) {
        Setting s = in.settings().get(name);
        if (s == null) {
            return defaultValue;
        }

        long value;
        try {
            value = Long.parseLong(s.setting());
        } catch (NumberFormatException e) {
            return defaultValue;
        }

        if (s.unit() == null) {
            return value;
        }

        return switch (s.unit()) {
            case "8kB" -> value * 8 * 1024L;
            case "kB" -> value * 1024L;
            case "MB" -> value * MB;
            case "GB" -> value * GB;
            case "TB" -> value * GB * 1024L;
            case "B" -> value;
            default -> value;
        };
    }

    public static double millis(Input in, String name, double defaultValue) {
        Setting s = in.settings().get(name);
        if (s == null) {
            return defaultValue;
        }

        double value;
        try {
            value = Double.parseDouble(s.setting());
        } catch (NumberFormatException e) {
            return defaultValue;
        }

        if (s.unit() == null) {
            return value;
        }

        return switch (s.unit()) {
            case "us" -> value / 1000.0;
            case "ms" -> value;
            case "s" -> value * 1000.0;
            case "min" -> value * 60_000.0;
            default -> value;
        };
    }

    public static double seconds(Input in, String name, double defaultValue) {
        Setting s = in.settings().get(name);
        if (s == null) {
            return defaultValue;
        }

        double value;
        try {
            value = Double.parseDouble(s.setting());
        } catch (NumberFormatException e) {
            return defaultValue;
        }

        if (s.unit() == null) {
            return value;
        }

        return switch (s.unit()) {
            case "ms" -> value / 1000.0;
            case "s" -> value;
            case "min" -> value * 60.0;
            case "h" -> value * 3600.0;
            case "d" -> value * 86400.0;
            default -> value;
        };
    }

    private static long roundToGb(long bytes) {
        return Math.max(GB, Math.round((double) bytes / GB) * GB);
    }

    public static String human(long bytes) {
        if (bytes >= GB) {
            double gb = (double) bytes / GB;
            return gb == Math.floor(gb) ? (long) gb + "GB" : String.format(Locale.ROOT, "%.1fGB", gb);
        }

        if (bytes >= MB) {
            return (bytes / MB) + "MB";
        }

        return (bytes / 1024) + "kB";
    }

    private static String humanConfig(long bytes) {
        return human(bytes);
    }

    private static String formatMillis(double ms) {
        return ms == Math.floor(ms) ? (long) ms + "ms" : String.format(Locale.ROOT, "%.1fms", ms);
    }

    private static String formatSeconds(double seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (long) (seconds / 60) + "min";
        }

        return (long) seconds + "s";
    }

    private static String formatDecimal(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

}
