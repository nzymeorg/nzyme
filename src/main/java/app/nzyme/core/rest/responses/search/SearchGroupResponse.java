package app.nzyme.core.rest.responses.search;

import app.nzyme.core.rest.responses.briefing.BriefingLinkResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class SearchGroupResponse {

    @JsonProperty("source")
    public abstract String source();

    @JsonProperty("title")
    public abstract String title();

    @Schema(description = "INSTANT for configuration and findings, RECORDED for recorded network data.")
    @JsonProperty("tier")
    public abstract String tier();

    @Schema(description = "Place of the source in the order groups are shown in when nothing else ranks them.")
    @JsonProperty("position")
    public abstract int position();

    @Schema(description = "OK, TIMEOUT if the source took longer than it is allowed to, BUSY if too many searches "
            + "of recorded data were running, ERROR if the source failed. Only OK carries hits.")
    @JsonProperty("status")
    public abstract String status();

    @JsonProperty("hits")
    public abstract List<SearchHitResponse> hits();

    @Schema(description = "If the source has more hits after the returned ones. Request them with a higher offset.")
    @JsonProperty("has_more")
    public abstract boolean hasMore();

    @Schema(description = "If the source has more hits that cannot be requested, because the offset is limited.")
    @JsonProperty("limited")
    public abstract boolean limited();

    @JsonProperty("offset")
    public abstract int offset();

    @JsonProperty("limit")
    public abstract int limit();

    @Schema(description = "Start of the recorded data that was searched. Missing if the source searched all of its "
            + "data.")
    @JsonProperty("searched_from")
    @Nullable
    public abstract DateTime searchedFrom();

    @Schema(description = "Page of the web interface that lists the matching records of this source, if there is "
            + "one.")
    @JsonProperty("all_link")
    @Nullable
    public abstract BriefingLinkResponse allLink();

    @JsonProperty("took_ms")
    public abstract long tookMs();

    public static SearchGroupResponse create(String source,
                                             String title,
                                             String tier,
                                             int position,
                                             String status,
                                             List<SearchHitResponse> hits,
                                             boolean hasMore,
                                             boolean limited,
                                             int offset,
                                             int limit,
                                             @Nullable DateTime searchedFrom,
                                             @Nullable BriefingLinkResponse allLink,
                                             long tookMs) {
        return builder()
                .source(source)
                .title(title)
                .tier(tier)
                .position(position)
                .status(status)
                .hits(hits)
                .hasMore(hasMore)
                .limited(limited)
                .offset(offset)
                .limit(limit)
                .searchedFrom(searchedFrom)
                .allLink(allLink)
                .tookMs(tookMs)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SearchGroupResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder source(String source);

        public abstract Builder title(String title);

        public abstract Builder tier(String tier);

        public abstract Builder position(int position);

        public abstract Builder status(String status);

        public abstract Builder hits(List<SearchHitResponse> hits);

        public abstract Builder hasMore(boolean hasMore);

        public abstract Builder limited(boolean limited);

        public abstract Builder offset(int offset);

        public abstract Builder limit(int limit);

        public abstract Builder searchedFrom(@Nullable DateTime searchedFrom);

        public abstract Builder allLink(@Nullable BriefingLinkResponse allLink);

        public abstract Builder tookMs(long tookMs);

        public abstract SearchGroupResponse build();
    }

}
