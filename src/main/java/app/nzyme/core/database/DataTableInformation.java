package app.nzyme.core.database;

import jakarta.annotation.Nullable;
import org.jdbi.v3.core.statement.SqlStatement;

import java.util.UUID;

public class DataTableInformation {

    private final String tableName;
    private final String rowCountQuery;

    @Nullable
    private final String purgeQuery;

    private final boolean tenantScoped;

    public DataTableInformation(String tableName, String rowCountQuery, String purgeQuery) {
        this(tableName, rowCountQuery, purgeQuery, false);
    }

    // Tenant scoped queries use a <scope> placeholder bound by organization and tenant instead of the <taps> list,
    // so a tenant without any live tap is still counted and cleaned.
    public DataTableInformation(String tableName, String rowCountQuery, String purgeQuery, boolean tenantScoped) {
        this.tableName = tableName;
        this.rowCountQuery = rowCountQuery;
        this.purgeQuery = purgeQuery;
        this.tenantScoped = tenantScoped;
    }

    public String getTableName() {
        return tableName;
    }

    public String getRowCountQuery() {
        return rowCountQuery;
    }

    @Nullable
    public String getPurgeQuery() {
        return purgeQuery;
    }

    public boolean isTenantScoped() {
        return tenantScoped;
    }

    public static <T extends SqlStatement<T>> T bindTenantScope(T statement,
                                                                 @Nullable UUID organizationId,
                                                                 @Nullable UUID tenantId) {
        if (organizationId == null) {
            return statement.define("scope", "TRUE");
        }

        if (tenantId == null) {
            return statement.define("scope", "organization_id = :organization_id")
                    .bind("organization_id", organizationId);
        }

        return statement.define("scope", "organization_id = :organization_id AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId);
    }

}
