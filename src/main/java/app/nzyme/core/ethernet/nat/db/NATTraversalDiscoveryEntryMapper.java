package app.nzyme.core.ethernet.nat.db;

import app.nzyme.core.ethernet.L4MapperTools;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class NATTraversalDiscoveryEntryMapper implements RowMapper<NATTraversalDiscoveryEntry> {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public NATTraversalDiscoveryEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        DateTime terminatedAt = rs.getTimestamp("terminated_at") == null ?
                null : new DateTime(rs.getTimestamp("terminated_at"));

        L4AddressData source = rs.getString("source_address") == null
                ? null : L4MapperTools.fieldsToAddressData("source", rs);
        L4AddressData destination = rs.getString("destination_address") == null
                ? null : L4MapperTools.fieldsToAddressData("destination", rs);

        List<L4AddressData> mappedAddresses;
        String mappedAddressesJson = rs.getString("mapped_addresses");
        if (mappedAddressesJson == null || mappedAddressesJson.isEmpty()) {
            mappedAddresses = Collections.emptyList();
        } else {
            try {
                mappedAddresses = OM.readValue(
                        mappedAddressesJson, new TypeReference<>() {
                        });
            } catch (Exception e) {
                throw new SQLException("Could not parse mapped_addresses JSON.", e);
            }
        }

        return NATTraversalDiscoveryEntry.create(
                rs.getString("session_key"),
                rs.getString("transport"),
                rs.getString("status"),
                new DateTime(rs.getTimestamp("most_recent_segment_time")),
                new DateTime(rs.getTimestamp("first_seen")),
                terminatedAt,
                source,
                destination,
                mappedAddresses
        );
    }

}