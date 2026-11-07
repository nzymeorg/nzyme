package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.assets.identity.AssetIdentity;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import org.joda.time.DateTime;

public class AssetIdentityBacklogIndicator extends Indicator {

    private static final int ORANGE_AFTER_MINUTES = 15;
    private static final int RED_AFTER_MINUTES = 120;

    private final AssetIdentity identity;

    public AssetIdentityBacklogIndicator(AssetIdentity identity) {
        this.identity = identity;
    }

    @Override
    protected IndicatorStatus doRun() {
        AssetIdentity.SuccessorBacklog backlog = identity.successorBacklog();

        if (backlog.dueChecks() == 0 || backlog.oldestDueSince() == null) {
            return IndicatorStatus.green(this);
        }

        DateTime oldest = backlog.oldestDueSince();
        if (oldest.isBefore(DateTime.now().minusMinutes(RED_AFTER_MINUTES))) {
            return IndicatorStatus.red(this);
        }

        if (oldest.isBefore(DateTime.now().minusMinutes(ORANGE_AFTER_MINUTES))) {
            return IndicatorStatus.orange(this);
        }

        return IndicatorStatus.green(this);
    }

    @Override
    public String getId() {
        return "asset_identity_backlog";
    }

    @Override
    public String getName() {
        return "Asset Identity Backlog";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_ASSET_IDENTITY_BACKLOG_TOGGLED;
    }

}
