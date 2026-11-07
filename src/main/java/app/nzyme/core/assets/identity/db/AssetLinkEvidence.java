package app.nzyme.core.assets.identity.db;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

// Stored as JSON on the link.
public record AssetLinkEvidence(@JsonProperty("signal") String signal,
                                @JsonProperty("weight") int weight,
                                @JsonProperty("value") @Nullable String value) {

    @JsonCreator
    public AssetLinkEvidence { }

}
