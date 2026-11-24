package app.nzyme.core.search.sources;

import app.nzyme.core.monitors.MonitorType;
import app.nzyme.core.rest.responses.briefing.BriefingLinkResponse;
import app.nzyme.core.rest.responses.search.SearchHitResponse;
import app.nzyme.core.search.SearchConditions;
import app.nzyme.core.search.SearchScope;
import app.nzyme.core.search.SearchSource;
import app.nzyme.core.search.SearchTerm;
import app.nzyme.core.search.SqlSearchSource;
import com.google.common.collect.Lists;
import org.jdbi.v3.core.Handle;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ManagementSearch {

    public static List<SearchSource> sources() {
        return List.of(new Monitors(), new Dashboards(), new Locations(), new Taps(), new Administration());
    }

    private static class Monitors extends SqlSearchSource {

        @Override
        public String id() {
            return "monitors";
        }

        @Override
        public String title() {
            return "Monitors";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return !types(scope).isEmpty();
        }

        private static List<String> types(SearchScope scope) {
            return Arrays.stream(MonitorType.values())
                    .filter(type -> scope.hasSubsystem(type.getSubsystem())
                            && scope.hasPermission(type.getRequiredManagePermission()))
                    .map(Enum::name)
                    .toList();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchConditions conditions = new SearchConditions(term).uuid("m.uuid").contains("m.name", "m.description");

            String sql = "SELECT m.uuid, m.name, m.description, m.enabled, m.status, m.updated_at "
                    + "FROM monitors m "
                    + "WHERE m.organization_id = :organization_id AND m.tenant_id = :tenant_id "
                    + "AND m.type IN (<types>) AND " + conditions.sql() + " "
                    + "ORDER BY " + bestFirst("m.name") + ", m.name ASC, m.uuid ASC LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, null,
                    query -> query.bindList("types", types(request.scope())))) {
                hits.add(hit(
                        "MONITOR",
                        text(row, "name"),
                        text(row, "description"),
                        facts(term).add("Monitoring", bool(row, "enabled") ? "Enabled" : "Disabled").build(),
                        time(row, "updated_at"),
                        score(term, text(row, "name"), text(row, "uuid")),
                        link("MONITOR", "uuid", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request);
        }

    }

    private static class Dashboards extends SqlSearchSource {

        @Override
        public String id() {
            return "dashboards";
        }

        @Override
        public String title() {
            return "Dashboards";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return scope.dashboardsEnabled()
                    && (scope.hasPermission("dashboards_view") || scope.hasPermission("dashboards_manage"));
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            SearchConditions dashboards = new SearchConditions(term).uuid("d.uuid").contains("d.name", "d.description");

            for (Map<String, Object> row : rows(handle, "SELECT d.uuid, d.name, d.description, d.updated_at "
                    + "FROM dashboards d "
                    + "WHERE d.organization_id = :organization_id AND d.tenant_id = :tenant_id "
                    + "AND " + dashboards.sql() + " "
                    + "ORDER BY " + bestFirst("d.name") + ", d.name ASC, d.uuid ASC LIMIT :cap", dashboards, request)) {
                hits.add(hit(
                        "DASHBOARD",
                        text(row, "name"),
                        text(row, "description"),
                        facts(term).build(),
                        time(row, "updated_at"),
                        score(term, text(row, "name"), text(row, "uuid")),
                        link("DASHBOARD", "uuid", text(row, "uuid"))
                ));
            }

            if (request.scope().hasPermission("dashboards_manage")) {
                SearchConditions displays = new SearchConditions(term).uuid("p.uuid").contains("p.name");

                for (Map<String, Object> row : rows(handle, "SELECT p.uuid, p.name, p.last_seen_at "
                        + "FROM dashboard_displays p "
                        + "WHERE p.organization_id = :organization_id AND p.tenant_id = :tenant_id "
                        + "AND " + displays.sql() + " "
                        + "ORDER BY " + bestFirst("p.name") + ", p.name ASC, p.uuid ASC LIMIT :cap", displays, request)) {
                    hits.add(hit(
                            "DASHBOARD_DISPLAY",
                            text(row, "name"),
                            "Display",
                            facts(term).build(),
                            time(row, "last_seen_at"),
                            score(term, text(row, "name"), text(row, "uuid")),
                            link("DASHBOARD_DISPLAY", "uuid", text(row, "uuid"))
                    ));
                }
            }

            return merged(hits, request);
        }

    }

    private static class Locations extends SqlSearchSource {

        @Override
        public String id() {
            return "locations";
        }

        @Override
        public String title() {
            return "Locations";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return !term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.IP, SearchTerm.Kind.CIDR);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            SearchConditions locations = new SearchConditions(term).uuid("l.uuid").contains("l.name", "l.description");

            for (Map<String, Object> row : rows(handle, "SELECT l.uuid, l.name, l.description, l.updated_at "
                    + "FROM auth_tenants_locations l "
                    + "WHERE l.organization_id = :organization_id AND l.tenant_id = :tenant_id "
                    + "AND " + locations.sql() + " "
                    + "ORDER BY " + bestFirst("l.name") + ", l.name ASC, l.uuid ASC LIMIT :cap", locations, request)) {
                hits.add(hit(
                        "LOCATION",
                        text(row, "name"),
                        text(row, "description"),
                        facts(term).build(),
                        time(row, "updated_at"),
                        score(term, text(row, "name"), text(row, "uuid")),
                        link("LOCATION", "uuid", text(row, "uuid"))
                ));
            }

            SearchConditions floors = new SearchConditions(term).uuid("f.uuid").contains("f.name");

            for (Map<String, Object> row : rows(handle, "SELECT f.name, f.number, f.updated_at, "
                    + "l.uuid AS location_uuid, l.name AS location_name FROM auth_tenants_locations_floors f "
                    + "JOIN auth_tenants_locations l ON l.uuid = f.location_id "
                    + "WHERE l.organization_id = :organization_id AND l.tenant_id = :tenant_id "
                    + "AND " + floors.sql() + " "
                    + "ORDER BY l.name ASC, f.number ASC, f.uuid ASC LIMIT :cap", floors, request)) {
                hits.add(hit(
                        "FLOOR",
                        text(row, "name"),
                        "Floor of location " + text(row, "location_name"),
                        facts(term).build(),
                        time(row, "updated_at"),
                        score(term, text(row, "name")),
                        link("LOCATION", "uuid", text(row, "location_uuid"))
                ));
            }

            return merged(hits, request);
        }

    }

    /*
     * Taps and everything under Administration are searched across what the administrator administers, not
     * only the selected tenant: all of the node for a super administrator, the own organization otherwise.
     */
    private static class Taps extends SqlSearchSource {

        @Override
        public String id() {
            return "taps";
        }

        @Override
        public String title() {
            return "Taps";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return scope.isAdministrator() && !term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.CIDR);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchConditions conditions = new SearchConditions(term)
                    .uuid("t.uuid")
                    .contains("t.name", "t.description")
                    .custom("t.remote_address = :s_ip", SearchTerm.Kind.IP);

            String sql = "SELECT t.uuid, t.name, t.description, t.last_report, t.remote_address, "
                    + "o.name AS organization_name, te.name AS tenant_name FROM taps t "
                    + "LEFT JOIN auth_organizations o ON o.uuid = t.organization_id "
                    + "LEFT JOIN auth_tenants te ON te.uuid = t.tenant_id "
                    + "WHERE t.deleted = false AND " + administered(request.scope(), "t.organization_id") + " "
                    + "AND " + conditions.sql() + " "
                    + "ORDER BY " + bestFirst("t.name") + ", t.name ASC, t.uuid ASC LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request)) {
                hits.add(hit(
                        "TAP",
                        text(row, "name"),
                        text(row, "description"),
                        facts(term)
                                .add("Organization", row.get("organization_name"))
                                .add("Tenant", row.get("tenant_name"))
                                .add("Remote Address", row.get("remote_address"))
                                .build(),
                        time(row, "last_report"),
                        score(term, text(row, "name"), text(row, "uuid"), text(row, "remote_address")),
                        link("TAP", "uuid", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request);
        }

    }

    private static String administered(SearchScope scope, String organizationColumn) {
        return scope.superAdministrator() ? "TRUE" : organizationColumn + " = :organization_id";
    }

    private static class Administration extends SqlSearchSource {

        @Override
        public String id() {
            return "administration";
        }

        @Override
        public String title() {
            return "Administration";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return scope.isAdministrator()
                    && !term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.IP, SearchTerm.Kind.CIDR);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchScope scope = request.scope();
            List<SearchHitResponse> hits = Lists.newArrayList();

            SearchConditions organizations = new SearchConditions(term)
                    .uuid("o.uuid")
                    .contains("o.name", "o.description");

            for (Map<String, Object> row : rows(handle, "SELECT o.uuid, o.name, o.description, o.updated_at "
                    + "FROM auth_organizations o WHERE " + administered(scope, "o.uuid") + " "
                    + "AND " + organizations.sql() + " "
                    + "ORDER BY " + bestFirst("o.name") + ", o.name ASC, o.uuid ASC LIMIT :cap", organizations, request)) {
                hits.add(hit(
                        "ORGANIZATION",
                        text(row, "name"),
                        text(row, "description"),
                        facts(term).build(),
                        time(row, "updated_at"),
                        score(term, text(row, "name"), text(row, "uuid")),
                        link("ORGANIZATION", "uuid", text(row, "uuid"))
                ));
            }

            SearchConditions tenants = new SearchConditions(term).uuid("t.uuid").contains("t.name", "t.description");

            for (Map<String, Object> row : rows(handle, "SELECT t.uuid, t.organization_id, t.name, t.description, "
                    + "t.updated_at, o.name AS organization_name FROM auth_tenants t "
                    + "LEFT JOIN auth_organizations o ON o.uuid = t.organization_id "
                    + "WHERE " + administered(scope, "t.organization_id") + " "
                    + "AND " + tenants.sql() + " "
                    + "ORDER BY " + bestFirst("t.name") + ", t.name ASC, t.uuid ASC LIMIT :cap", tenants, request)) {
                hits.add(hit(
                        "TENANT",
                        text(row, "name"),
                        text(row, "description"),
                        facts(term).add("Organization", row.get("organization_name")).build(),
                        time(row, "updated_at"),
                        score(term, text(row, "name"), text(row, "uuid")),
                        link("TENANT", "organization", text(row, "organization_id"), "tenant", text(row, "uuid"))
                ));
            }

            SearchConditions users = new SearchConditions(term).uuid("u.uuid").contains("u.name", "u.email");

            for (Map<String, Object> row : rows(handle, "SELECT u.uuid, u.name, u.email, u.is_superadmin, "
                    + "u.is_orgadmin, u.organization_id, u.tenant_id, u.last_activity, "
                    + "o.name AS organization_name, t.name AS tenant_name FROM auth_users u "
                    + "LEFT JOIN auth_organizations o ON o.uuid = u.organization_id "
                    + "LEFT JOIN auth_tenants t ON t.uuid = u.tenant_id "
                    + "WHERE " + (scope.superAdministrator()
                            ? "TRUE" : "u.organization_id = :organization_id AND u.is_superadmin = false") + " "
                    + "AND " + users.sql() + " "
                    + "ORDER BY " + bestFirst("u.name") + ", u.name ASC, u.uuid ASC LIMIT :cap", users, request)) {
                String uuid = text(row, "uuid");
                String organization = text(row, "organization_id");
                String tenant = text(row, "tenant_id");

                String role;
                BriefingLinkResponse link;
                if (bool(row, "is_superadmin")) {
                    role = "Super administrator";
                    link = link("SUPER_ADMINISTRATOR", "uuid", uuid);
                } else if (bool(row, "is_orgadmin")) {
                    role = "Organization administrator";
                    link = link("ORGANIZATION_ADMINISTRATOR", "organization", organization, "uuid", uuid);
                } else if (organization != null && tenant != null) {
                    role = "User";
                    link = link("TENANT_USER", "organization", organization, "tenant", tenant, "uuid", uuid);
                } else {
                    continue;
                }

                hits.add(hit(
                        "USER",
                        text(row, "name"),
                        role,
                        facts(term)
                                .add("Email", row.get("email"))
                                .add("Organization", row.get("organization_name"))
                                .add("Tenant", row.get("tenant_name"))
                                .build(),
                        time(row, "last_activity"),
                        score(term, text(row, "name"), text(row, "email"), uuid),
                        link
                ));
            }

            SearchConditions actions = new SearchConditions(term).uuid("a.uuid").contains("a.name", "a.description");

            for (Map<String, Object> row : rows(handle, "SELECT a.uuid, a.name, a.description, a.organization_id, "
                    + "a.updated_at FROM event_actions a WHERE " + administered(scope, "a.organization_id") + " "
                    + "AND " + actions.sql() + " "
                    + "ORDER BY " + bestFirst("a.name") + ", a.name ASC, a.uuid ASC LIMIT :cap", actions, request)) {
                String organization = text(row, "organization_id");

                hits.add(hit(
                        "EVENT_ACTION",
                        text(row, "name"),
                        text(row, "description"),
                        facts(term).build(),
                        time(row, "updated_at"),
                        score(term, text(row, "name"), text(row, "uuid")),
                        organization == null
                                ? link("EVENT_ACTION", "uuid", text(row, "uuid"))
                                : link("ORGANIZATION_EVENT_ACTION", "organization", organization,
                                        "uuid", text(row, "uuid"))
                ));
            }

            if (scope.superAdministrator()) {
                SearchConditions nodes = new SearchConditions(term).uuid("n.uuid").contains("n.name");

                for (Map<String, Object> row : rows(handle, "SELECT n.uuid, n.name, n.version, n.last_seen "
                        + "FROM nodes n WHERE n.deleted = false AND " + nodes.sql() + " "
                        + "ORDER BY " + bestFirst("n.name") + ", n.name ASC, n.uuid ASC LIMIT :cap", nodes, request)) {
                    hits.add(hit(
                            "NODE",
                            text(row, "name"),
                            "Node",
                            facts(term).add("Version", row.get("version")).build(),
                            time(row, "last_seen"),
                            score(term, text(row, "name"), text(row, "uuid")),
                            link("NODE", "uuid", text(row, "uuid"))
                    ));
                }
            }

            return merged(hits, request);
        }

    }

}
