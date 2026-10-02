package app.nzyme.core.database.generic;

import app.nzyme.core.ethernet.L4MapperTools;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class L4AddressDataAddressNumberNumberAggregationResultMapper
        implements RowMapper<L4AddressDataAddressNumberNumberAggregationResult> {

    @Override
    public L4AddressDataAddressNumberNumberAggregationResult map(ResultSet rs, StatementContext ctx) throws SQLException {
        return L4AddressDataAddressNumberNumberAggregationResult.create(
                L4MapperTools.fieldsToAddressData("key", rs),
                rs.getLong("value1"),
                rs.getLong("value2")
        );
    }

}
