package app.nzyme.core.rest.responses.system.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class OrganizationDataCategoriesResponse {

    @JsonProperty("organization_id")
    public abstract UUID organizationId();

    @JsonProperty("organization_name")
    public abstract String organizationName();

    @JsonProperty("total_sizes")
    public abstract Map<String, DataCategorySizesResponse> totalSizes();

    @JsonProperty("subsystem_sizes")
    public abstract Map<String, DataCategorySizesResponse> subsystemSizes();

    @JsonProperty("tenants")
    public abstract List<TenantDataCategoriesResponse> tenants();

    @Nullable
    @JsonProperty("calculated_at")
    public abstract DateTime calculatedAt();

    @JsonProperty("calculating")
    public abstract boolean calculating();

    @Nullable
    @JsonProperty("calculation_error")
    public abstract String calculationError();

    public static OrganizationDataCategoriesResponse create(UUID organizationId, String organizationName, Map<String, DataCategorySizesResponse> totalSizes, Map<String, DataCategorySizesResponse> subsystemSizes, List<TenantDataCategoriesResponse> tenants, DateTime calculatedAt, boolean calculating, String calculationError) {
        return builder()
                .organizationId(organizationId)
                .organizationName(organizationName)
                .totalSizes(totalSizes)
                .subsystemSizes(subsystemSizes)
                .tenants(tenants)
                .calculatedAt(calculatedAt)
                .calculating(calculating)
                .calculationError(calculationError)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_OrganizationDataCategoriesResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder organizationName(String organizationName);
        public abstract Builder totalSizes(Map<String, DataCategorySizesResponse> totalSizes);
        public abstract Builder subsystemSizes(Map<String, DataCategorySizesResponse> subsystemSizes);
        public abstract Builder tenants(List<TenantDataCategoriesResponse> tenants);
        public abstract Builder calculatedAt(DateTime calculatedAt);
        public abstract Builder calculating(boolean calculating);
        public abstract Builder calculationError(String calculationError);

        public abstract OrganizationDataCategoriesResponse build();
    }

}
