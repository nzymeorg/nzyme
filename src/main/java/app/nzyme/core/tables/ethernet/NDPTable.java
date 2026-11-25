package app.nzyme.core.tables.ethernet;

import app.nzyme.core.assets.AssetManager;
import app.nzyme.core.assets.profile.AssetProfileBatch;
import app.nzyme.core.ethernet.ndp.NDP;
import app.nzyme.core.util.Tools;
import org.jdbi.v3.core.Handle;
import app.nzyme.core.rest.resources.taps.reports.tables.ndp.NdpMessageReport;
import app.nzyme.core.rest.resources.taps.reports.tables.ndp.NdpReport;
import app.nzyme.core.rest.resources.taps.reports.tables.ndp.NdpRouterAdvertisementReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public class NDPTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(NDPTable.class);

    // What a tap reports at most.
    private static final int MAX_MESSAGES = 20_000;
    private static final int MAX_ROUTER_ADVERTISEMENTS = 1_000;
    private static final int MAX_LIST_ENTRIES = 32;

    private static final List<String> PREFERENCES = List.of("HIGH", "MEDIUM", "LOW");

    private final Timer totalReportTimer;

    private final TablesService tablesService;
    private final ObjectMapper om = new ObjectMapper();

    public NDPTable(TablesService tablesService) {
        this.tablesService = tablesService;

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.NDP_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, NdpReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            tablesService.getNzyme().getDatabase().useHandle(handle -> {
                Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);
                if (tap.isEmpty()) {
                    throw new RuntimeException("Reporting tap [" + tapUuid + "] not found. Not processing report.");
                }

                PreparedBatch messages = handle.prepareBatch("INSERT INTO ndp_messages(tap_uuid, message_type, " +
                        "source_mac, destination_mac, source_address, destination_address, hop_limit, " +
                        "target_address, source_link_layer_address, target_link_layer_address, router, solicited, " +
                        "overrides, packet_count, first_seen, last_seen, created_at) VALUES(:tap_uuid, " +
                        ":message_type, :source_mac, :destination_mac, :source_address::inet, " +
                        ":destination_address::inet, :hop_limit, :target_address::inet, " +
                        ":source_link_layer_address, :target_link_layer_address, :router, :solicited, :overrides, " +
                        ":packet_count, :first_seen, :last_seen, NOW())");

                int count = 0;
                for (NdpMessageReport m : report.messages()) {
                    if (count >= MAX_MESSAGES) {
                        LOG.warn("NDP report of tap [{}] has more than {} messages. Skipping the rest.",
                                tapUuid, MAX_MESSAGES);
                        break;
                    }

                    if (!NDP.MESSAGE_TYPES.contains(m.messageType())) {
                        continue;
                    }

                    messages.bind("tap_uuid", tapUuid)
                            .bind("message_type", m.messageType())
                            .bind("source_mac", m.sourceMac())
                            .bind("destination_mac", m.destinationMac())
                            .bind("source_address", m.sourceAddress())
                            .bind("destination_address", m.destinationAddress())
                            .bind("hop_limit", m.hopLimit())
                            .bind("target_address", m.targetAddress())
                            .bind("source_link_layer_address", m.sourceLinkLayerAddress())
                            .bind("target_link_layer_address", m.targetLinkLayerAddress())
                            .bind("router", m.router())
                            .bind("solicited", m.solicited())
                            .bind("overrides", m.overrides())
                            .bind("packet_count", m.packetCount())
                            .bind("first_seen", m.firstSeen())
                            .bind("last_seen", m.lastSeen())
                            .add();
                    count++;
                }

                PreparedBatch advertisements = handle.prepareBatch("INSERT INTO ndp_router_advertisements(" +
                        "tap_uuid, source_mac, destination_mac, source_address, destination_address, hop_limit, " +
                        "source_link_layer_address, current_hop_limit, managed, other_configuration, preference, " +
                        "router_lifetime, reachable_time, retransmit_timer, mtu, prefixes, routes, dns_servers, " +
                        "dns_servers_lifetime, dns_search_list, packet_count, first_seen, last_seen, created_at) " +
                        "VALUES(:tap_uuid, :source_mac, :destination_mac, :source_address::inet, " +
                        ":destination_address::inet, :hop_limit, :source_link_layer_address, :current_hop_limit, " +
                        ":managed, :other_configuration, :preference, :router_lifetime, :reachable_time, " +
                        ":retransmit_timer, :mtu, :prefixes::jsonb, :routes::jsonb, :dns_servers::jsonb, " +
                        ":dns_servers_lifetime, :dns_search_list::jsonb, :packet_count, :first_seen, :last_seen, " +
                        "NOW())");

                count = 0;
                for (NdpRouterAdvertisementReport ra : report.routerAdvertisements()) {
                    if (count >= MAX_ROUTER_ADVERTISEMENTS) {
                        LOG.warn("NDP report of tap [{}] has more than {} router advertisements. Skipping the rest.",
                                tapUuid, MAX_ROUTER_ADVERTISEMENTS);
                        break;
                    }

                    advertisements.bind("tap_uuid", tapUuid)
                            .bind("source_mac", ra.sourceMac())
                            .bind("destination_mac", ra.destinationMac())
                            .bind("source_address", ra.sourceAddress())
                            .bind("destination_address", ra.destinationAddress())
                            .bind("hop_limit", ra.hopLimit())
                            .bind("source_link_layer_address", ra.sourceLinkLayerAddress())
                            .bind("current_hop_limit", ra.currentHopLimit())
                            .bind("managed", ra.managed())
                            .bind("other_configuration", ra.otherConfiguration())
                            .bind("preference", PREFERENCES.contains(ra.preference()) ? ra.preference() : "MEDIUM")
                            .bind("router_lifetime", ra.routerLifetime())
                            .bind("reachable_time", ra.reachableTime())
                            .bind("retransmit_timer", ra.retransmitTimer())
                            .bind("mtu", ra.mtu())
                            .bind("prefixes", om.writeValueAsString(limited(ra.prefixes())))
                            .bind("routes", om.writeValueAsString(limited(ra.routes())))
                            .bind("dns_servers", om.writeValueAsString(limited(ra.dnsServers())))
                            .bind("dns_servers_lifetime", ra.dnsServersLifetime())
                            .bind("dns_search_list", om.writeValueAsString(limited(ra.dnsSearchList())))
                            .bind("packet_count", ra.packetCount())
                            .bind("first_seen", ra.firstSeen())
                            .bind("last_seen", ra.lastSeen())
                            .add();
                    count++;
                }

                messages.execute();
                advertisements.execute();

                registerAssets(handle, tap.get(), report);
            });
        } catch (Exception e) {
            LOG.error("Could not write NDP report of tap [{}].", tapUuid, e);
        }
    }

    // A message that arrived with another hop limit than 255 was routed: its MAC address is the router's.
    private void registerAssets(Handle handle, Tap tap, NdpReport report) {
        Map<String, DateTime[]> senders = new TreeMap<>();
        AssetProfileBatch profile = new AssetProfileBatch(tap);

        int count = 0;
        for (NdpMessageReport m : report.messages()) {
            if (count++ >= MAX_MESSAGES) {
                break;
            }

            if (NDP.MESSAGE_TYPES.contains(m.messageType())) {
                sender(senders, profile, m.sourceMac(), m.hopLimit(), m.packetCount(), m.firstSeen(), m.lastSeen());
            }
        }

        count = 0;
        for (NdpRouterAdvertisementReport ra : report.routerAdvertisements()) {
            if (count++ >= MAX_ROUTER_ADVERTISEMENTS) {
                break;
            }

            sender(senders, profile, ra.sourceMac(), ra.hopLimit(), ra.packetCount(), ra.firstSeen(), ra.lastSeen());
        }

        for (Map.Entry<String, DateTime[]> sender : senders.entrySet()) {
            tablesService.getNzyme().getAssetsManager().registerSeen(
                    handle, tap, sender.getKey(), sender.getValue()[0], sender.getValue()[1], AssetManager.SeenBy.NDP);
        }

        profile.flush(handle);
    }

    private static void sender(Map<String, DateTime[]> senders,
                               AssetProfileBatch profile,
                               String mac,
                               int hopLimit,
                               long packetCount,
                               DateTime firstSeen,
                               DateTime lastSeen) {
        if (hopLimit != 255 || !AssetProfileBatch.isUsableMac(mac) || !Tools.isValidMacAddress(mac)
                || firstSeen == null || lastSeen == null) {
            return;
        }

        profile.activity(mac, lastSeen, AssetProfileBatch.ActivityKind.NDP, Math.max(1, packetCount), 0, 0);
        profile.tap(mac, lastSeen, Math.max(1, packetCount));

        DateTime[] seen = senders.get(mac);
        if (seen == null) {
            senders.put(mac, new DateTime[]{firstSeen, lastSeen});
        } else {
            if (firstSeen.isBefore(seen[0])) {
                seen[0] = firstSeen;
            }
            if (lastSeen.isAfter(seen[1])) {
                seen[1] = lastSeen;
            }
        }
    }

    private static <T> List<T> limited(List<T> list) {
        if (list == null) {
            return List.of();
        }

        return list.size() > MAX_LIST_ENTRIES ? list.subList(0, MAX_LIST_ENTRIES) : list;
    }

    @Override
    public void retentionClean() {
        // NOOP. Database cleaned by category/tenant independently.
    }

}
