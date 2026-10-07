package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Body carrying the UUIDs of the records to act on.")
@AutoValue
public abstract class UUIDListRequest {

    public abstract List<UUID> uuids();

    @JsonCreator
    public static UUIDListRequest create(@Schema(description = "UUIDs of the records.", requiredMode = Schema.RequiredMode.REQUIRED)
                                         @JsonProperty("uuids") List<UUID> uuids) {
        return builder()
                .uuids(uuids)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UUIDListRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuids(List<UUID> uuids);

        public abstract UUIDListRequest build();
    }
}
