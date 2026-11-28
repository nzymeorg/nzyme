package app.nzyme.core.ethernet.portalintegrity;

import jakarta.annotation.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/*
 * The portal integrity probes the taps say they run, from what comes with every tap status. It tells a probe
 * that stopped reporting from one that was taken out of the tap configuration on purpose.
 */
public final class PortalProbeConfigurations {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final int MAX_PROBES_PER_TAP = 100;
    private static final int MAX_INTERVAL_MINUTES = 30 * 24 * 60;

    public record Probe(boolean active, int intervalSeconds, Set<String> controlUrls, boolean ipv6) {}

    // An empty Optional is a tap that did not say which probes it runs.
    private final Map<UUID, Optional<Map<String, Probe>>> taps = new HashMap<>();

    public void put(UUID tapUuid, @Nullable String reportedProbes) {
        taps.put(tapUuid, parse(reportedProbes));
    }

    /*
     * Whether the tap runs a probe that requests the control URL over the IP version. Empty if the tap did not
     * say, which is a tap that has not sent a status since it was upgraded.
     */
    public Optional<Boolean> requests(UUID tapUuid, String probeName, String controlUrl, int ipVersion) {
        Optional<Map<String, Probe>> probes = taps.getOrDefault(tapUuid, Optional.empty());
        if (probes.isEmpty()) {
            return Optional.empty();
        }

        Probe probe = probes.get().get(probeName);

        return Optional.of(probe != null && probe.active() && probe.controlUrls().contains(controlUrl)
                && (ipVersion == 4 || probe.ipv6()));
    }

    public Optional<Integer> intervalSeconds(UUID tapUuid, String probeName) {
        return taps.getOrDefault(tapUuid, Optional.empty())
                .map(probes -> probes.get(probeName))
                .map(Probe::intervalSeconds);
    }

    private static Optional<Map<String, Probe>> parse(@Nullable String reportedProbes) {
        if (reportedProbes == null || reportedProbes.isBlank()) {
            return Optional.empty();
        }

        JsonNode checks;
        try {
            checks = OM.readTree(reportedProbes);
        } catch (JacksonException e) {
            return Optional.empty();
        }

        if (!checks.isObject()) {
            return Optional.empty();
        }

        Map<String, Probe> probes = new HashMap<>();
        for (Map.Entry<String, JsonNode> check : checks.properties()) {
            if (probes.size() >= MAX_PROBES_PER_TAP) {
                break;
            }

            Set<String> controlUrls = new HashSet<>();
            for (JsonNode url : check.getValue().path("control_urls")) {
                if (url.isString() && controlUrls.size() < PortalIntegrity.MAX_CONTROL_URLS_PER_TAP) {
                    controlUrls.add(url.asString());
                }
            }

            long intervalMinutes = Math.max(1, Math.min(check.getValue().path("interval_minutes").asLong(1),
                    MAX_INTERVAL_MINUTES));

            probes.put(check.getKey(), new Probe(
                    check.getValue().path("active").asBoolean(false),
                    (int) intervalMinutes * 60,
                    controlUrls,
                    check.getValue().path("ipv6").asBoolean(false)
            ));
        }

        return Optional.of(probes);
    }

}
