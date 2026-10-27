package app.nzyme.core.ethernet.dns.tunnels;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DnsTunnelJson {

    private static final ObjectMapper OM = new ObjectMapper();

    public static String toJson(Object value) {
        return OM.writeValueAsString(value);
    }

    public static List<String> parseStrings(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Lists.newArrayList();
        }

        return OM.readValue(json, new TypeReference<>() {});
    }

    public static Map<String, Long> parseCounts(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Maps.newLinkedHashMap();
        }

        return OM.readValue(json, new TypeReference<>() {});
    }

    public static List<UUID> parseUuids(@Nullable String json) {
        List<UUID> result = Lists.newArrayList();
        for (String s : parseStrings(json)) {
            result.add(UUID.fromString(s));
        }

        return result;
    }

}
