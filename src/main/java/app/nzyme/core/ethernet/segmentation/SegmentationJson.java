package app.nzyme.core.ethernet.segmentation;

import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class SegmentationJson {

    private static final ObjectMapper OM = new ObjectMapper();

    public static PolicyRule parseRule(String json) {
        return OM.readValue(json, PolicyRule.class);
    }

    public static List<PolicyRule> parseRules(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Lists.newArrayList();
        }

        return OM.readValue(json, new TypeReference<>() {});
    }

    public static List<Finding.Target> parseTargets(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Lists.newArrayList();
        }

        return OM.readValue(json, new TypeReference<>() {});
    }

}
