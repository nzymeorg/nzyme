package app.nzyme.core.distributed;

import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Supplier;

/*
 * Reads the pressure stall information (PSI) of the Linux kernel: the time tasks were stalled waiting for memory.
 * Kernels built without PSI have no such files and kernels with PSI switched off refuse to read them. Both are
 * common, so nothing in here may fail when the information is missing.
 */
public class MemoryPressureMonitor {

    private static final Logger LOG = LogManager.getLogger(MemoryPressureMonitor.class);

    private static final Path OWN_CGROUP = Path.of("/proc/self/cgroup");
    private static final Path CGROUPS = Path.of("/sys/fs/cgroup");
    private static final Path HOST = Path.of("/proc/pressure/memory");

    private final Supplier<Path> sourceFinder;

    @Nullable
    private Path source;

    private boolean hasPrevious;
    private long previousStallMicros;
    private long previousSampleAt;

    public MemoryPressureMonitor() {
        this(MemoryPressureMonitor::findSource);

        if (source == null) {
            LOG.info("Memory pressure is not measured. The kernel of this host does not provide pressure stall " +
                    "information (PSI) or has it switched off (kernel parameter psi=1 switches it on).");
        } else {
            LOG.info("Measuring memory pressure from [{}].", source);
        }
    }

    MemoryPressureMonitor(@Nullable Path source) {
        this(() -> source);
    }

    private MemoryPressureMonitor(Supplier<Path> sourceFinder) {
        this.sourceFinder = sourceFinder;
        startOver();
    }

    // Percent of the time since the previous call in which at least one task was stalled waiting for memory.
    public synchronized OptionalDouble sample() {
        OptionalLong stallMicros = source == null ? OptionalLong.empty() : readStallMicros(source);

        // The node may have been moved to another cgroup, or PSI was switched on or off.
        if (stallMicros.isEmpty() || !hasPrevious) {
            startOver();
            return OptionalDouble.empty();
        }

        long now = System.nanoTime();
        double elapsedMicros = (now - previousSampleAt) / 1_000.0;
        long stalled = stallMicros.getAsLong() - previousStallMicros;

        this.previousStallMicros = stallMicros.getAsLong();
        this.previousSampleAt = now;

        if (stalled < 0 || elapsedMicros <= 0) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(Math.min(100, stalled * 100.0 / elapsedMicros));
    }

    /*
     * The stall time is a counter. After a read that failed, the next value would hold the stalls of the whole gap
     * and report them as stalls of one interval.
     */
    private void startOver() {
        this.source = sourceFinder.get();

        OptionalLong stallMicros = source == null ? OptionalLong.empty() : readStallMicros(source);
        this.hasPrevious = stallMicros.isPresent();
        this.previousStallMicros = stallMicros.orElse(0);
        this.previousSampleAt = System.nanoTime();
    }

    /*
     * The cgroup of the node counts only the stalls of the node itself. The host file also counts every other
     * service and container on the host, and inside a container it describes the host the container runs on.
     */
    @Nullable
    private static Path findSource() {
        try {
            for (String line : Files.readAllLines(OWN_CGROUP)) {
                if (line.startsWith("0::")) {
                    Path own = Path.of(CGROUPS.toString(), line.substring(3).trim(), "memory.pressure").normalize();
                    if (own.startsWith(CGROUPS) && readStallMicros(own).isPresent()) {
                        return own;
                    }
                }
            }
        } catch (Exception ignored) {
            // The host file is the next best source.
        }

        return readStallMicros(HOST).isPresent() ? HOST : null;
    }

    static OptionalLong readStallMicros(Path file) {
        try {
            for (String line : Files.readAllLines(file)) {
                if (!line.startsWith("some ")) {
                    continue;
                }

                for (String field : line.split(" ")) {
                    if (field.startsWith("total=")) {
                        long total = Long.parseLong(field.substring(6));
                        return total < 0 ? OptionalLong.empty() : OptionalLong.of(total);
                    }
                }
            }
        } catch (Exception ignored) {
            // Missing file, PSI switched off (the read fails with "Operation not supported") or an unknown format.
        }

        return OptionalLong.empty();
    }

}
