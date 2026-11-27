package app.nzyme.core.distributed;

import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class GarbageCollectionMonitor {

    public record Sample(double pauseTimePercent, OptionalDouble heapAfterCollectionPercent) { }

    private final List<GarbageCollectorMXBean> pausingCollectors = new ArrayList<>();
    private final Set<String> heapPools = new HashSet<>();

    private final AtomicLong lowestHeapAfterCollection = new AtomicLong(Long.MAX_VALUE);

    private final long usableHeap;

    private long previousPauseMs;
    private long previousSampleAt;

    public GarbageCollectionMonitor() {
        long survivorSpace = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                heapPools.add(pool.getName());

                if (pool.getName().contains("Survivor")) {
                    survivorSpace += Math.max(0, pool.getUsage().getMax());
                }
            }
        }

        /*
         * The serial and parallel collectors count one survivor space as heap, and it is empty after a full
         * collection. A full heap would never reach 100% with it. G1 has no survivor space of a fixed size.
         */
        long maximum = Runtime.getRuntime().maxMemory();
        this.usableHeap = maximum > survivorSpace ? maximum - survivorSpace : maximum;

        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            // ZGC and Shenandoah report their concurrent cycles as collectors of their own. Those do not stop the node.
            if (!collector.getName().contains("Cycles")) {
                pausingCollectors.add(collector);
            }

            if (collector instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> onCollection(notification), null, null);
            }
        }

        this.previousPauseMs = totalPauseMs();
        this.previousSampleAt = System.nanoTime();
    }

    // Covers the time since the previous call.
    public synchronized Sample sample() {
        long now = System.nanoTime();
        long pauseMs = totalPauseMs();
        double elapsedMs = (now - previousSampleAt) / 1_000_000.0;

        double pauseTimePercent = elapsedMs <= 0
                ? 0 : Math.max(0, Math.min(100, (pauseMs - previousPauseMs) * 100.0 / elapsedMs));

        this.previousPauseMs = pauseMs;
        this.previousSampleAt = now;

        long lowest = lowestHeapAfterCollection.getAndSet(Long.MAX_VALUE);

        return new Sample(pauseTimePercent, lowest == Long.MAX_VALUE || usableHeap <= 0
                ? OptionalDouble.empty() : OptionalDouble.of(Math.min(100, lowest * 100.0 / usableHeap)));
    }

    private long totalPauseMs() {
        long total = 0;
        for (GarbageCollectorMXBean collector : pausingCollectors) {
            total += Math.max(0, collector.getCollectionTime());
        }

        return total;
    }

    private void onCollection(Notification notification) {
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
            return;
        }

        Map<String, MemoryUsage> after = GarbageCollectionNotificationInfo
                .from((CompositeData) notification.getUserData())
                .getGcInfo()
                .getMemoryUsageAfterGc();

        long used = 0;
        for (Map.Entry<String, MemoryUsage> pool : after.entrySet()) {
            if (heapPools.contains(pool.getKey())) {
                used += pool.getValue().getUsed();
            }
        }

        // ZGC reports its pauses without memory usage.
        if (used > 0) {
            lowestHeapAfterCollection.accumulateAndGet(used, Math::min);
        }
    }

}
