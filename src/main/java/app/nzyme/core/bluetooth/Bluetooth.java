package app.nzyme.core.bluetooth;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.bluetooth.db.BluetoothDeviceSummary;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Bluetooth {

    private final NzymeNode nzyme;

    public enum OrderColumn {

        MAC("mac"),
        AVERAGE_RSSI("average_rssi"),
        TAGS("tags"),
        TRANSPORTS("transports"),
        NAMES("names"),
        LAST_SEEN("last_seen");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public Bluetooth(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public long countAllDevices(TimeRange timeRange, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT(d.mac)) FROM bluetooth_devices AS d " +
                                "WHERE d.last_seen >= :tr_from AND d.last_seen <= :tr_to AND d.tap_uuid IN (<taps>)")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .first()
        );
    }

    public List<BluetoothDeviceSummary> findAllDevices(TimeRange timeRange,
                                                       Filters filters,
                                                       OrderColumn orderColumn,
                                                       OrderDirection orderDirection,
                                                       int limit,
                                                       int offset,
                                                       List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BluetoothDeviceFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT d.mac, ARRAY_AGG(DISTINCT(d.alias)) AS aliases, " +
                                "ARRAY_AGG(DISTINCT(d.device)) AS devices, " +
                                "ARRAY_AGG(DISTINCT(d.transport)) AS transports, " +
                                "ARRAY_AGG(DISTINCT(d.name)) AS names, " +
                                "AVG(d.rssi) AS average_rssi, " +
                                "ARRAY_AGG(DISTINCT(COALESCE(d.company_id, 0))) AS company_ids, " +
                                "ARRAY_AGG(DISTINCT(COALESCE(d.uuids, '[]'))) AS service_uuids, " +
                                "ARRAY_AGG(DISTINCT(COALESCE(d.class_number, 0))) AS class_numbers, " +
                                "ARRAY_AGG(DISTINCT tag) AS tags, " +
                                "MIN(d.last_seen) AS first_seen, MAX(d.last_seen) AS last_seen " +
                                "FROM bluetooth_devices AS d " +
                                "LEFT JOIN LATERAL (SELECT DISTINCT jsonb_object_keys(d.tags) AS tag) AS ignore ON true " +
                                "WHERE d.last_seen >= :tr_from AND d.last_seen <= :tr_to " +
                                "AND d.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY d.mac HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindList("taps", taps)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bindMap(filterFragment.bindings())
                        .mapTo(BluetoothDeviceSummary.class)
                        .list()
        );
    }

    public Optional<BluetoothDeviceSummary> findOneDevice(String mac, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT d.mac, ARRAY_AGG(DISTINCT(d.alias)) AS aliases, " +
                                "ARRAY_AGG(DISTINCT(d.device)) AS devices, " +
                                "ARRAY_AGG(DISTINCT(d.transport)) AS transports, " +
                                "ARRAY_AGG(DISTINCT(COALESCE(d.name, 'None'))) AS names, " +
                                "AVG(d.rssi) AS average_rssi, " +
                                "ARRAY_AGG(DISTINCT(COALESCE(d.company_id, 0))) AS company_ids, " +
                                "ARRAY_AGG(DISTINCT(COALESCE(d.uuids, '[]'))) AS service_uuids, " +
                                "ARRAY_AGG(DISTINCT(COALESCE(d.class_number, 0))) AS class_numbers, " +
                                "ARRAY_AGG(DISTINCT tag) AS tags, " +
                                "MIN(d.last_seen) AS first_seen, MAX(d.last_seen) AS last_seen " +
                                "FROM bluetooth_devices AS d " +
                                "LEFT JOIN LATERAL (SELECT DISTINCT jsonb_object_keys(d.tags) AS tag) AS ignore ON true " +
                                "WHERE mac = :mac AND d.tap_uuid IN (<taps>) " +
                                "GROUP BY d.mac ")
                        .bind("mac", mac)
                        .bindList("taps", taps)
                        .mapTo(BluetoothDeviceSummary.class)
                        .findOne()
        );
    }

    public List<GenericIntegerHistogramEntry> getDeviceSignalStrengthHistogram(String mac,
                                                                               TimeRange timeRange,
                                                                               Bucketing.BucketingConfiguration bucketing,
                                                                               List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT AVG(rssi) AS value," +
                                "DATE_TRUNC(:date_trunc, created_at) AS bucket FROM bluetooth_devices " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) AND mac = :mac " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bindList("taps", taps)
                        .bind("mac", mac)
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public List<TapBasedSignalStrengthResult> getDeviceSignalStrengthPerTap(String mac,
                                                                             TimeRange timeRange,
                                                                             List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT d.tap_uuid AS tap_uuid, t.name AS tap_name, " +
                                "AVG(d.rssi) AS signal_strength " +
                                "FROM bluetooth_devices AS d " +
                                "LEFT JOIN taps AS t ON d.tap_uuid = t.uuid " +
                                "WHERE d.mac = :mac  AND d.tap_uuid IN (<taps>) " +
                                "AND d.created_at >= :tr_from AND d.created_at <= :tr_to " +
                                "GROUP BY d.tap_uuid, t.name ORDER BY signal_strength DESC")
                        .bind("mac", mac)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(TapBasedSignalStrengthResult.class)
                        .list()
        );
    }

}
