package app.nzyme.core.dot11.db;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SecuritySettingsParser {

    private static final ObjectMapper om = new ObjectMapper();

    public static List<Dot11SecuritySuiteJson> parseSecuritySettings(java.sql.Array secArr) throws SQLException {
        // Keyed by suite: the same suite is stored with and without the protection flags around a tap upgrade.
        Map<List<String>, Dot11SecuritySuiteJson> securitySuites = Maps.newLinkedHashMap();

        if (secArr == null) {
            return Lists.newArrayList();
        }

        Object[] elems = (Object[]) secArr.getArray();
        for (Object elem : elems) {
            if (elem == null) continue;

            final String json;
            if (elem instanceof org.postgresql.util.PGobject pg) {
                json = pg.getValue();
            } else {
                json = elem.toString();
            }

            try {
                JsonNode root = om.readTree(json);

                JsonNode suites = root.get("suites");
                if (suites == null || !suites.isObject()) {
                    continue;
                }

                Dot11SecuritySuiteJson suite =
                        Dot11SecuritySuiteJson.builder()
                                .groupCipher(suites.path("group_cipher").asText(null))
                                .pairwiseCiphers(joinTextArray(suites.get("pairwise_ciphers")))
                                .keyManagementModes(joinTextArray(suites.get("key_management_modes")))
                                .pmfMode(root.path("pmf").asText(null))
                                .operatingChannelValidation(booleanOrNull(root.get("operating_channel_validation")))
                                .beaconProtection(booleanOrNull(root.get("beacon_protection")))
                                .build();

                securitySuites.merge(
                        Arrays.asList(suite.groupCipher(), suite.pairwiseCiphers(), suite.keyManagementModes(),
                                suite.pmfMode()),
                        suite,
                        (a, b) -> a.toBuilder()
                                .operatingChannelValidation(
                                        anyTrue(a.operatingChannelValidation(), b.operatingChannelValidation()))
                                .beaconProtection(anyTrue(a.beaconProtection(), b.beaconProtection()))
                                .build()
                );

            } catch (Exception e) {
                throw new RuntimeException("Failed to parse security_settings JSON", e);
            }
        }

        return Lists.newArrayList(securitySuites.values());
    }

    private static Boolean booleanOrNull(JsonNode node) {
        return node == null || !node.isBoolean() ? null : node.asBoolean();
    }

    private static Boolean anyTrue(Boolean a, Boolean b) {
        if (a == null) {
            return b;
        }

        return b == null ? a : a || b;
    }

    private static String joinTextArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return null;
        }

        List<String> values = Lists.newArrayList();
        for (JsonNode n : arrayNode) {
            if (n.isTextual()) {
                values.add(n.asText());
            }
        }

        return values.isEmpty() ? null : Joiner.on(',').join(values);
    }
}