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
                     DateTime updatedAt) {

    public boolean alertsOn(FindingClass findingClass) {
        if (status != PolicyStatus.ACTIVE) {
            return false;
        }

        return findingClass == FindingClass.CROSSED ? alertOnConnected : alertOnAttempted;
    }

}
