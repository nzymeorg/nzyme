package app.nzyme.core.assets.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AssetEntryMapper implements RowMapper<AssetEntry> {
    @Override
    public AssetEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        List<String> hostnames = Arrays.asList((String[]) rs.getArray("hostnames").getArray());
        List<String> ipAddresses = Arrays.asList((String[]) rs.getArray("ip_addresses").getArray());

        return AssetEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("mac"),
                rs.getBoolean("is_active"),
                rs.getString("dhcp_fingerprint_initial"),
                rs.getString("dhcp_fingerprint_renew"),
                rs.getString("dhcp_fingerprint_reboot"),
                rs.getString("dhcp_fingerprint_rebind"),
                rs.getBoolean("seen_arp"),
                rs.getBoolean("seen_dhcp"),
                rs.getBoolean("seen_tcp"),
                rs.getBoolean("seen_udp"),
                hostnames,
                ipAddresses,
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("updated_at")),
                new DateTime(rs.getTimestamp("created_at"))
        );
    }
}
