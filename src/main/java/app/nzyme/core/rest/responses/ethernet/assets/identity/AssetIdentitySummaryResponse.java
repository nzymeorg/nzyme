package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class AssetIdentitySummaryResponse {

    @Schema(description = "Newest duplicate MAC findings of the asset within the summary period, most recently seen first. Capped, see duplicates_total for the full count.")
    @JsonProperty("duplicates")
    public abstract List<DuplicateMacFindingResponse> duplicates();

    @Schema(description = "Total number of duplicate MAC findings of the asset within the summary period.")
    @JsonProperty("duplicates_total")
    public abstract long duplicatesTotal();

    @Schema(description = "Newest identity change findings of the asset within the summary period, most recently seen first. Capped, see changes_total for the full count.")
    @JsonProperty("changes")
    public abstract List<IdentityChangeFindingResponse> changes();

    @Schema(description = "Total number of identity change findings of the asset within the summary period.")
    @JsonProperty("changes_total")
    public abstract long changesTotal();

    @Schema(description = "Open and confirmed links that involve the asset, newest first. Dismissed links are not included.")
    @JsonProperty("links")
    public abstract List<AssetLinkResponse> links();

    @Schema(description = "Length of the summary period in days. Findings older than this are not counted.")
    @JsonProperty("period_days")
    public abstract int periodDays();

    public static AssetIdentitySummaryResponse create(List<DuplicateMacFindingResponse> duplicates, long duplicatesTotal, List<IdentityChangeFindingResponse> changes, long changesTotal, List<AssetLinkResponse> links, int periodDays) {
        return builder()
                .duplicates(duplicates)
                .duplicatesTotal(duplicatesTotal)
                .changes(changes)
                .changesTotal(changesTotal)
                .links(links)
                .periodDays(periodDays)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetIdentitySummaryResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder duplicates(List<DuplicateMacFindingResponse> duplicates);
        public abstract Builder duplicatesTotal(long duplicatesTotal);
        public abstract Builder changes(List<IdentityChangeFindingResponse> changes);
        public abstract Builder changesTotal(long changesTotal);
        public abstract Builder links(List<AssetLinkResponse> links);
        public abstract Builder periodDays(int periodDays);
        public abstract AssetIdentitySummaryResponse build();
    }
}
