package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(description = "Body for updating the sidebar title of the web interface.")
@AutoValue
public abstract class UpdateSidebarTitleRequest {

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("subtitle")
    @Nullable
    public abstract String subtitle();

    @JsonCreator
    public static UpdateSidebarTitleRequest create(@Schema(description = "Title shown in the sidebar.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                   @JsonProperty("title") String title,
                                                   @Schema(description = "Subtitle shown below the title in the sidebar. Omit for no subtitle.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                   @JsonProperty("subtitle") String subtitle) {
        return builder()
                .title(title)
                .subtitle(subtitle)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateSidebarTitleRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder title(String title);

        public abstract Builder subtitle(String subtitle);

        public abstract UpdateSidebarTitleRequest build();
    }
}
