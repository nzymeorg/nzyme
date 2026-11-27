package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.distributed.Node;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.IndicatorStatusLevel;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import org.joda.time.DateTime;

import java.util.List;

public class NodeHeapIndicator extends Indicator {

    private final NzymeNode nzyme;

    public NodeHeapIndicator(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    protected IndicatorStatus doRun() {
        List<Node> nodes = nzyme.getNodeManager().getNodes();

        return nzyme.getDatabase().withHandle(handle -> {
            IndicatorStatus status = IndicatorStatus.green(this);
            for (Node node : nodes) {
                if (node.deleted() || node.lastSeen().isBefore(DateTime.now().minusMinutes(2))) {
                    continue;
                }

                status = switch (nzyme.getNodeManager().findHeapOfNode(node, handle).status()) {
                    case EXHAUSTED -> IndicatorStatus.red(this);
                    case FULL, GC_OVERHEAD, MEMORY_PRESSURE, NO_MEASUREMENTS -> IndicatorStatus.orange(this);
                    case OK -> status;
                };

                if (status.resultLevel() == IndicatorStatusLevel.RED) {
                    break;
                }
            }

            return status;
        });
    }

    @Override
    public String getId() {
        return "node_heap";
    }

    @Override
    public String getName() {
        return "Node Heap";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_NODE_HEAP_TOGGLED;
    }

}
