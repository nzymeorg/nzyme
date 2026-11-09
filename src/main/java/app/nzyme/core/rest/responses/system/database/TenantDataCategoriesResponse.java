package app.nzyme.core.rest.responses.system.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class TenantDataCategoriesResponse {

    @JsonProperty("tenant_id")
    public abstract UUID tenantId();

    @JsonProperty("tenant_name")
    public abstract String tenantName();

    @JsonProperty("organization_id")
    public abstract UUID organizationId();

    @JsonProperty("organization_name")
    public abstract String organizationName();

    @JsonProperty("categories")
    public abstract Map<String, DataCategorySizesAndConfigurationResponse> categories();

    @JsonProperty("subsystem_sizes")
    public abstract Map<String, DataCategorySizesResponse> subsystemSizes();

    @Nullable
    @JsonProperty("calculated_at")
    public abstract DateTime calculatedAt();

    @JsonProperty("calculating")
    public abstract boolean calculating();

    @Nullable
    @JsonProperty("calculation_error")
    public abstract String calculationError();

    public static TenantDataCategoriesResponse create(UUID tenantId, String tenantName, UUID organizationId, String organizationName, Map<String, DataCategorySizesAndConfigurationResponse> categories, Map<String, DataCategorySizesResponse> subsystemSizes, DateTime calculatedAt, boolean calculating, String calculationError) {
        return builder()
                .tenantId(tenantId)
                .tenantName(tenantName)
                .organizationId(organizationId)
                .organizationName(organizationName)
                .categories(categories)
                .subsystemSizes(subsystemSizes)
                .calculatedAt(calculatedAt)
                .calculating(calculating)
                .calculationError(calculationError)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TenantDataCategoriesResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder tenantName(String tenantName);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder organizationName(String organizationName);
        public abstract Builder categories(Map<String, DataCategorySizesAndConfigurationResponse> categories);
        public abstract Builder subsystemSizes(Map<String, DataCategorySizesResponse> subsystemSizes);
        public abstract Builder calculatedAt(DateTime calculatedAt);
        public abstract Builder calculating(boolean calculating);
        public abstract Builder calculationError(String calculationError);

        public abstract TenantDataCategoriesResponse build();
    }

}
