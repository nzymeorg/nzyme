package app.nzyme.core.tables.ethernet;

import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.dhcp.DHCPFingerprint;
import app.nzyme.core.rest.resources.taps.reports.tables.dhcp.DhcpTransactionsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.dhcp.Dhcpv4TransactionReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.sql.Types;
import java.util.*;

public class DHCPTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(DHCPTable.class);

    private final Timer totalReportTimer;
    private final Timer transactionsV4ReportTimer;
    private final Timer assetRegistrationTimer;

    private final TablesService tablesService;

    private final ObjectMapper om;

    public DHCPTable(TablesService tablesService) {
        this.tablesService = tablesService;

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DHCP_TOTAL_REPORT_PROCESSING_TIMER);
        this.transactionsV4ReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DHCP_TRANSACTIONS_FOUR_REPORT_PROCESSING_TIMER);
        this.assetRegistrationTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DHCP_ASSET_REGISTRATION_PROCESSING_TIMER);

        this.om = JsonMapper.builder()
                .addModule(new JodaModule())
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, DhcpTransactionsReport report) {
        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            try (Timer.Context ignored1 = totalReportTimer.time()) {
                Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);
                if (tap.isEmpty()) {
                    throw new RuntimeException("Reporting tap [" + tapUuid + "] not found. Not processing report.");
                }

                try (Timer.Context ignored2 = transactionsV4ReportTimer.time()) {
                    writeV4Transactions(handle, tap.get(), report.four());
                }

                try (Timer.Context ignored3 = assetRegistrationTimer.time()) {
                    registerAssets(handle, tap.get(), report.four());
                }
            }
        });
    }

    private void writeV4Transactions(Handle handle, Tap tap, List<Dhcpv4TransactionReport> txs) {
        for (Dhcpv4TransactionReport tx : txs) {
            String additionalClientMacs;
            String additionalServerMacs;
            String offeredIpAddresses;
            String options;
            String additionalOptions;
            String additionalFingerprints;
            String additionalVendorClasses;
            String timestamps;
            String notes;

            try {
                additionalClientMacs = om.writeValueAsString(tx.additionalClientMacs());
                additionalServerMacs = om.writeValueAsString(tx.additionalServerMacs());
                offeredIpAddresses = om.writeValueAsString(tx.offeredIpAddresses());
                options = om.writeValueAsString(tx.options());
                additionalOptions = om.writeValueAsString(tx.additionalOptions());
                timestamps = om.writeValueAsString(tx.timestamps());
                additionalFingerprints = "[]";
                additionalVendorClasses = om.writeValueAsString(tx.additionalVendorClasses());
                notes = om.writeValueAsString(tx.notes());
            } catch (JacksonException e) {
                LOG.error("Could not serialize DHCP transaction data. Skipping transaction.", e);
                continue;
            }

            Optional<String> fingerprint = new DHCPFingerprint(tx.options(), tx.vendorClass()).generate();

            Optional<Long> existingTx;
            try {
                existingTx = handle.createQuery("SELECT id FROM dhcp_transactions " +
                                "WHERE transaction_id = :transaction_id AND first_packet = :first_packet " +
                                "AND tap_uuid = :tap_uuid AND is_complete = :is_complete")
                        .bind("transaction_id", tx.transactionId())
                        .bind("first_packet", tx.firstPacket())
                        .bind("tap_uuid", tap.uuid())
                        .bind("is_complete", false)
                        .mapTo(Long.class)
                        .findOne();
            } catch (IllegalStateException e) {
                LOG.error("Multiple existing DHCP transactions with transaction ID <{}> found. Skipping.",
                        tx.transactionId());
                continue;
            }

            if (existingTx.isEmpty()) {
                handle.createUpdate("INSERT INTO dhcp_transactions(uuid, tap_uuid, transaction_id, " +
                                "transaction_type, client_mac, additional_client_macs, server_mac, " +
                                "additional_server_macs, offered_ip_addresses, requested_ip_address, options, " +
                                "additional_options, fingerprint, additional_fingerprints, vendor_class, " +
                                "additional_vendor_classes, timestamps, first_packet, latest_packet, notes, " +
                                "is_successful, is_complete, updated_at, created_at) VALUES(:uuid, :tap_uuid, " +
                                ":transaction_id, :transaction_type, :client_mac, :additional_client_macs::jsonb, " +
                                ":server_mac, :additional_server_macs::jsonb, :offered_ip_addresses::jsonb, " +
                                ":requested_ip_address::inet, :options::jsonb, :additional_options::jsonb, " +
                                ":fingerprint, :additional_fingerprints::jsonb, :vendor_class, " +
                                ":additional_vendor_classes::jsonb, :timestamps::jsonb, :first_packet, " +
                                ":latest_packet, :notes::jsonb, :is_successful, :is_complete, NOW(), NOW())")
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tap.uuid())
                        .bind("transaction_id", tx.transactionId())
                        .bind("transaction_type", tx.transactionType())
                        .bind("client_mac", tx.clientMac())
                        .bind("additional_client_macs", additionalClientMacs)
                        .bind("server_mac", tx.serverMac())
                        .bind("additional_server_macs", additionalServerMacs)
                        .bind("offered_ip_addresses", offeredIpAddresses)
                        .bind("requested_ip_address", tx.requestedIpAddress())
                        .bind("options", options)
                        .bind("additional_options", additionalOptions)
                        .bind("fingerprint", fingerprint)
                        .bind("additional_fingerprints", additionalFingerprints)
                        .bind("vendor_class", tx.vendorClass())
                        .bind("additional_vendor_classes", additionalVendorClasses)
                        .bind("timestamps", timestamps)
                        .bind("first_packet", tx.firstPacket())
                        .bind("latest_packet", tx.latestPacket())
                        .bind("notes", notes)
                        .bind("is_successful", tx.successful())
                        .bind("is_complete", tx.complete())
                        .execute();
            } else {
                handle.createUpdate("UPDATE dhcp_transactions " +
                                "SET additional_client_macs = :additional_client_macs::jsonb, " +
                                "server_mac = :server_mac, additional_server_macs = :additional_server_macs::jsonb, " +
                                "offered_ip_addresses = :offered_ip_addresses::jsonb, " +
                                "requested_ip_address = :requested_ip_address::inet, options = :options::jsonb, " +
                                "additional_options = :additional_options::jsonb, " +
                                "fingerprint = :fingerprint, " +
                                "additional_fingerprints = :additional_fingerprints::jsonb, " +
                                "vendor_class = :vendor_class, " +
                                "additional_vendor_classes = :additional_vendor_classes::jsonb, " +
                                "timestamps = :timestamps::jsonb, " +
                                "latest_packet = :latest_packet, notes = :notes::jsonb, is_complete = :is_complete, " +
                                "is_successful = :is_successful, updated_at = NOW() WHERE id = :id")
                        .bind("id", existingTx.get())
                        .bind("additional_client_macs", additionalClientMacs)
                        .bind("server_mac", tx.serverMac())
                        .bind("additional_server_macs", additionalServerMacs)
                        .bind("offered_ip_addresses", offeredIpAddresses)
                        .bind("requested_ip_address", tx.requestedIpAddress())
                        .bind("options", options)
                        .bind("additional_options", additionalOptions)
                        .bind("fingerprint", fingerprint)
                        .bind("additional_fingerprints", additionalFingerprints)
                        .bind("vendor_class", tx.vendorClass())
                        .bind("additional_vendor_classes", additionalVendorClasses)
                        .bind("timestamps", timestamps)
                        .bind("latest_packet", tx.latestPacket())
                        .bind("notes", notes)
                        .bind("is_successful", tx.successful())
                        .bind("is_complete", tx.complete())
                        .execute();
            }
        }
    }

    private void registerAssets(Handle handle, Tap tap, List<Dhcpv4TransactionReport> txs) {
        PreparedBatch updateBatch = handle.prepareBatch("UPDATE assets SET last_seen = :last_seen, " +
                "dhcp_fingerprint_initial = :dhcp_fingerprint_initial, " +
                "dhcp_fingerprint_renew = :dhcp_fingerprint_renew, " +
                "dhcp_fingerprint_reboot = :dhcp_fingerprint_reboot, " +
                "dhcp_fingerprint_rebind = :dhcp_fingerprint_rebind, " +
                "seen_dhcp = true, updated_at = NOW() " +
                "WHERE id = :id");

        Map<String, NewAsset> newAssets = new LinkedHashMap<>();
        boolean haveUpdate = false;

        for (Dhcpv4TransactionReport tx : txs) {
            if (tx.clientMac().equals("00:00:00:00:00:00")) {
                continue;
            }

            Optional<String> fingerprint = new DHCPFingerprint(tx.options(), tx.vendorClass()).generate();

            // Already queued as brand-new in this report? Merge.
            NewAsset pending = newAssets.get(tx.clientMac());
            if (pending != null) {
                pending.updateLastSeen(tx.latestPacket());
                fingerprint.ifPresent(fp -> pending.setFingerprintIfAbsent(tx.transactionType(), fp));
                continue;
            }

            Optional<AssetEntry> asset = tablesService.getNzyme().getAssetsManager()
                    .findAssetByMac(tx.clientMac(), tap.organizationId(), tap.tenantId());

            if (asset.isPresent()) {
                updateBatch
                        .bind("id", asset.get().id())
                        .bind("last_seen", tx.latestPacket())
                        .bind("dhcp_fingerprint_initial", asset.get().dhcpFingerprintInitial())
                        .bind("dhcp_fingerprint_renew", asset.get().dhcpFingerprintRenew())
                        .bind("dhcp_fingerprint_reboot", asset.get().dhcpFingerprintReboot())
                        .bind("dhcp_fingerprint_rebind", asset.get().dhcpFingerprintRebind());

                if (fingerprint.isPresent()) {
                    switch (tx.transactionType()) {
                        case "Initial":
                            if (asset.get().dhcpFingerprintInitial() == null) {
                                updateBatch.bind("dhcp_fingerprint_initial", fingerprint.get());
                            }
                            break;
                        case "Renew":
                            if (asset.get().dhcpFingerprintRenew() == null) {
                                updateBatch.bind("dhcp_fingerprint_renew", fingerprint.get());
                            }
                            break;
                        case "Reboot":
                            if (asset.get().dhcpFingerprintReboot() == null) {
                                updateBatch.bind("dhcp_fingerprint_reboot", fingerprint.get());
                            }
                            break;
                        case "Rebind":
                            if (asset.get().dhcpFingerprintRebind() == null) {
                                updateBatch.bind("dhcp_fingerprint_rebind", fingerprint.get());
                            }
                            break;
                    }
                }

                updateBatch.add();
                haveUpdate = true;
                checkFingerprint(tap, asset.get(), tx);
            } else {
                NewAsset na = new NewAsset(UUID.randomUUID(), tx.clientMac(), tx.firstPacket(), tx.latestPacket());
                fingerprint.ifPresent(fp -> na.setFingerprintIfAbsent(tx.transactionType(), fp));
                newAssets.put(tx.clientMac(), na);
            }
        }

        if (haveUpdate) {
            updateBatch.execute();
        }

        for (NewAsset na : newAssets.values()) {
            boolean inserted = handle.createQuery("INSERT INTO assets(uuid, organization_id, tenant_id, " +
                            "mac, dhcp_fingerprint_initial, dhcp_fingerprint_renew, dhcp_fingerprint_reboot, " +
                            "dhcp_fingerprint_rebind, first_seen, last_seen, seen_dhcp, updated_at, created_at) " +
                            "VALUES(:uuid, :organization_id, :tenant_id, :mac, :dhcp_fingerprint_initial, " +
                            ":dhcp_fingerprint_renew, :dhcp_fingerprint_reboot, :dhcp_fingerprint_rebind, " +
                            ":first_seen, :last_seen, true, NOW(), NOW()) " +
                            "ON CONFLICT (mac, organization_id, tenant_id) DO UPDATE SET " +
                            "last_seen = GREATEST(assets.last_seen, EXCLUDED.last_seen), " +
                            "dhcp_fingerprint_initial = COALESCE(assets.dhcp_fingerprint_initial, EXCLUDED.dhcp_fingerprint_initial), " +
                            "dhcp_fingerprint_renew = COALESCE(assets.dhcp_fingerprint_renew, EXCLUDED.dhcp_fingerprint_renew), " +
                            "dhcp_fingerprint_reboot = COALESCE(assets.dhcp_fingerprint_reboot, EXCLUDED.dhcp_fingerprint_reboot), " +
                            "dhcp_fingerprint_rebind = COALESCE(assets.dhcp_fingerprint_rebind, EXCLUDED.dhcp_fingerprint_rebind), " +
                            "seen_dhcp = true, updated_at = NOW() " +
                            "RETURNING (xmax = 0) AS inserted")
                    .bind("uuid", na.uuid())
                    .bind("organization_id", tap.organizationId())
                    .bind("tenant_id", tap.tenantId())
                    .bind("mac", na.mac())
                    .bind("first_seen", na.firstSeen())
                    .bind("last_seen", na.lastSeen())
                    .bind("dhcp_fingerprint_initial", na.fingerprintInitial())
                    .bind("dhcp_fingerprint_renew", na.fingerprintRenew())
                    .bind("dhcp_fingerprint_reboot", na.fingerprintReboot())
                    .bind("dhcp_fingerprint_rebind", na.fingerprintRebind())
                    .mapTo(Boolean.class)
                    .one();

            if (inserted) {
                tablesService.getNzyme().getAssetsManager().onNewAsset(
                        Subsystem.ETHERNET, na.uuid(), na.mac(),
                        tap.organizationId(), tap.tenantId(), tap.uuid());
            }
        }
    }

    private void checkFingerprint(Tap tap, AssetEntry asset, Dhcpv4TransactionReport tx) {
        Optional<String> fingerprint = new DHCPFingerprint(tx.options(), tx.vendorClass()).generate();

        String existingFingerprint;
        switch (tx.transactionType()) {
            case "Initial":
                existingFingerprint = asset.dhcpFingerprintInitial();
                break;
            case "Renew":
                existingFingerprint = asset.dhcpFingerprintRenew();
                break;
            case "Reboot":
                existingFingerprint = asset.dhcpFingerprintReboot();
                break;
            case "Rebind":
                existingFingerprint = asset.dhcpFingerprintRebind();
                break;
            default:
                return;
        }

        if (fingerprint.isEmpty() || existingFingerprint == null) {
            return;
        }

        if (!fingerprint.get().equals(existingFingerprint)) {
            // Fingerprint differs.
            // TODO only trigger if alert enabled for this asset.
            Map<String, String> attributes = Maps.newHashMap();
            attributes.put("asset_uuid", asset.uuid().toString());
            attributes.put("mac", tx.clientMac());
            attributes.put("transaction_type", tx.transactionType());
            attributes.put("existing_fingerprint", existingFingerprint);
            attributes.put("new_fingerprint", fingerprint.get());

            tablesService.getNzyme().getDetectionAlertService().raiseAlert(
                    tap.organizationId(),
                    tap.tenantId(),
                    null,
                    tap.uuid(),
                    DetectionType.ASSETS_DHCP_FINGERPRINT_NEW,
                    Subsystem.ETHERNET,
                    "MAC address \"" + tx.clientMac() + "\" is presenting new DHCP fingerprint " +
                            " \"" + fingerprint.get() + "\"",
                    attributes,
                    Set.of("asset_uuid", "new_fingerprint")
            );
        }
    }

    @Override
    public void retentionClean() {
        // NOOP. Remove from plugin APIs if there remains no use. Database cleaned by category/tenant independently.
    }

    private static final class NewAsset {

        private final UUID uuid;
        private final String mac;
        private final DateTime firstSeen;
        private DateTime lastSeen;

        private String fingerprintInitial;
        private String fingerprintRenew;
        private String fingerprintReboot;
        private String fingerprintRebind;

        NewAsset(UUID uuid, String mac, DateTime firstSeen, DateTime lastSeen) {
            this.uuid = uuid;
            this.mac = mac;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
        }

        void updateLastSeen(DateTime candidate) {
            if (candidate != null && (lastSeen == null || candidate.isAfter(lastSeen))) {
                lastSeen = candidate;
            }
        }

        void setFingerprintIfAbsent(String transactionType, String fingerprint) {
            switch (transactionType) {
                case "Initial":
                    if (fingerprintInitial == null) fingerprintInitial = fingerprint;
                    break;
                case "Renew":
                    if (fingerprintRenew == null) fingerprintRenew = fingerprint;
                    break;
                case "Reboot":
                    if (fingerprintReboot == null) fingerprintReboot = fingerprint;
                    break;
                case "Rebind":
                    if (fingerprintRebind == null) fingerprintRebind = fingerprint;
                    break;
                default:
            }
        }

        UUID uuid() {
            return uuid;
        }

        String mac() {
            return mac;
        }

        DateTime firstSeen() {
            return firstSeen;
        }

        DateTime lastSeen() {
            return lastSeen;
        }

        String fingerprintInitial() {
            return fingerprintInitial;
        }

        String fingerprintRenew() {
            return fingerprintRenew;
        }

        String fingerprintReboot() {
            return fingerprintReboot;
        }

        String fingerprintRebind() {
            return fingerprintRebind;
        }

    }

}