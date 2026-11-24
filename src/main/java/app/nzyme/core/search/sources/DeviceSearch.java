package app.nzyme.core.search.sources;

import app.nzyme.core.rest.responses.search.SearchHitResponse;
import app.nzyme.core.search.SearchConditions;
import app.nzyme.core.search.SearchScope;
import app.nzyme.core.search.SearchSource;
import app.nzyme.core.search.SearchTerm;
import app.nzyme.core.search.SearchWindow;
import app.nzyme.core.search.SqlSearchSource;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.Map;

public class DeviceSearch {

    public static List<SearchSource> sources() {
        return List.of(new BluetoothDevices(), new Uavs());
    }

    private static class BluetoothDevices extends SqlSearchSource {

        @Override
        public String id() {
            return "bluetooth_devices";
        }

        @Override
        public String title() {
            return "Bluetooth Devices";
        }

        @Override
        public Tier tier() {
            return Tier.RECORDED;
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return scope.hasSubsystem(Subsystem.BLUETOOTH) && scope.hasTaps()
                    && (term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX) || term.isRecordedText());
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = request.windowOr(term.is(SearchTerm.Kind.MAC)
                    ? SearchWindow.all() : SearchWindow.lastHours(SearchWindow.DEFAULT_SCAN_HOURS));

            SearchConditions conditions = new SearchConditions(term).mac("d.mac");
            if (term.isRecordedText()) {
                conditions.contains("d.name", "d.alias", "d.manufacturer_name");
            }

            String sql = "SELECT d.mac, max(d.last_seen) AS last_seen, max(d.name) AS name, max(d.alias) AS alias, "
                    + "max(d.manufacturer_name) AS manufacturer, max(d.oui) AS oui, max(d.transport) AS transport "
                    + "FROM bluetooth_devices d "
                    + "WHERE d.tap_uuid IN (<taps>) AND d.last_seen >= :w_from AND d.last_seen <= :w_to "
                    + "AND " + conditions.sql() + " "
                    + "GROUP BY d.mac ORDER BY last_seen DESC, d.mac ASC LIMIT :limit OFFSET :offset";

            // Over all data, only the newest row of the address is read.
            if (term.is(SearchTerm.Kind.MAC) && request.window() == null) {
                sql = "SELECT d.mac, d.last_seen, d.name, d.alias, d.manufacturer_name AS manufacturer, d.oui, "
                        + "d.transport FROM bluetooth_devices d WHERE d.mac = :s_mac AND d.tap_uuid IN (<taps>) "
                        + "ORDER BY d.last_seen DESC LIMIT 1 OFFSET :offset";
            }

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                String mac = text(row, "mac");
                String name = text(row, "name") == null ? text(row, "alias") : text(row, "name");
                boolean named = name != null && !name.equalsIgnoreCase(mac.replace(":", "-"));

                hits.add(hit(
                        "BLUETOOTH_DEVICE",
                        named ? name : mac,
                        "Bluetooth device",
                        facts(term)
                                .add("MAC Address", named ? mac : null)
                                .add("Manufacturer", row.get("manufacturer"))
                                .add("Vendor", row.get("oui"))
                                .build(),
                        time(row, "last_seen"),
                        score(term, mac, name),
                        link("BLUETOOTH_DEVICE", "mac", mac)
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class Uavs extends SqlSearchSource {

        @Override
        public String id() {
            return "uavs";
        }

        @Override
        public String title() {
            return "UAVs";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return scope.hasSubsystem(Subsystem.UAV) && !term.isAnyOf(SearchTerm.Kind.IP, SearchTerm.Kind.CIDR);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            if (request.scope().hasTaps()) {
                SearchConditions uavs = new SearchConditions(term).contains("u.identifier", "u.designation",
                        "u.id_serial", "u.id_registration", "u.id_utm", "u.operator_id");

                for (Map<String, Object> row : rows(handle, "SELECT u.identifier, max(u.designation) AS designation, "
                        + "max(u.uav_type) AS uav_type, max(u.id_serial) AS serial, "
                        + "max(u.id_registration) AS registration, max(u.operator_id) AS operator_id, "
                        + "max(u.last_seen) AS last_seen, "
                        + "(SELECT c.classification FROM uavs_classifications c "
                        + "WHERE c.uav_identifier = u.identifier AND c.organization_id = :organization_id "
                        + "AND c.tenant_id = :tenant_id LIMIT 1) AS classification "
                        + "FROM uavs u WHERE u.tap_uuid IN (<taps>) AND " + uavs.sql() + " "
                        + "GROUP BY u.identifier ORDER BY last_seen DESC, u.identifier ASC "
                        + "LIMIT :cap", uavs, request)) {
                    String identifier = text(row, "identifier");
                    String designation = text(row, "designation");

                    hits.add(hit(
                            "UAV",
                            designation == null ? identifier : designation,
                            "UAV",
                            facts(term)
                                    .add("Serial Number", row.get("serial"))
                                    .add("Registration", row.get("registration"))
                                    .add("Operator", row.get("operator_id"))
                                    .add("Classification", row.get("classification"))
                                    .build(),
                            time(row, "last_seen"),
                            score(term, identifier, designation, text(row, "serial"), text(row, "registration")),
                            link("UAV", "identifier", identifier)
                    ));
                }
            }

            SearchConditions types = new SearchConditions(term).contains("t.name", "t.model", "t.match_value");

            for (Map<String, Object> row : rows(handle, "SELECT t.uuid, t.name, t.model, t.match_value, t.updated_at "
                    + "FROM uavs_types t "
                    + "WHERE t.organization_id = :organization_id AND t.tenant_id = :tenant_id "
                    + "AND " + types.sql() + " "
                    + "ORDER BY " + bestFirst("t.name") + ", t.name ASC, t.uuid ASC LIMIT :cap", types, request)) {
                hits.add(hit(
                        "UAV_TYPE",
                        text(row, "name"),
                        "Custom UAV type",
                        facts(term).add("Model", row.get("model")).add("Matches", row.get("match_value")).build(),
                        time(row, "updated_at"),
                        score(term, text(row, "name"), text(row, "model")),
                        link("UAV_TYPES")
                ));
            }

            return merged(hits, request);
        }

    }

}
