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

import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.taps.TapManager;

import java.util.List;
import java.util.Optional;

/**
 * Taps report the number of their files (like the configuration file holding the tap secret) that are accessible
 * by other users on the tap host. The tap refuses to start with such permissions, so this only triggers when they
 * are loosened while the tap is running.
 */
public class TapFilePermissionsIndicator extends Indicator {

    public static final String TAP_GAUGE_NAME = "files.permission_issues";

    private final TapManager tapManager;

    public TapFilePermissionsIndicator(TapManager tapManager) {
        this.tapManager = tapManager;
    }

    @Override
    protected IndicatorStatus doRun() {
        List<Tap> taps = tapManager.findAllTapsOfAllUsers();

        for (Tap tap : taps) {
            Optional<Double> issues = tapManager.findLatestActiveMetricsGaugeValue(tap.uuid(), TAP_GAUGE_NAME);

            if (issues.isPresent() && issues.get() > 0) {
                return IndicatorStatus.red(this);
            }
        }

        return IndicatorStatus.green(this);
    }

    @Override
    public String getId() {
        return "tap_file_permissions";
    }

    @Override
    public String getName() {
        return "Tap File Permissions";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_TAP_FILE_PERMISSIONS_TOGGLED;
    }

}
