package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.CIDR;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

// Attempts from the segment to the destination that are not answered are not recorded. They are only counted.
public record Exclusion(long id,
                        UUID uuid,
                        long segmentId,
                        CIDR destination,
                        String note,
                        long matchCount,
                        @Nullable DateTime lastMatchedAt,
                        DateTime createdAt) { }
