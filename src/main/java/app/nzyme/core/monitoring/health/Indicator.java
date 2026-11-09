package app.nzyme.core.monitoring.health;

import org.joda.time.DateTime;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Indicator {

    /*
     * Returned by an indicator that cannot judge the cluster from this node right now, for example because this
     * node has not collected enough samples since it started. The stored status stays untouched instead of
     * flapping to UNAVAILABLE on every node restart.
     */
    public static final IndicatorStatus NO_RESULT = IndicatorStatus.create(
            "", "", new DateTime(0), IndicatorStatusLevel.UNAVAILABLE, false);


    private static final Logger LOG = LogManager.getLogger(Indicator.class);

    public IndicatorStatus run() {
        LOG.debug("Running health check indicator [{}].", getName());
        IndicatorStatus result = doRun();
        LOG.debug("Finished health check indicator run of [{}].", getName());

        return result;
    }

    protected abstract IndicatorStatus doRun();

    public abstract String getId();
    public abstract String getName();
    public abstract SystemEventType getSystemEventType();

}
