package app.nzyme.core.ethernet.icmp.db;

import com.google.common.collect.Lists;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Helpers for the jsonb string arrays the ICMP detection tables use. */
public class JsonArrays {

    private static final ObjectMapper OM = new ObjectMapper();

    public static List<String> strings(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return Lists.newArrayList();
        }

        try {
            return OM.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new SQLException("Could not parse JSON array.", e);
        }
    }

    public static List<UUID> uuids(String json) throws SQLException {
        List<UUID> result = Lists.newArrayList();
        for (String s : strings(json)) {
            result.add(UUID.fromString(s));
        }
        return result;
    }

    public static String toJson(List<?> values) {
        try {
            return OM.writeValueAsString(values);
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize JSON array.", e);
        }
    }

}
