/*
 * This file is part of nzyme.
 *
 * nzyme is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * nzyme is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.distributed.MetricExternalName;
import app.nzyme.core.distributed.Node;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;

import java.util.Optional;

/**
 * Every node checks the permissions of its own secret-holding files once per minute and reports the number of
 * problems as a node gauge. Nodes refuse to start with such permissions, so this only triggers when they are
 * loosened while a node is running.
 */
public class NodeFilePermissionsIndicator extends Indicator {

    private final NzymeNode nzyme;

    public NodeFilePermissionsIndicator(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    protected IndicatorStatus doRun() {
        return nzyme.getDatabase().withHandle(handle -> {
            for (Node node : nzyme.getNodeManager().getNodes()) {
                if (node.deleted()) {
                    continue;
                }

                Optional<Double> issues = nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                        node.uuid(), MetricExternalName.FILE_PERMISSION_ISSUES.database_label, handle
                );

                if (issues.isPresent() && issues.get() > 0) {
                    return IndicatorStatus.red(this);
                }
            }

            return IndicatorStatus.green(this);
        });
    }

    @Override
    public String getId() {
        return "node_file_permissions";
    }

    @Override
    public String getName() {
        return "Node File Permissions";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_NODE_FILE_PERMISSIONS_TOGGLED;
    }

}
