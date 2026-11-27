package app.nzyme.core.dot11.monitoring.ssids;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.taps.Tap;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/*
 * Records what stays in range for the dwell time, and alerts on what nobody decided on. A run of a tenant is a
 * sequence of small steps. Each step is a transaction that takes the lock of the tenant, so that two nodes never work
 * on the same tenant at once and a step that fails takes nothing else with it.
 */
abstract class DwellTimeMonitor {

    private static final Logger LOG = LogManager.getLogger(DwellTimeMonitor.class);

    private static final int LOOKBACK_MINUTES = 3;
    private static final int MAX_CATCH_UP_MINUTES = 60;
    private static final int DWELL_WINDOW_HOURS = 24;
    private static final int RECENT_DWELL_WINDOW_MINUTES = 60;
    private static final int MAX_SEEN_PER_RUN = 10000;

    private static final int REEVALUATION_MINUTES = 15;
    private static final int REEVALUATION_BATCH_SIZE = 50;

    /*
     * Counting the minutes something was seen in reads its reports of a day. That work is one statement per name and
     * stops when the time of a run is used up. The next run continues where it stopped.
     */
    private static final int STATEMENT_TIMEOUT_MS = 5000;
    private static final int EVALUATION_BUDGET_MS = 10000;

    protected final NzymeNode nzyme;

    protected DwellTimeMonitor(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    protected record Tenant(UUID organizationId, UUID tenantId, List<UUID> taps, int dwellTimeMinutes, DateTime now) {

        protected DateTime dwellSince() {
            return now.minusHours(DWELL_WINDOW_HOURS);
        }

    }

    protected record Pending(long id, String name) { }

    protected record Alerting(String name, List<UUID> alertTaps, Map<String, String> attributes) { }

    // For log messages, like "hidden access points".
    protected abstract String subjects();

    // Names the lock of a tenant and the registry key that holds until when reports were read.
    protected abstract String name();

    // Until how long ago reports are read. Not up to now if a report is written in more than one step.
    protected int settleSeconds() {
        return 0;
    }

    // Those with the most frames first, so that a flood of made-up names is considered last.
    protected abstract List<String> findSeen(Handle handle, Tenant tenant, DateTime from, DateTime to, int limit);

    // Marks those of the names that are known as seen and returns them.
    protected abstract Set<String> touchKnown(Handle handle, Tenant tenant, List<String> seen);

    protected boolean hasRoomFor(Tenant tenant, int unknown) {
        return true;
    }

    // Empty if no tap recorded the name in the time range.
    protected abstract Optional<Sightings> findSightings(Handle handle, Tenant tenant, String name, DateTime from, DateTime to);

    // False if it was not created, because it is known already or there is no room.
    protected abstract boolean create(Handle handle, Tenant tenant, String name, List<UUID> alertTaps);

    /*
     * Undecided and seen in the last day. Those never evaluated come first, then those evaluated longest ago. One
     * without taps to alert at is due in every run, so that it follows a change of the dwell time at once.
     */
    protected abstract List<Pending> findPendingToEvaluate(Tenant tenant, DateTime evaluatedBefore, int limit);

    // Null keeps the taps of the last evaluation.
    protected abstract void setEvaluated(Handle handle, long id, DateTime at, @Nullable Collection<UUID> alertTaps);

    protected void afterEvaluation(Tenant tenant) { }

    // Undecided, seen in the last day and with taps to alert at.
    protected abstract List<Alerting> findAlerting(Tenant tenant);

    protected abstract void raiseAlert(Tenant tenant, Alerting alerting, List<UUID> taps);

    public void runForTenant(UUID organizationId, UUID tenantId, int dwellTimeMinutes, boolean eventingEnabled) {
        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream()
                .map(Tap::uuid)
                .toList();

        if (taps.isEmpty()) {
            return;
        }

        Tenant tenant = new Tenant(organizationId, tenantId, taps, dwellTimeMinutes, DateTime.now());

        Optional<List<String>> unknown;
        try {
            unknown = locked(tenant, handle -> recordSeen(handle, tenant));
        } catch (Exception e) {
            // The next run starts from now. Reading the same reports again would fail the same way.
            locked(tenant, handle -> storeSeenUntil(tenant));
            throw e;
        }

        if (unknown.isEmpty()) {
            LOG.debug("Monitoring of {} for tenant [{}/{}] is running on another node. Skipping.",
                    subjects(), organizationId, tenantId);
            return;
        }

        long deadline = System.currentTimeMillis() + EVALUATION_BUDGET_MS;
        evaluateUnknown(tenant, unknown.get(), deadline);
        if (eventingEnabled) {
            reevaluatePending(tenant, deadline);
        }

        afterEvaluation(tenant);

        if (eventingEnabled) {
            raiseAlerts(tenant);
        }
    }

    // Empty if another node holds the lock of the tenant.
    protected <T> Optional<T> locked(Tenant tenant, Function<Handle, T> work) {
        return nzyme.getDatabase().inTransaction(handle -> {
            boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                    .bind("key", name() + ":" + tenant.organizationId() + ":" + tenant.tenantId())
                    .mapTo(Boolean.class)
                    .one();

            if (!locked) {
                return Optional.<T>empty();
            }

            handle.execute("SET LOCAL statement_timeout = " + STATEMENT_TIMEOUT_MS);

            return Optional.of(work.apply(handle));
        });
    }

    private List<String> recordSeen(Handle handle, Tenant tenant) {
        DateTime seenSince = seenSince(tenant);

        List<String> seen = findSeen(handle, tenant, seenSince, tenant.now().minusSeconds(settleSeconds()),
                MAX_SEEN_PER_RUN);

        if (seen.size() >= MAX_SEEN_PER_RUN) {
            LOG.warn("Taps of tenant [{}/{}] recorded more than <{}> {} since [{}]. Considering those with the " +
                    "most frames only.", tenant.organizationId(), tenant.tenantId(), MAX_SEEN_PER_RUN, subjects(),
                    seenSince);
        }

        Set<String> known = touchKnown(handle, tenant, seen);
        storeSeenUntil(tenant);

        return seen.stream().filter(name -> !known.contains(name)).toList();
    }

    private void evaluateUnknown(Tenant tenant, List<String> unknown, long deadline) {
        if (unknown.isEmpty() || !hasRoomFor(tenant, unknown.size())) {
            return;
        }

        for (String name : unknown) {
            if (System.currentTimeMillis() > deadline) {
                LOG.debug("Out of time for new {} of tenant [{}/{}]. The next run continues.",
                        subjects(), tenant.organizationId(), tenant.tenantId());
                return;
            }

            try {
                Optional<Boolean> done = locked(tenant, handle -> {
                    // What is there all day reaches the dwell time in the last hour, a fraction to read.
                    Optional<Sightings> sightings = dwelling(handle, tenant, name,
                            tenant.now().minusMinutes(RECENT_DWELL_WINDOW_MINUTES));
                    if (sightings.isEmpty()) {
                        sightings = dwelling(handle, tenant, name, tenant.dwellSince());
                    }

                    return sightings.isPresent() && create(handle, tenant, name,
                            sightings.get().tapsToAlertAt(tenant.dwellTimeMinutes()));
                });

                if (done.isEmpty()) {
                    return;
                }
            } catch (Exception e) {
                LOG.warn("Could not evaluate [{}] of the {} of tenant [{}/{}]. Skipping.",
                        name, subjects(), tenant.organizationId(), tenant.tenantId(), e);
            }
        }
    }

    /*
     * The taps to alert at are kept, because they take a day of reports to work out. What was created from the last
     * hour alone has no evaluation time yet and comes first here.
     */
    private void reevaluatePending(Tenant tenant, long deadline) {
        while (true) {
            List<Pending> entries = findPendingToEvaluate(tenant, tenant.now().minusMinutes(REEVALUATION_MINUTES),
                    REEVALUATION_BATCH_SIZE);

            for (Pending entry : entries) {
                if (System.currentTimeMillis() > deadline) {
                    LOG.debug("Out of time for the alert locations of {} of tenant [{}/{}]. The next run continues.",
                            subjects(), tenant.organizationId(), tenant.tenantId());
                    return;
                }

                Optional<Boolean> done;
                try {
                    done = locked(tenant, handle -> {
                        setEvaluated(handle, entry.id(), DateTime.now(),
                                dwelling(handle, tenant, entry.name(), tenant.dwellSince())
                                        .map(sightings -> sightings.tapsToAlertAt(tenant.dwellTimeMinutes()))
                                        .orElse(List.of()));
                        return true;
                    });
                } catch (Exception e) {
                    LOG.warn("Could not evaluate [{}] of the {} of tenant [{}/{}]. Keeping its alert locations.",
                            entry.name(), subjects(), tenant.organizationId(), tenant.tenantId(), e);

                    // Marked as evaluated, or the same one would be the first of every run.
                    done = locked(tenant, handle -> {
                        setEvaluated(handle, entry.id(), DateTime.now(), null);
                        return true;
                    });
                }

                if (done.isEmpty()) {
                    return;
                }
            }

            if (entries.size() < REEVALUATION_BATCH_SIZE) {
                return;
            }
        }
    }

    private Optional<Sightings> dwelling(Handle handle, Tenant tenant, String name, DateTime since) {
        return findSightings(handle, tenant, name, since, tenant.now().minusSeconds(settleSeconds()))
                .filter(sightings -> sightings.activeMinutes() >= tenant.dwellTimeMinutes());
    }

    private void raiseAlerts(Tenant tenant) {
        for (Alerting alerting : findAlerting(tenant)) {
            try {
                // A tap may have been deleted since the taps were worked out.
                List<UUID> taps = alerting.alertTaps().stream().filter(tenant.taps()::contains).toList();

                raiseAlert(tenant, alerting, taps.isEmpty() ? tenant.taps() : taps);
            } catch (Exception e) {
                LOG.error("Could not raise alert for [{}] of the {} of tenant [{}/{}].", alerting.name(), subjects(),
                        tenant.organizationId(), tenant.tenantId(), e);
            }
        }
    }

    // From where to read reports: the stored moment less an overlap, but never more than the catch-up time.
    private DateTime seenSince(Tenant tenant) {
        DateTime latest = tenant.now().minusMinutes(LOOKBACK_MINUTES);

        Optional<DateTime> seenUntil;
        try {
            seenUntil = nzyme.getDatabaseCoreRegistry()
                    .getValue(name() + "_seen_until", tenant.organizationId(), tenant.tenantId())
                    .map(value -> new DateTime(Long.parseLong(value)));
        } catch (NumberFormatException e) {
            seenUntil = Optional.empty();
        }

        if (seenUntil.isEmpty()) {
            return latest;
        }

        DateTime earliest = tenant.now().minusMinutes(MAX_CATCH_UP_MINUTES);
        DateTime since = seenUntil.get().minusMinutes(LOOKBACK_MINUTES);

        return since.isBefore(earliest) ? earliest : since.isAfter(latest) ? latest : since;
    }

    // Only called under the lock. The registry does not keep two nodes from writing the same key twice.
    private boolean storeSeenUntil(Tenant tenant) {
        nzyme.getDatabaseCoreRegistry().setValue(name() + "_seen_until", String.valueOf(tenant.now().getMillis()),
                tenant.organizationId(), tenant.tenantId());

        return true;
    }

}
