package app.nzyme.core.ethernet.nat.db;

import app.nzyme.core.ethernet.L4MapperTools;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class NATTraversalDiscoveryEntryMapper implements RowMapper<NATTraversalDiscoveryEntry> {

    @Override
    public NATTraversalDiscoveryEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        DateTime terminatedAt = rs.getTimestamp("terminated_at") == null ?
                null : new DateTime(rs.getTimestamp("terminated_at"));

        L4AddressData source = rs.getString("source_address") == null
                ? null : L4MapperTools.fieldsToAddressData("source", rs);
        L4AddressData destination = rs.getString("destination_address") == null
                ? null : L4MapperTools.fieldsToAddressData("destination", rs);

        return NATTraversalDiscoveryEntry.create(
                rs.getString("session_key"),
                rs.getString("transport"),
                rs.getString("mapped_addresses"),
                new DateTime(rs.getTimestamp("most_recent_segment_time")),
                new DateTime(rs.getTimestamp("first_seen")),
                terminatedAt,
                source,
                destination
        );
    }

}
