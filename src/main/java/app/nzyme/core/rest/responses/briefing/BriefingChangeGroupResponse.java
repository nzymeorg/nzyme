package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;

@AutoValue
public abstract class BriefingChangeGroupResponse {

    @JsonProperty("key")
    public abstract String key();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("subsystem")
    @Nullable
    public abstract String subsystem();

    @JsonProperty("link")
    @Nullable
    public abstract BriefingLinkResponse link();

    @JsonProperty("tiles")
    public abstract List<BriefingChangeTileResponse> tiles();

    @JsonProperty("hint")
    @Nullable
    public abstract String hint();

    public static BriefingChangeGroupResponse create(String key, String title, String subsystem, BriefingLinkResponse link, List<BriefingChangeTileResponse> tiles, String hint) {
        return builder()
                .key(key)
                .title(title)
                .subsystem(subsystem)
                .link(link)
                .tiles(tiles)
                .hint(hint)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingChangeGroupResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder key(String key);

        public abstract Builder title(String title);

        public abstract Builder subsystem(String subsystem);

        public abstract Builder link(BriefingLinkResponse link);

        public abstract Builder tiles(List<BriefingChangeTileResponse> tiles);

        public abstract Builder hint(String hint);

        public abstract BriefingChangeGroupResponse build();
    }
}
