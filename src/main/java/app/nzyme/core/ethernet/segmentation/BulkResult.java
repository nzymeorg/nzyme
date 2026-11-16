package app.nzyme.core.ethernet.segmentation;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record BulkResult(long affected, List<Skipped> skipped) {

    public record Skipped(UUID uuid, String name, String reason) { }

    public static BulkResult of(long affected) {
        return new BulkResult(affected, Collections.emptyList());
    }

}
