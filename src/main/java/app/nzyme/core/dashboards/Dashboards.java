package app.nzyme.core.dashboards;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dashboards.db.DashboardEntry;
import app.nzyme.core.dashboards.db.DisplayDashboard;
import app.nzyme.core.dashboards.db.DisplayEntry;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.SqlStatement;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Dashboards {

    public static final int MAXIMUM_DASHBOARDS_PER_DISPLAY = 25;

    private static final String SELECT_DASHBOARDS = "SELECT d.*, (SELECT COUNT(*) " +
            "FROM dashboard_display_assignments a WHERE a.dashboard_id = d.id) AS display_count FROM dashboards d ";

    private static final String SELECT_DISPLAYS = "SELECT p.*, l.name AS location_name FROM dashboard_displays p " +
            "LEFT JOIN auth_tenants_locations l ON l.uuid = p.location_id ";

    private static final String WITH_DASHBOARD = "AND EXISTS (SELECT 1 FROM dashboard_display_assignments a " +
            "WHERE a.display_id = p.id AND a.dashboard_id = :dashboard_id) ";

    private static final String AT_LOCATION = "AND p.location_id = :location_id ";

    private final NzymeNode nzyme;
    private final ObjectMapper om = JsonMapper.builder().build();
    private final DashboardContent content;
    private final DashboardTopology topology;

    public Dashboards(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.content = new DashboardContent(nzyme);
        this.topology = new DashboardTopology(nzyme, content);
    }

    public DashboardContent content() {
        return content;
    }

    public DashboardTopology topology() {
        return topology;
    }

    public boolean isEnabled(UUID organizationId, UUID tenantId) {
        return Boolean.parseBoolean(nzyme.getDatabaseCoreRegistry()
                .getValue(DashboardsRegistryKeys.DASHBOARDS_ENABLED.key(), organizationId, tenantId)
                .orElse(DashboardsRegistryKeys.DASHBOARDS_ENABLED.defaultValue().get()));
    }

    public long countDashboardsOfOrganization(UUID organizationId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dashboards WHERE organization_id = :organization_id")
                        .bind("organization_id", organizationId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public long countDashboards(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dashboards " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DashboardEntry> findAllDashboards(UUID organizationId, UUID tenantId, int limit, int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_DASHBOARDS +
                                "WHERE d.organization_id = :organization_id AND d.tenant_id = :tenant_id " +
                                "ORDER BY d.name ASC, d.id ASC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(DashboardEntry.class)
                        .list()
        );
    }

    public Optional<DashboardEntry> findDashboard(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_DASHBOARDS + "WHERE d.uuid = :uuid " +
                                "AND d.organization_id = :organization_id AND d.tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DashboardEntry.class)
                        .findOne()
        );
    }

    public List<DashboardEntry> findDashboards(List<UUID> uuids, UUID organizationId, UUID tenantId) {
        if (uuids.isEmpty()) {
            return Lists.newArrayList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_DASHBOARDS + "WHERE d.uuid IN (<uuids>) " +
                                "AND d.organization_id = :organization_id AND d.tenant_id = :tenant_id")
                        .bindList("uuids", uuids)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DashboardEntry.class)
                        .list()
        );
    }

    public DashboardEntry createDashboard(UUID organizationId, UUID tenantId, DashboardSettings settings) {
        return nzyme.getDatabase().withHandle(handle ->
                bindSettings(settings, handle.createQuery("INSERT INTO dashboards(uuid, organization_id, tenant_id, " +
                                "name, description, scope_type, scope_ids, window_minutes, widgets, created_at, " +
                                "updated_at) VALUES(:uuid, :organization_id, :tenant_id, :name, :description, " +
                                ":scope_type, :scope_ids::jsonb, :window_minutes, :widgets::jsonb, NOW(), NOW()) " +
                                "RETURNING *, 0::bigint AS display_count")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId))
                        .mapTo(DashboardEntry.class)
                        .one()
        );
    }

    public void updateDashboard(long id, DashboardSettings settings) {
        nzyme.getDatabase().useHandle(handle ->
                bindSettings(settings, handle.createUpdate("UPDATE dashboards SET name = :name, " +
                                "description = :description, scope_type = :scope_type, " +
                                "scope_ids = :scope_ids::jsonb, window_minutes = :window_minutes, " +
                                "widgets = :widgets::jsonb, updated_at = NOW() WHERE id = :id")
                        .bind("id", id))
                        .execute()
        );
    }

    public void deleteDashboard(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dashboards WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public long countDisplaysOfOrganization(UUID organizationId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dashboard_displays WHERE organization_id = :organization_id")
                        .bind("organization_id", organizationId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public long countDisplays(UUID organizationId,
                              UUID tenantId,
                              @Nullable Long dashboardId,
                              @Nullable UUID locationId) {
        return nzyme.getDatabase().withHandle(handle -> {
            Query query = handle.createQuery("SELECT COUNT(*) FROM dashboard_displays p " +
                            "WHERE p.organization_id = :organization_id AND p.tenant_id = :tenant_id " +
                            (dashboardId == null ? "" : WITH_DASHBOARD) +
                            (locationId == null ? "" : AT_LOCATION))
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId);

            bindDisplayFilters(query, dashboardId, locationId);

            return query.mapTo(Long.class).one();
        });
    }

    public List<DisplayEntry> findAllDisplays(UUID organizationId,
                                              UUID tenantId,
                                              @Nullable Long dashboardId,
                                              @Nullable UUID locationId,
                                              int limit,
                                              int offset) {
        return nzyme.getDatabase().withHandle(handle -> {
            Query query = handle.createQuery(SELECT_DISPLAYS +
                            "WHERE p.organization_id = :organization_id AND p.tenant_id = :tenant_id " +
                            (dashboardId == null ? "" : WITH_DASHBOARD) +
                            (locationId == null ? "" : AT_LOCATION) +
                            "ORDER BY p.name ASC, p.id ASC LIMIT :limit OFFSET :offset")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("limit", limit)
                    .bind("offset", offset);

            bindDisplayFilters(query, dashboardId, locationId);

            return query.mapTo(DisplayEntry.class).list();
        });
    }

    public Optional<DisplayEntry> findDisplay(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_DISPLAYS + "WHERE p.uuid = :uuid " +
                                "AND p.organization_id = :organization_id AND p.tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DisplayEntry.class)
                        .findOne()
        );
    }

    public Optional<DisplayEntry> findValidDisplayByTokenHash(String tokenHash) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_DISPLAYS + "WHERE p.token_hash = :token_hash " +
                                "AND (p.expires_at IS NULL OR p.expires_at > NOW())")
                        .bind("token_hash", tokenHash)
                        .mapTo(DisplayEntry.class)
                        .findOne()
        );
    }

    public Map<Long, List<DisplayDashboard>> findDashboardsOfDisplays(List<Long> displayIds) {
        Map<Long, List<DisplayDashboard>> result = Maps.newHashMap();
        if (displayIds.isEmpty()) {
            return result;
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createQuery("SELECT a.display_id, d.uuid, d.name FROM dashboard_display_assignments a " +
                                "JOIN dashboards d ON d.id = a.dashboard_id WHERE a.display_id IN (<display_ids>) " +
                                "ORDER BY a.display_id ASC, a.position ASC")
                        .bindList("display_ids", displayIds)
                        .map((rs, ctx) -> Map.entry(
                                rs.getLong("display_id"),
                                new DisplayDashboard(UUID.fromString(rs.getString("uuid")), rs.getString("name"))
                        ))
                        .forEach(row -> result.computeIfAbsent(row.getKey(), k -> Lists.newArrayList())
                                .add(row.getValue()))
        );

        return result;
    }

    public List<DisplayDashboard> findDashboardsOfDisplay(long displayId) {
        return findDashboardsOfDisplays(List.of(displayId)).getOrDefault(displayId, Lists.newArrayList());
    }

    public DisplayEntry createDisplay(UUID organizationId,
                                      UUID tenantId,
                                      DisplaySettings settings,
                                      String tokenHash,
                                      List<Long> dashboardIds) {
        return nzyme.getDatabase().inTransaction(handle -> {
            DisplayEntry display = bindSettings(settings, handle.createQuery("WITH p AS (INSERT INTO dashboard_displays(uuid, " +
                            "organization_id, tenant_id, name, location_id, token_hash, theme, light_from, " +
                            "light_until, time_zone, clock_format, scale_percent, rotation_seconds, " +
                            "refresh_seconds, allowed_networks, redacted, hide_cursor, expires_at, created_at, updated_at) " +
                            "VALUES(:uuid, :organization_id, :tenant_id, :name, :location_id, :token_hash, " +
                            ":theme, :light_from, :light_until, :time_zone, :clock_format, :scale_percent, " +
                            ":rotation_seconds, :refresh_seconds, :allowed_networks::jsonb, :redacted, :hide_cursor, :expires_at, NOW(), " +
                            "NOW()) RETURNING *) SELECT p.*, l.name AS location_name FROM p " +
                            "LEFT JOIN auth_tenants_locations l ON l.uuid = p.location_id")
                    .bind("uuid", UUID.randomUUID())
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("token_hash", tokenHash))
                    .mapTo(DisplayEntry.class)
                    .one();

            assignDashboards(handle, display.id(), dashboardIds);

            return display;
        });
    }

    public void updateDisplay(long id, DisplaySettings settings, List<Long> dashboardIds) {
        nzyme.getDatabase().useTransaction(handle -> {
            bindSettings(settings, handle.createUpdate("UPDATE dashboard_displays SET name = :name, location_id = :location_id, " +
                            "theme = :theme, light_from = :light_from, light_until = :light_until, " +
                            "time_zone = :time_zone, clock_format = :clock_format, scale_percent = :scale_percent, " +
                            "rotation_seconds = :rotation_seconds, refresh_seconds = :refresh_seconds, " +
                            "allowed_networks = :allowed_networks::jsonb, redacted = :redacted, hide_cursor = :hide_cursor, " +
                            "expires_at = :expires_at, " +
                            "updated_at = NOW() WHERE id = :id")
                    .bind("id", id))
                    .execute();

            handle.createUpdate("DELETE FROM dashboard_display_assignments WHERE display_id = :display_id")
                    .bind("display_id", id)
                    .execute();

            assignDashboards(handle, id, dashboardIds);
        });
    }

    public void replaceDisplayToken(long id, String tokenHash) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dashboard_displays SET token_hash = :token_hash, updated_at = NOW() " +
                                "WHERE id = :id")
                        .bind("id", id)
                        .bind("token_hash", tokenHash)
                        .execute()
        );
    }

    public void recordDisplayContact(long id, @Nullable String remoteAddress, @Nullable String reportedVersion) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dashboard_displays SET last_seen_at = NOW(), " +
                                "last_remote_address = :remote_address, last_reported_version = :reported_version " +
                                "WHERE id = :id")
                        .bind("id", id)
                        .bind("remote_address", remoteAddress)
                        .bind("reported_version", reportedVersion)
                        .execute()
        );
    }

    public void deleteDisplay(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dashboard_displays WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    private static void bindDisplayFilters(Query query, @Nullable Long dashboardId, @Nullable UUID locationId) {
        if (dashboardId != null) {
            query.bind("dashboard_id", dashboardId);
        }

        if (locationId != null) {
            query.bind("location_id", locationId);
        }
    }

    private void assignDashboards(Handle handle, long displayId, List<Long> dashboardIds) {
        for (int position = 0; position < dashboardIds.size(); position++) {
            handle.createUpdate("INSERT INTO dashboard_display_assignments(display_id, dashboard_id, position) " +
                            "VALUES(:display_id, :dashboard_id, :position)")
                    .bind("display_id", displayId)
                    .bind("dashboard_id", dashboardIds.get(position))
                    .bind("position", position)
                    .execute();
        }
    }

    private <T extends SqlStatement<T>> T bindSettings(DashboardSettings settings, T statement) {
        return statement
                .bind("name", settings.name())
                .bind("description", settings.description())
                .bind("scope_type", settings.scope().name())
                .bind("scope_ids", om.writeValueAsString(settings.scopeIds()))
                .bind("window_minutes", settings.windowMinutes())
                .bind("widgets", om.writeValueAsString(settings.widgets()));
    }

    private <T extends SqlStatement<T>> T bindSettings(DisplaySettings settings, T statement) {
        return statement
                .bind("name", settings.name())
                .bind("location_id", settings.locationId())
                .bind("theme", settings.theme().name())
                .bind("light_from", settings.lightFrom())
                .bind("light_until", settings.lightUntil())
                .bind("time_zone", settings.timeZone())
                .bind("clock_format", settings.clockFormat().name())
                .bind("scale_percent", settings.scalePercent())
                .bind("rotation_seconds", settings.rotationSeconds())
                .bind("refresh_seconds", settings.refreshSeconds())
                .bind("allowed_networks", om.writeValueAsString(settings.allowedNetworks()))
                .bind("redacted", settings.redacted())
                .bind("hide_cursor", settings.hideCursor())
                .bind("expires_at", settings.expiresAt());
    }

}
