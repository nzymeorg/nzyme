package app.nzyme.core.database.generic;

import app.nzyme.core.ethernet.L4MapperTools;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class L4AddressPairNumberAggregationResultMapper implements RowMapper<L4AddressPairNumberAggregationResult> {

    @Override
    public L4AddressPairNumberAggregationResult map(ResultSet rs, StatementContext ctx) throws SQLException {
        L4AddressData address1 = rs.getString("value1") == null
                ? null : L4MapperTools.fieldsToAddressData("value1", rs);
        L4AddressData address2 = rs.getString("value2") == null
                ? null : L4MapperTools.fieldsToAddressData("value2", rs);

        return L4AddressPairNumberAggregationResult.create(
                address1, address2, rs.getLong("value3")
        );
    }

}
