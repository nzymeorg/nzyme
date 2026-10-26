package app.nzyme.core.ethernet.dhcp.db;

import com.google.common.collect.Lists;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * A row holds an array of responses. Queries that merge rows of several taps with jsonb_agg yield an array of
 * arrays. Both shapes are accepted and responses are merged by identity.
 */
public class DHCPServerResponses {

    private static final ObjectMapper OM = JsonMapper.builder()
            .addModule(new JodaModule())
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public static String toJson(List<DHCPServerResponseEntry> responses) {
        return OM.writeValueAsString(responses);
    }

    public static List<DHCPServerResponseEntry> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Lists.newArrayList();
        }

        JsonNode root = OM.readTree(json);
        List<DHCPServerResponseEntry> flat = Lists.newArrayList();
        collect(root, flat);

        return merge(flat);
    }

    private static void collect(JsonNode node, List<DHCPServerResponseEntry> into) {
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
            into.add(OM.treeToValue(node, DHCPServerResponseEntry.class));
        }
    }

    public static List<DHCPServerResponseEntry> merge(List<DHCPServerResponseEntry> responses) {
        Map<String, DHCPServerResponseEntry> merged = new LinkedHashMap<>();

        for (DHCPServerResponseEntry r : responses) {
            DHCPServerResponseEntry existing = merged.get(r.identity());
            if (existing == null) {
                merged.put(r.identity(), r);
                continue;
            }

            merged.put(r.identity(), DHCPServerResponseEntry.create(
                    existing.messageType(),
                    existing.serverMac(),
                    existing.serverAddress(),
                    existing.serverIdentifier() != null ? existing.serverIdentifier() : r.serverIdentifier(),
                    existing.offeredAddress(),
                    existing.subnetMask() != null ? existing.subnetMask() : r.subnetMask(),
                    existing.routers().isEmpty() ? r.routers() : existing.routers(),
                    existing.dnsServers().isEmpty() ? r.dnsServers() : existing.dnsServers(),
                    existing.domainName() != null ? existing.domainName() : r.domainName(),
                    existing.leaseTimeSeconds() != null ? existing.leaseTimeSeconds() : r.leaseTimeSeconds(),
                    Math.max(existing.count(), r.count()),
                    existing.firstSeen().isBefore(r.firstSeen()) ? existing.firstSeen() : r.firstSeen(),
                    existing.lastSeen().isAfter(r.lastSeen()) ? existing.lastSeen() : r.lastSeen()
            ));
        }

        return Lists.newArrayList(merged.values());
    }

    public static List<DHCPServerResponseEntry> listFromJson(String json) {
        return OM.readValue(json, new TypeReference<>() {});
    }

}
