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

public class STUNNegotiationEntryMapper implements RowMapper<STUNNegotiationEntry> {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public STUNNegotiationEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        L4AddressData source = rs.getString("source_address") == null
                ? null : L4MapperTools.fieldsToAddressData("source", rs);
        L4AddressData destination = rs.getString("destination_address") == null
                ? null : L4MapperTools.fieldsToAddressData("destination", rs);

        return STUNNegotiationEntry.create(
                rs.getString("negotiation_key"),
                rs.getString("negotiation_key_sha256"),
                rs.getBoolean("is_active"),
                rs.getString("transport"),
                rs.getBoolean("successful"),
                rs.getBoolean("is_turn"),
                rs.getLong("bytes_exchanged"),
                source,
                destination,
                parseAddressArray(rs, "mapped_addresses"),
                parseAddressArray(rs, "peer_addresses"),
                parseAddressArray(rs, "relayed_addresses"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_activity"))
        );
    }

    private List<L4AddressData> parseAddressArray(ResultSet rs, String fieldName) throws SQLException {
        List<L4AddressData> addresses;
        String mappedAddressesJson = rs.getString(fieldName);
        if (mappedAddressesJson == null || mappedAddressesJson.isEmpty()) {
            addresses = Collections.emptyList();
        } else {
            try {
                addresses = OM.readValue(
                        mappedAddressesJson, new TypeReference<>() {
                        });
            } catch (Exception e) {
                throw new SQLException("Could not parse ["+ fieldName + "] addresses JSON.", e);
            }
        }

        return addresses;
    }

}