package app.nzyme.core.rest.responses.system.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;

@AutoValue
public abstract class GlobalDatabaseCategoriesResponse {

    @JsonProperty("global_sizes")
    public abstract Map<String, DataCategorySizesResponse> globalSizes();

    @JsonProperty("subsystem_sizes")
    public abstract Map<String, DataCategorySizesResponse> subsystemSizes();

    @JsonProperty("organizations")
    public abstract List<OrganizationDataCategoriesResponse> organizations();

    @Nullable
    @JsonProperty("calculated_at")
    public abstract DateTime calculatedAt();

    @JsonProperty("estimated")
    public abstract boolean estimated();

    @JsonProperty("calculating")
    public abstract boolean calculating();

    @Nullable
    @JsonProperty("calculation_error")
    public abstract String calculationError();

    public static GlobalDatabaseCategoriesResponse create(Map<String, DataCategorySizesResponse> globalSizes, Map<String, DataCategorySizesResponse> subsystemSizes, List<OrganizationDataCategoriesResponse> organizations, DateTime calculatedAt, boolean estimated, boolean calculating, String calculationError) {
        return builder()
                .globalSizes(globalSizes)
                .subsystemSizes(subsystemSizes)
                .organizations(organizations)
                .calculatedAt(calculatedAt)
                .estimated(estimated)
                .calculating(calculating)
                .calculationError(calculationError)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_GlobalDatabaseCategoriesResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder globalSizes(Map<String, DataCategorySizesResponse> globalSizes);
        public abstract Builder subsystemSizes(Map<String, DataCategorySizesResponse> subsystemSizes);
        public abstract Builder organizations(List<OrganizationDataCategoriesResponse> organizations);
        public abstract Builder calculatedAt(DateTime calculatedAt);
        public abstract Builder estimated(boolean estimated);
        public abstract Builder calculating(boolean calculating);
        public abstract Builder calculationError(String calculationError);

        public abstract GlobalDatabaseCategoriesResponse build();
    }

}
