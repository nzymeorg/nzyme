package app.nzyme.core.assets.identity.db;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

// Stored as JSON on the finding.
public record IdentityChange(@JsonProperty("signal") String signal,
                             @JsonProperty("weight") int weight,
                             @JsonProperty("previous_value") @Nullable String previousValue,
                             @JsonProperty("new_value") String newValue,
                             @JsonProperty("seen_at") String seenAt) {

    @JsonCreator
    public IdentityChange { }

}
