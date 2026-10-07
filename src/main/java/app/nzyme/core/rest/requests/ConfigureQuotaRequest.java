package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;

@Schema(description = "Body for configuring a quota of an organization or a tenant.")
@AutoValue
public abstract class ConfigureQuotaRequest {

    @Min(0)
    @Nullable
    public abstract Integer quota();

    @JsonCreator
    public static ConfigureQuotaRequest create(@Schema(description = "New quota value. Omit or set to null to remove the quota and allow unlimited use.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("quota") Integer quota) {
        return builder()
                .quota(quota)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ConfigureQuotaRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder quota(Integer quota);

        public abstract ConfigureQuotaRequest build();
    }
}
