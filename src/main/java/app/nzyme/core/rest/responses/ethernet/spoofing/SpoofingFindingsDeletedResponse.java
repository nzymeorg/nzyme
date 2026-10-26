package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SpoofingFindingsDeletedResponse {

    @JsonProperty("deleted_count")
    public abstract int deletedCount();

    public static SpoofingFindingsDeletedResponse create(int deletedCount) {
        return builder()
                .deletedCount(deletedCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingFindingsDeletedResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder deletedCount(int deletedCount);
        public abstract SpoofingFindingsDeletedResponse build();
    }
}
