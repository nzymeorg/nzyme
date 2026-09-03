package app.nzyme.core.bluetooth.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class BluetoothDeviceEntryMapper implements RowMapper<BluetoothDeviceEntry> {

    @Override
    public BluetoothDeviceEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return BluetoothDeviceEntry.create(
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("tap_uuid")),
                rs.getString("mac"),
                rs.getString("oui"),
                rs.getString("alias"),
                rs.getString("device"),
                rs.getString("transport"),
                rs.getString("name"),
                rs.getInt("rssi"),
                rs.getInt("company_id"),
                rs.getInt("class_number"),
                rs.getInt("appearance"),
                rs.getString("modalias"),
                rs.getInt("tx_power"),
                rs.getString("manufacturer_data"),
                rs.getString("manufacturer_name"),
                rs.getString("uuids"),
                rs.getString("service_data"),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at"))
        );
    }

}
