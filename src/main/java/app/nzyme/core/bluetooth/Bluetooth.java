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

    public long countAllDevices(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BluetoothDeviceFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT d.mac FROM bluetooth_devices AS d " +
                                "LEFT JOIN LATERAL (SELECT DISTINCT jsonb_object_keys(d.tags) AS tag) AS ignore ON true " +
                                "WHERE d.last_seen >= :tr_from AND d.last_seen <= :tr_to " +
                                "AND d.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY d.mac HAVING 1=1 " + filterFragment.havingSql() +
                                ") AS devices")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
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
                handle.createQuery("SELECT d.mac, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.oui) " +
                                "FILTER (WHERE d.oui IS NOT NULL), ARRAY[]::text[]) AS ouis, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.manufacturer_name) " +
                                "FILTER (WHERE d.manufacturer_name IS NOT NULL), ARRAY[]::text[]) AS manufacturer_names, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.alias) " +
                                "FILTER (WHERE d.alias IS NOT NULL), ARRAY[]::text[]) AS aliases, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.device) " +
                                "FILTER (WHERE d.device IS NOT NULL), ARRAY[]::text[]) AS devices, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.transport) " +
                                "FILTER (WHERE d.transport IS NOT NULL), ARRAY[]::text[]) AS transports, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.name) " +
                                "FILTER (WHERE d.name IS NOT NULL), ARRAY[]::text[]) AS names, " +
                                "AVG(d.rssi) AS average_rssi, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.company_id) " +
                                "FILTER (WHERE d.company_id IS NOT NULL), ARRAY[]::int[]) AS company_ids, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.uuids) " +
                                "FILTER (WHERE d.uuids IS NOT NULL), ARRAY[]::text[]) AS service_uuids, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.class_number) " +
                                "FILTER (WHERE d.class_number IS NOT NULL), ARRAY[]::int[]) AS class_numbers, " +
                                "COALESCE(ARRAY_AGG(DISTINCT tag) " +
                                "FILTER (WHERE tag IS NOT NULL), ARRAY[]::text[]) AS tags, " +
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

    public List<GenericIntegerHistogramEntry> getDeviceCountHistogram(TimeRange timeRange,
                                                                      Filters filters,
                                                                      Bucketing.BucketingConfiguration bucketing,
                                                                      List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BluetoothDeviceFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, d.last_seen) AS bucket, " +
                                "COUNT(DISTINCT d.mac) AS value " +
                                "FROM bluetooth_devices AS d " +
                                "LEFT JOIN LATERAL (SELECT DISTINCT jsonb_object_keys(d.tags) AS tag) AS ignore ON true " +
                                "WHERE d.tap_uuid IN (<taps>) AND d.last_seen >= :tr_from " +
                                "AND d.last_seen <= :tr_to " + filterFragment.whereSql() +
                                "GROUP BY bucket HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY bucket DESC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public Optional<BluetoothDeviceSummary> findOneDevice(String mac, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT d.mac, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.oui) " +
                                "FILTER (WHERE d.oui IS NOT NULL), ARRAY[]::text[]) AS ouis, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.manufacturer_name) " +
                                "FILTER (WHERE d.manufacturer_name IS NOT NULL), ARRAY[]::text[]) AS manufacturer_names, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.alias) " +
                                "FILTER (WHERE d.alias IS NOT NULL), ARRAY[]::text[]) AS aliases, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.device) " +
                                "FILTER (WHERE d.device IS NOT NULL), ARRAY[]::text[]) AS devices, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.transport) " +
                                "FILTER (WHERE d.transport IS NOT NULL), ARRAY[]::text[]) AS transports, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.name) " +
                                "FILTER (WHERE d.name IS NOT NULL), ARRAY[]::text[]) AS names, " +
                                "AVG(d.rssi) AS average_rssi, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.company_id) " +
                                "FILTER (WHERE d.company_id IS NOT NULL), ARRAY[]::int[]) AS company_ids, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.uuids) " +
                                "FILTER (WHERE d.uuids IS NOT NULL), ARRAY[]::text[]) AS service_uuids, " +
                                "COALESCE(ARRAY_AGG(DISTINCT d.class_number) " +
                                "FILTER (WHERE d.class_number IS NOT NULL), ARRAY[]::int[]) AS class_numbers, " +
                                "COALESCE(ARRAY_AGG(DISTINCT tag) " +
                                "FILTER (WHERE tag IS NOT NULL), ARRAY[]::text[]) AS tags, " +
                                "MIN(d.last_seen) AS first_seen, MAX(d.last_seen) AS last_seen " +
                                "FROM bluetooth_devices AS d " +
                                "LEFT JOIN LATERAL (SELECT DISTINCT jsonb_object_keys(d.tags) AS tag) AS ignore ON true " +
                                "WHERE mac = :mac AND d.tap_uuid IN (<taps>) " +
                                "GROUP BY d.mac")
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
