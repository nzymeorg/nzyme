package app.nzyme.core.ethernet.spoofing.db;

import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class SpoofingMapperTools {

    private static final ObjectMapper OM = new ObjectMapper();

    public static List<UUID> parseTaps(@Nullable String json) throws SQLException {
        List<UUID> taps = Lists.newArrayList();
        if (json == null || json.isBlank()) {
            return taps;
        }

        try {
            List<String> parsed = OM.readValue(json, new TypeReference<>() {});
            for (String t : parsed) {
                taps.add(UUID.fromString(t));
            }
        } catch (Exception e) {
            throw new SQLException("Could not parse tap UUIDs.", e);
        }

        return taps;
    }

}
