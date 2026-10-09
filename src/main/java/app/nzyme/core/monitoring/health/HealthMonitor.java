package app.nzyme.core.monitoring.health;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.events.types.SystemEvent;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import app.nzyme.core.monitoring.health.indicators.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HealthMonitor {

    private static final Logger LOG = LogManager.getLogger(HealthMonitor.class);

    private final NzymeNode nzyme;

    public HealthMonitor(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public void initialize() {
        Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("node-metrics-updater-%d")
                        .setDaemon(true)
                        .build()
        ).scheduleAtFixedRate(this::runChecks, 0, 1, TimeUnit.MINUTES);
    }

    private void runChecks() {
        LOG.debug("Running health monitor checks.");

        // Add all checks/indicators here.
        List<Indicator> checks = new ImmutableList.Builder<Indicator>()
                .add(new NodeClockIndicator(nzyme.getNodeManager()))
                .add(new CryptoSyncIndicator(nzyme.getCrypto()))
                .add(new DatabaseClockIndicator(nzyme.getDatabase(), nzyme.getConfiguration().ntpServer()))
                .add(new TapClockIndicator(nzyme.getTapManager()))
                .add(new NodeOfflineIndicator(nzyme.getNodeManager()))
                .add(new TapOfflineIndicator(nzyme.getTapManager()))
                .add(new TapThroughputIndicator(nzyme.getTapManager()))
                .add(new TapDropIndicator(nzyme.getTapManager()))
                .add(new TapBufferIndicator(nzyme.getTapManager()))
                .add(new TapErrorIndicator(nzyme.getTapManager()))
                .add(new TapFilePermissionsIndicator(nzyme.getTapManager()))
                .add(new NodeFilePermissionsIndicator(nzyme))
                .add(new TLSExpirationIndicator(nzyme.getCrypto(), nzyme.getNodeManager()))
                .add(new TasksQueueTaskFailureIndicator(nzyme.getTasksQueue()))
                .add(new TasksQueueTaskStuckIndicator(nzyme.getTasksQueue()))
                .add(new MessageBusMessageFailureIndicator(nzyme.getMessageBus()))
                .add(new MessageBusMessageStuckIndicator(nzyme.getMessageBus()))
                .build();

        // Pull all indicators status and active/inactive state.
        Map<String, IndicatorStatus> previousIndicators = Maps.newHashMap();
        for (IndicatorStatus status : getIndicatorStatus()) {
            previousIndicators.put(status.indicatorId(), status);
        }

        for (Indicator check : checks) {
            try {
                // TODO do not pull
                if (!indicatorIsActive(check.getId())) {
                    continue;
                }

                DateTime now = DateTime.now();
                IndicatorStatus status = check.run();

                // Write to database.
                nzyme.getDatabase().useHandle(handle ->
                        handle.createUpdate("INSERT INTO health_indicators(indicator_id, indicator_name, level, " +
                                        "last_checked) VALUES(:indicator_id, :indicator_name, :level, :last_checked) " +
                                        "ON CONFLICT(indicator_id) DO UPDATE SET indicator_name = :indicator_name, " +
                                        "level = :level, last_checked = :last_checked")
                                .bind("indicator_id", check.getId())
                                .bind("indicator_name", check.getName())
                                .bind("level", status.resultLevel().toString().toUpperCase())
                                .bind("last_checked", now)
                                .execute()
                );

                // Trigger notification if status changed since last run and if last run
                IndicatorStatus previousStatus = previousIndicators.get(check.getId());
                if (previousStatus == null || !previousStatus.resultLevel().equals(status.resultLevel()))
                    nzyme.getEventEngine().processEvent(
                            SystemEvent.create(
                                    check.getSystemEventType(),
                                    now,
                                    "Health Monitor [" + check.getName() + "]: <" + status.resultLevel() + ">"
                            ),
                            null, null
                    );
            } catch(Exception e) {
                LOG.error("Could not run health check indicator. Skipping.", e);
            }
        }

        LOG.debug("Finished running health monitor checks.");
    }

    public List<IndicatorStatus> getIndicatorStatus() {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT indicator_id, indicator_name, level, last_checked, active " +
                                "FROM health_indicators")
                        .mapTo(IndicatorStatus.class)
                        .list()
        );
    }

    public void updateIndicatorActivationState(String indicatorId, boolean active) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE health_indicators SET active = :active WHERE indicator_id = :indicator_id")
                        .bind("active", active)
                        .bind("indicator_id", indicatorId)
                        .execute()
        );
    }

    public boolean indicatorIsActive(String indicatorId) {
        Optional<Boolean> active = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT active FROM health_indicators WHERE indicator_id = :indicator_id")
                        .bind("indicator_id", indicatorId)
                        .mapTo(Boolean.class)
                        .findFirst()
        );

        // An indicator is active if no db entry exists yet. (Could be the first run.)
        return active.orElse(true);
    }

}
