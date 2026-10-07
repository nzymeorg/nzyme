package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(description = "Body for approving known WiFi networks of a tenant.")
@AutoValue
public abstract class ApproveByRegexRequest {

    @Nullable
    public abstract String regex();

    @JsonCreator
    public static ApproveByRegexRequest create(@Schema(description = "Java regular expression matched against the SSIDs. Omit to approve every known network " + "of the tenant.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("regex") String regex) {
        return builder()
                .regex(regex)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ApproveByRegexRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder regex(String regex);

        public abstract ApproveByRegexRequest build();
    }
}
