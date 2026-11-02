package app.nzyme.core.ethernet.dhcp.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DHCPFlakyFingerprintAssetMapper implements RowMapper<DHCPFlakyFingerprintAssetEntry> {

    private final ObjectMapper om = JsonMapper.builder().build();

    @Override
    public DHCPFlakyFingerprintAssetEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        List<String> transactionTypes;
        List<String> fingerprints;
        try {
            transactionTypes = om.readValue(rs.getString("transaction_types"), new TypeReference<>() {});
            fingerprints = om.readValue(rs.getString("fingerprints"), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Could not deserialize DHCP flaky fingerprint asset row.", e);
        }

        return DHCPFlakyFingerprintAssetEntry.create(
                rs.getString("client_mac"),
                transactionTypes,
                fingerprints,
                rs.getLong("fingerprint_count"),
                rs.getLong("change_count"),
                rs.getLong("transaction_count"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

}
