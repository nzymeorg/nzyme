package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.ethernet.segmentation.Segmentation;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import app.nzyme.core.util.TenantCacheKey;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// A tenant that is behind but catching up is expected: a first run reads back in time, and so does the first run
// after a break.
public class SegmentationLagIndicator extends Indicator {

    private static final int BEHIND_AFTER_MINUTES = 20;
    private static final int STUCK_AFTER_MINUTES = 15;

    private final Segmentation segmentation;

    private final DateTime startedAt = DateTime.now();

    private final Map<TenantCacheKey, Long> previousLag = new ConcurrentHashMap<>();

    public SegmentationLagIndicator(Segmentation segmentation) {
        this.segmentation = segmentation;
    }

    @Override
    protected IndicatorStatus doRun() {
        DateTime now = DateTime.now();
        List<Segmentation.Progress> all = segmentation.progress();

        boolean behind = false;
        DateTime lastProgress = null;
        for (Segmentation.Progress progress : all) {
            if (lastProgress == null || progress.updatedAt().isAfter(lastProgress)) {
                lastProgress = progress.updatedAt();
            }

            long lag = (now.getMillis() - progress.watermark().getMillis()) / 1000;
            Long previous = previousLag.put(
                    TenantCacheKey.create(progress.organizationId(), progress.tenantId()), lag);
            if (lag > BEHIND_AFTER_MINUTES * 60 && previous != null && lag >= previous) {
                behind = true;
            }
        }

        if (lastProgress != null && lastProgress.isBefore(now.minusMinutes(STUCK_AFTER_MINUTES))
                && startedAt.isBefore(now.minusMinutes(STUCK_AFTER_MINUTES))) {
            return IndicatorStatus.red(this);
        }

        return behind ? IndicatorStatus.orange(this) : IndicatorStatus.green(this);
    }

    @Override
    public String getId() {
        return "segmentation_lag";
    }

    @Override
    public String getName() {
        return "Segmentation Lag";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_SEGMENTATION_LAG_TOGGLED;
    }

}
