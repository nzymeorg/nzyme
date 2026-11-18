package app.nzyme.core.ethernet.segmentation;

import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

public record Policy(long id,
                     UUID uuid,
                     UUID organizationId,
                     UUID tenantId,
                     String name,
                     @Nullable String description,
                     PolicyStatus status,
                     boolean alertOnConnected,
                     boolean alertOnAttempted,
                     PolicyRule scope,
                     List<PolicyRule> allowed,
                     DateTime createdAt,
                     DateTime updatedAt,
                     // The cursor moves back from where the engine was to backfillFrom. The previous definition is
                     // what these sessions were evaluated against, of a paused policy up to backfillPreviousUntil.
                     @Nullable DateTime backfillRequestedAt,
                     @Nullable DateTime backfillCursor,
                     @Nullable DateTime backfillFrom,
                     @Nullable PolicyRule backfillPreviousScope,
                     List<PolicyRule> backfillPreviousAllowed,
                     @Nullable DateTime backfillPreviousUntil) {

    public boolean backfillPending() {
        return backfillRequestedAt != null && status != PolicyStatus.PAUSED;
    }

    public boolean alertsOn(FindingClass findingClass) {
        if (status != PolicyStatus.ACTIVE) {
            return false;
        }

        return findingClass == FindingClass.CROSSED ? alertOnConnected : alertOnAttempted;
    }

}
