package app.nzyme.core.rest.responses.search;

import app.nzyme.core.rest.responses.briefing.BriefingLinkResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class SearchHitResponse {

    @Schema(description = "Kind of entity that was found, for example ASSET, DOT11_BSSID or ALERT.")
    @JsonProperty("type")
    public abstract String type();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("subtitle")
    @Nullable
    public abstract String subtitle();

    @Schema(description = "Up to four attributes of the entity, the ones that matched the query first.")
    @JsonProperty("facts")
    public abstract List<SearchFactResponse> facts();

    @Schema(description = "When the entity was last seen or changed. Missing for entities without such a time.")
    @JsonProperty("timestamp")
    @Nullable
    public abstract DateTime timestamp();

    @Schema(description = "How well the entity matches: 100 for an exact match of an identifier or name, 60 for a "
            + "match at the start and 30 for a match somewhere inside.")
    @JsonProperty("score")
    public abstract int score();

    @Schema(description = "Page of the web interface that shows the entity, as a target with parameters.")
    @JsonProperty("link")
    public abstract BriefingLinkResponse link();

    public static SearchHitResponse create(String type,
                                           String title,
                                           @Nullable String subtitle,
                                           List<SearchFactResponse> facts,
                                           @Nullable DateTime timestamp,
                                           int score,
                                           BriefingLinkResponse link) {
        return builder()
                .type(type)
                .title(title)
                .subtitle(subtitle)
                .facts(facts)
                .timestamp(timestamp)
                .score(score)
                .link(link)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SearchHitResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);

        public abstract Builder title(String title);

        public abstract Builder subtitle(@Nullable String subtitle);

        public abstract Builder facts(List<SearchFactResponse> facts);

        public abstract Builder timestamp(@Nullable DateTime timestamp);

        public abstract Builder score(int score);

        public abstract Builder link(BriefingLinkResponse link);

        public abstract SearchHitResponse build();
    }

}
