package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.distributed.MetricExternalName;
import app.nzyme.core.distributed.Node;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import org.joda.time.DateTime;

import java.util.Optional;

// Unavailable while Connect is off.
public class ConnectIndicator extends Indicator {

    private static final int STALE_AFTER_MINUTES = 2;

    private final NzymeNode nzyme;

    public ConnectIndicator(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    protected IndicatorStatus doRun() {
        if (!nzyme.getConnect().isEnabled()) {
            return IndicatorStatus.unavailable(this);
        }

        Optional<String> lastReport = nzyme.getDatabaseCoreRegistry()
                .getValue(ConnectRegistryKeys.LAST_SUCCESSFUL_REPORT_SUBMISSION.key());

        if (lastReport.isEmpty()) {
            return IndicatorStatus.red(this);
        }

        if (DateTime.parse(lastReport.get()).isBefore(DateTime.now().minusMinutes(STALE_AFTER_MINUTES))) {
            return IndicatorStatus.red(this);
        }

        return nzyme.getDatabase().withHandle(handle -> {
            for (Node node : nzyme.getNodeManager().getNodes()) {
                if (node.deleted()) {
                    continue;
                }

                Optional<Double> failing = nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                        node.uuid(), MetricExternalName.CONNECT_DATA_FAILING.database_label, handle
                );

                if (failing.isPresent() && failing.get() > 0) {
                    return IndicatorStatus.red(this);
                }
            }

            return IndicatorStatus.green(this);
        });
    }

    @Override
    public String getId() {
        return "connect";
    }

    @Override
    public String getName() {
        return "Connect";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_CONNECT_TOGGLED;
    }

}
