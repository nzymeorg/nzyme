package app.nzyme.core.briefing;

import app.nzyme.core.NzymeNode;
import org.joda.time.DateTime;

import java.util.Optional;
import java.util.UUID;

public class BriefingVisits {

    public static final int GRACE_MINUTES = 10;

    private final NzymeNode nzyme;

    public BriefingVisits(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    private record VisitRow(DateTime lastVisitAt, DateTime previousVisitAt) { }

    public Optional<DateTime> touch(UUID userId, BriefingType type) {
        return nzyme.getDatabase().withHandle(handle -> {
            Optional<VisitRow> row = handle.createQuery("SELECT last_visit_at, previous_visit_at " +
                            "FROM briefing_visits WHERE user_id = :user_id AND briefing_type = :type")
                    .bind("user_id", userId)
                    .bind("type", type.name())
                    .map((rs, ctx) -> new VisitRow(
                            new DateTime(rs.getTimestamp("last_visit_at")),
                            rs.getTimestamp("previous_visit_at") == null
                                    ? null : new DateTime(rs.getTimestamp("previous_visit_at"))
                    ))
                    .findOne();

            if (row.isEmpty()) {
                handle.createUpdate("INSERT INTO briefing_visits(user_id, briefing_type, last_visit_at, " +
                                "previous_visit_at) VALUES(:user_id, :type, NOW(), NULL)")
                        .bind("user_id", userId)
                        .bind("type", type.name())
                        .execute();

                return Optional.empty();
            }

            if (row.get().lastVisitAt().isBefore(DateTime.now().minusMinutes(GRACE_MINUTES))) {
                handle.createUpdate("UPDATE briefing_visits SET previous_visit_at = last_visit_at, " +
                                "last_visit_at = NOW() WHERE user_id = :user_id AND briefing_type = :type")
                        .bind("user_id", userId)
                        .bind("type", type.name())
                        .execute();

                return Optional.of(row.get().lastVisitAt());
            }

            // Reload within the grace period keeps the current window.
            return Optional.ofNullable(row.get().previousVisitAt());
        });
    }

    public void markSeen(UUID userId, BriefingType type) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO briefing_visits(user_id, briefing_type, last_visit_at, " +
                                "previous_visit_at) VALUES(:user_id, :type, NOW(), NOW()) " +
                                "ON CONFLICT (user_id, briefing_type) DO UPDATE " +
                                "SET previous_visit_at = NOW(), last_visit_at = NOW()")
                        .bind("user_id", userId)
                        .bind("type", type.name())
                        .execute()
        );
    }

}
