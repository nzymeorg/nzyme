package app.nzyme.core.tables.ethernet;

import app.nzyme.core.assets.AssetInformation;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.rest.resources.taps.reports.tables.arp.ArpPacketReport;
import app.nzyme.core.rest.resources.taps.reports.tables.arp.ArpPacketsReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ARPTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(DataTable.class);

    private final Timer totalReportTimer;
    private final Timer assetRegistrationTimer;

    private final TablesService tablesService;

    public ARPTable(TablesService tablesService) {
        this.tablesService = tablesService;

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.ARP_TOTAL_REPORT_PROCESSING_TIMER);

        this.assetRegistrationTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.ARP_ASSET_REGISTRATION_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, ArpPacketsReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            tablesService.getNzyme().getDatabase().useHandle(handle -> {
                Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);
                if (tap.isEmpty()) {
                    throw new RuntimeException("Reporting tap [" + tapUuid + "] not found. Not processing report.");
                }

                PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO arp_packets(uuid, tap_uuid, " +
                        "ethernet_source_mac, ethernet_destination_mac, hardware_type, protocol_type, operation, " +
                        "arp_sender_mac, arp_sender_address, arp_target_mac, arp_target_address, size, " +
                        "timestamp, created_at) VALUES(:uuid, :tap_uuid, :ethernet_source_mac, " +
                        ":ethernet_destination_mac, :hardware_type, :protocol_type, :operation, :arp_sender_mac, " +
                        ":arp_sender_address::inet, :arp_target_mac, :arp_target_address::inet, :size, " +
                        ":timestamp, NOW())");

                for (ArpPacketReport packet : report.packets()) {
                    insertBatch
                            .bind("uuid", UUID.randomUUID())
                            .bind("tap_uuid", tapUuid)
                            .bind("ethernet_source_mac", packet.ethernetSourceMac())
                            .bind("ethernet_destination_mac", packet.ethernetDestinationMac())
                            .bind("hardware_type", packet.hardwareType())
                            .bind("protocol_type", packet.protocolType())
                            .bind("operation", packet.operation())
                            .bind("arp_sender_mac", packet.arpSenderMac())
                            .bind("arp_sender_address", packet.arpSenderAddress())
                            .bind("arp_target_mac", packet.arpTargetMac())
                            .bind("arp_target_address", packet.arpTargetAddress())
                            .bind("size", packet.size())
                            .bind("timestamp", packet.timestamp())
                            .add();
                }

                insertBatch.execute();

                try (Timer.Context ignored1 = assetRegistrationTimer.time()) {
                    registerAssets(handle, tap.get(), timestamp, report.packets());
                }
            });
        }
    }

    private void registerAssets(Handle handle, Tap tap, DateTime timestamp, List<ArpPacketReport> conversations) {
        // Aggregate sources to avoid updating same asset over and over again.

        PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO assets(uuid, organization_id, tenant_id, " +
                "mac, first_seen, last_seen, seen_arp, updated_at, created_at) VALUES(:uuid, " +
                ":organization_id, :tenant_id, :mac, :first_seen, :last_seen, true, NOW(), NOW())");
        PreparedBatch updateBatch = handle.prepareBatch("UPDATE assets SET last_seen = :last_seen, " +
                "seen_arp = true, updated_at = NOW() WHERE id = :id");

        Map<String, AssetInformation> assets = Maps.newHashMap();
        for (ArpPacketReport conversation : conversations) {
            if (conversation.ethernetSourceMac() == null || conversation.ethernetSourceMac().equals("00:00:00:00:00:00")) {
                continue;
            }

            AssetInformation existingAsset = assets.get(conversation.ethernetSourceMac());
            if (existingAsset != null) {
                // We have already seen this asset. Update timestamps.
                DateTime firstSeen;
                DateTime lastSeen;

                if (conversation.timestamp().isBefore(existingAsset.firstSeen())) {
                    firstSeen = conversation.timestamp();
                } else {
                    firstSeen = existingAsset.firstSeen();
                }

                if (conversation.timestamp().isAfter(existingAsset.lastSeen())) {
                    lastSeen = conversation.timestamp();
                } else {
                    lastSeen = existingAsset.lastSeen();
                }

                assets.put(
                        conversation.ethernetSourceMac(),
                        AssetInformation.create(conversation.ethernetSourceMac(), firstSeen, lastSeen)
                );
            } else {
                // Not seen before.
                assets.put(conversation.ethernetSourceMac(), AssetInformation.create(
                        conversation.ethernetSourceMac(), conversation.timestamp(), conversation.timestamp()
                ));
            }
        }

        for (AssetInformation assetInfo : assets.values()) {
            try {
                Optional<AssetEntry> asset = tablesService.getNzyme().getAssetsManager()
                        .findAssetByMac(assetInfo.mac(), tap.organizationId(), tap.tenantId());

                if (asset.isPresent()) {
                    // We have an existing asset.
                    updateBatch
                            .bind("id", asset.get().id())
                            .bind("last_seen", assetInfo.lastSeen())
                            .add();
                } else {
                    // First time we are seeing this asset.
                    UUID uuid = UUID.randomUUID();
                    insertBatch
                            .bind("uuid", uuid)
                            .bind("organization_id", tap.organizationId())
                            .bind("tenant_id", tap.tenantId())
                            .bind("mac", assetInfo.mac())
                            .bind("first_seen", assetInfo.firstSeen())
                            .bind("last_seen", assetInfo.lastSeen())
                            .add();

                    // Handle new asset.
                    tablesService.getNzyme().getAssetsManager().onNewAsset(
                            Subsystem.ETHERNET,
                            uuid,
                            assetInfo.mac(),
                            tap.organizationId(),
                            tap.tenantId(),
                            tap.uuid()
                    );
                }
            } catch (Exception e) {
                LOG.error("Could not register asset from ARP packets.", e);
            }
        }

        try {
            updateBatch.execute();
            insertBatch.execute();
        } catch(Exception e) {
            LOG.error("Could not write assets from ARP packets.", e);
        }
    }

    @Override
    public void retentionClean() {
        // NOOP. Remove from plugin APIs if there remains no use. Database cleaned by category/tenant independently.
    }
}