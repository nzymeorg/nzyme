package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.rest.resources.taps.reports.tables.nameres.NameResolutionVictimReport;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

import java.util.List;

public class NameResolutionJson {

    private static final ObjectMapper OM = JsonMapper.builder()
            .addModule(new JodaModule())
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public static String toJson(Object value) {
        return OM.writeValueAsString(value);
    }

    public static List<String> parseStrings(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Lists.newArrayList();
        }

        return OM.readValue(json, new TypeReference<>() {});
    }

    // Accepts one array of victims or, from jsonb_agg over several rows, an array of such arrays.
    public static List<NameResolutionVictimReport> parseVictims(@Nullable String json) {
        List<NameResolutionVictimReport> result = Lists.newArrayList();
        if (json == null || json.isBlank()) {
            return result;
        }

        collect(OM.readTree(json), result);
        return result;
    }

    private static void collect(JsonNode node, List<NameResolutionVictimReport> into) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                collect(child, into);
            }
            return;
        }

        if (node.isObject()) {
            into.add(OM.treeToValue(node, NameResolutionVictimReport.class));
        }
    }

}
