package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.util.filters.Filters;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record BulkSelection(@Nullable List<UUID> uuids, @Nullable Filters filters) {

    public static BulkSelection checked(Collection<UUID> uuids) {
        return new BulkSelection(List.copyOf(uuids), null);
    }

    public static BulkSelection matching(Filters filters) {
        return new BulkSelection(null, filters);
    }

    public boolean isChecked() {
        return uuids != null;
    }

    // Nothing is checked. There is nothing to do, and no statement to run with an empty list.
    public boolean isEmpty() {
        return uuids != null && uuids.isEmpty();
    }

}
