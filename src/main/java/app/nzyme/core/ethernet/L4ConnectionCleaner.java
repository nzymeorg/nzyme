package app.nzyme.core.ethernet;

import app.nzyme.core.NzymeNodeImpl;
import app.nzyme.core.ethernet.l4.tcp.TcpSessionState;
import app.nzyme.core.ethernet.l4.udp.UdpConversationState;
import app.nzyme.core.periodicals.Periodical;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

public class L4ConnectionCleaner extends Periodical {

    /*
     * This functionality takes care of situations where a tap shuts down with connections still open. Such a tap
     * would never see the FIN/RST in TCP or timeout in UDP, and we'd end up with a connection that exists for forever.
     */

    private static final Logger LOG = LogManager.getLogger(L4ConnectionCleaner.class);

    // Must stay identical to the predicate of the l4_sessions_open_l4_type_most_recent_segment_time_idx index or
    // the planner cannot use it.
    private static final String OPEN_STATE = "state NOT IN ('CLOSED', 'CLOSEDNODE', 'CLOSEDFIN', 'CLOSEDRST', " +
            "'CLOSEDTIMEOUT', 'CLOSEDTIMEOUTNODE', 'REFUSED')";

    private final NzymeNodeImpl nzyme;

    public L4ConnectionCleaner(NzymeNodeImpl nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    protected void execute() {
        LOG.debug("Cleaning stale L4 connections.");

        int tcpSessionTimeoutSeconds;
        if (nzyme.getConfiguration().protocols().isPresent()
                && nzyme.getConfiguration().protocols().get().tcp().isPresent()
                && nzyme.getConfiguration().protocols().get().tcp().get().sessionTimeoutSeconds().isPresent()) {
            tcpSessionTimeoutSeconds = nzyme.getConfiguration().protocols().get().tcp().get()
                    .sessionTimeoutSeconds().get();
        } else {
            tcpSessionTimeoutSeconds = 43200;
        }

        // SKIP LOCKED so that we never wait on a row lock. Tap reports and retention purges lock the same rows in
        // a different order and would deadlock with us.

        // TCP.
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE l4_sessions SET state = :new_state WHERE id IN (" +
                                "SELECT id FROM l4_sessions " +
                                "WHERE l4_type = 'TCP' AND most_recent_segment_time < :cutoff " +
                                "AND " + OPEN_STATE + " FOR UPDATE SKIP LOCKED)")
                        .bind("new_state", TcpSessionState.CLOSEDTIMEOUTNODE)
                        .bind("cutoff", DateTime.now().minusSeconds(tcpSessionTimeoutSeconds))
                        .execute()
        );

        // UDP.
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE l4_sessions SET state = :new_state WHERE id IN (" +
                                "SELECT id FROM l4_sessions " +
                                "WHERE l4_type = 'UDP' AND most_recent_segment_time < :cutoff " +
                                "AND " + OPEN_STATE + " FOR UPDATE SKIP LOCKED)")
                        .bind("new_state", UdpConversationState.CLOSEDNODE)
                        .bind("cutoff", DateTime.now().minusSeconds(120))
                        .execute()
        );
    }

    @Override
    public String getName() {
        return "L4ConnectionCleaner";
    }

}
