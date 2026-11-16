package app.nzyme.core.assets.tags;

import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

public record AssetTag(long id,
                       UUID uuid,
                       UUID organizationId,
                       UUID tenantId,
                       String name,
                       @Nullable String description,
                       long assetCount,
                       DateTime createdAt,
                       DateTime updatedAt) { }
