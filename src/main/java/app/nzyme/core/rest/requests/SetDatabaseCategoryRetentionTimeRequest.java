package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Body for setting the retention time of a database category.")
@AutoValue
public abstract class SetDatabaseCategoryRetentionTimeRequest {

    @Min(1)
    public abstract int retentionTimeDays();

    @JsonCreator
    public static SetDatabaseCategoryRetentionTimeRequest create(@Schema(description = "Number of days that data of this category is kept. Minimum is 1.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                 @JsonProperty("retention_time_days") int retentionTimeDays) {
        return builder()
                .retentionTimeDays(retentionTimeDays)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SetDatabaseCategoryRetentionTimeRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder retentionTimeDays(@Min(1) int retentionTimeDays);

        public abstract SetDatabaseCategoryRetentionTimeRequest build();
    }
}
