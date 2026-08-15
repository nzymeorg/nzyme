package app.nzyme.core.database;

import app.nzyme.core.assets.db.AssetEntryMapper;
import app.nzyme.core.assets.db.AssetHostnameEntryMapper;
import app.nzyme.core.assets.db.AssetIpAddressEntryMapper;
import app.nzyme.core.bluetooth.db.BluetoothDeviceEntryMapper;
import app.nzyme.core.bluetooth.db.BluetoothDeviceSummaryMapper;
import app.nzyme.core.configuration.node.NodeConfiguration;
import app.nzyme.core.context.db.MacAddressContextEntryMapper;
import app.nzyme.core.context.db.MacAddressTransparentContextEntryMapper;
import app.nzyme.core.crypto.database.TLSKeyAndCertificateEntryMapper;
import app.nzyme.core.crypto.database.TLSWildcardKeyAndCertificateEntryMapper;
import app.nzyme.core.database.generic.*;
import app.nzyme.core.detection.alerts.db.DetectionAlertAttributeEntryMapper;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntryMapper;
import app.nzyme.core.detection.alerts.db.DetectionAlertTimelineEntryMapper;
import app.nzyme.core.distributed.database.NodeEntryMapper;
import app.nzyme.core.distributed.database.metrics.GaugeHistogramBucketMapper;
import app.nzyme.core.distributed.database.metrics.TimerSnapshotMapper;
import app.nzyme.core.distributed.messaging.postgres.PostgresMessageEntryMapper;
import app.nzyme.core.distributed.tasksqueue.postgres.PostgresTasksQueueEntryMapper;
import app.nzyme.core.dot11.db.*;
import app.nzyme.core.dot11.db.monitoring.*;
import app.nzyme.core.dot11.db.monitoring.probereq.MonitoredProbeRequestEntryMapper;
import app.nzyme.core.dot11.tracks.db.TrackDetectorConfigMapper;
import app.nzyme.core.ethernet.arp.db.ARPStatisticsBucketMapper;
import app.nzyme.core.ethernet.arp.db.ArpPacketEntryMapper;
import app.nzyme.core.ethernet.arp.db.ArpSenderTargetCountPairMapper;
import app.nzyme.core.ethernet.dhcp.db.DHCPStatisticsBucketMapper;
import app.nzyme.core.ethernet.dhcp.db.DHCPTransactionMapper;
import app.nzyme.core.ethernet.dns.db.*;
import app.nzyme.core.ethernet.l4.db.L4AddressDataMapper;
import app.nzyme.core.ethernet.l4.db.L4NumbersMapper;
import app.nzyme.core.ethernet.l4.db.L4SessionMapper;
import app.nzyme.core.ethernet.l4.db.L4StatisticsBucketMapper;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryEntryMapper;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryHistogramBucketMapper;
import app.nzyme.core.ethernet.nat.db.STUNNegotiationEntryMapper;
import app.nzyme.core.ethernet.rtsp.db.RTSPStreamEntryMapper;
import app.nzyme.core.ethernet.socks.db.SocksTunnelEntryMapper;
import app.nzyme.core.ethernet.ssh.db.SSHSessionEntryMapper;
import app.nzyme.core.ethernet.l4.tcp.db.TcpSessionEntryMapper;
import app.nzyme.core.ethernet.l4.udp.db.UdpConversationEntryMapper;
import app.nzyme.core.ethernet.time.ntp.db.NTPTransactionEntryMapper;
import app.nzyme.core.events.db.EventActionEntryMapper;
import app.nzyme.core.events.db.EventEntryMapper;
import app.nzyme.core.events.db.SubscriptionEntryMapper;
import app.nzyme.core.floorplans.db.TenantLocationEntryMapper;
import app.nzyme.core.floorplans.db.TenantLocationFloorEntryMapper;
import app.nzyme.core.monitoring.GaugeEntryAverageMapper;
import app.nzyme.core.monitoring.TimerEntryAverageMapper;
import app.nzyme.core.monitoring.TimerEntryMapper;
import app.nzyme.core.monitoring.health.db.IndicatorStatusMapper;
import app.nzyme.core.monitors.db.MonitorEntryMapper;
import app.nzyme.core.registry.RegistryEntryMapper;
import app.nzyme.core.security.authentication.db.*;
import app.nzyme.core.security.sessions.db.SessionEntryMapper;
import app.nzyme.core.security.sessions.db.SessionEntryWithUserDetailsMapper;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntryMapper;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResultMapper;
import app.nzyme.core.taps.db.metrics.*;
import app.nzyme.core.timelines.db.TimelineEventEntryMapper;
import app.nzyme.core.uav.db.UavEntryMapper;
import app.nzyme.core.uav.db.UavTimelineEntryMapper;
import app.nzyme.core.uav.db.UavTypeEntryMapper;
import app.nzyme.core.uav.db.UavVectorEntryMapper;
import app.nzyme.plugin.Database;
import app.nzyme.core.crypto.database.PGPKeyFingerprintMapper;
import app.nzyme.core.taps.db.*;
import com.google.common.collect.Lists;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.ui.LoggerUIService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.ConnectionException;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.jodatime2.JodaTimePlugin;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.joda.time.DateTime;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class DatabaseImpl implements Database {

    private static final Logger LOG = LogManager.getLogger(DatabaseImpl.class);

    private final NodeConfiguration configuration;

    private Jdbi jdbi;

    public DatabaseImpl(NodeConfiguration configuration) {
        this.configuration = configuration;
    }

    public void initialize() throws LiquibaseException {
        // TODO use reflection here at some point.
        this.jdbi = Jdbi.create("jdbc:" + configuration.databasePath())
                .installPlugin(new PostgresPlugin())
                .installPlugin(new JodaTimePlugin())
                .registerRowMapper(new TapMapper())
                .registerRowMapper(new BusMapper())
                .registerRowMapper(new ChannelMapper())
                .registerRowMapper(new CaptureMapper())
                .registerRowMapper(new TapMetricsGaugeMapper())
                .registerRowMapper(new TapMetricsAggregationMapper())
                .registerRowMapper(new DNSStatisticsBucketMapper())
                .registerRowMapper(new DNSTrafficSummaryMapper())
                .registerRowMapper(new DNSPairSummaryMapper())
                .registerRowMapper(new PGPKeyFingerprintMapper())
                .registerRowMapper(new NodeEntryMapper())
                .registerRowMapper(new GaugeHistogramBucketMapper())
                .registerRowMapper(new TimerSnapshotMapper())
                .registerRowMapper(new IndicatorStatusMapper())
                .registerRowMapper(new TLSKeyAndCertificateEntryMapper())
                .registerRowMapper(new TLSWildcardKeyAndCertificateEntryMapper())
                .registerRowMapper(new PostgresMessageEntryMapper())
                .registerRowMapper(new PostgresTasksQueueEntryMapper())
                .registerRowMapper(new OrganizationEntryMapper())
                .registerRowMapper(new TenantEntryMapper())
                .registerRowMapper(new UserEntryMapper())
                .registerRowMapper(new TapPermissionEntryMapper())
                .registerRowMapper(new SessionEntryMapper())
                .registerRowMapper(new SessionEntryWithUserDetailsMapper())
                .registerRowMapper(new RegistryEntryMapper())
                .registerRowMapper(new EventEntryMapper())
                .registerRowMapper(new EventActionEntryMapper())
                .registerRowMapper(new SubscriptionEntryMapper())
                .registerRowMapper(new BSSIDSummaryMapper())
                .registerRowMapper(new SSIDChannelDetailsMapper())
                .registerRowMapper(new BSSIDAndSSIDCountHistogramEntryMapper())
                .registerRowMapper(new SSIDDetailsMapper())
                .registerRowMapper(new Dot11AdvertisementHistogramEntryMapper())
                .registerRowMapper(new SignalTrackHistogramEntryMapper())
                .registerRowMapper(new ActiveChannelMapper())
                .registerRowMapper(new ConnectedClientDetailsMapper())
                .registerRowMapper(new DisconnectedClientDetailsMapper())
                .registerRowMapper(new ClientHistogramEntryMapper())
                .registerRowMapper(new ClientActivityHistogramEntryMapper())
                .registerRowMapper(new FirstLastSeenTupleMapper())
                .registerRowMapper(new MonitoredSSIDMapper())
                .registerRowMapper(new MonitoredBSSIDMapper())
                .registerRowMapper(new MonitoredFingerprintMapper())
                .registerRowMapper(new MonitoredChannelMapper())
                .registerRowMapper(new MonitoredSecuritySuiteMapper())
                .registerRowMapper(new BSSIDWithTapMapper())
                .registerRowMapper(new DetectionAlertEntryMapper())
                .registerRowMapper(new DetectionAlertAttributeEntryMapper())
                .registerRowMapper(new DetectionAlertTimelineEntryMapper())
                .registerRowMapper(new TrackDetectorConfigMapper())
                .registerRowMapper(new CustomBanditDescriptionMapper())
                .registerRowMapper(new DiscoHistogramEntryMapper())
                .registerRowMapper(new CustomBanditDescriptionMapper())
                .registerRowMapper(new BSSIDFrameCountMapper())
                .registerRowMapper(new BSSIDPairFrameCountMapper())
                .registerRowMapper(new MacAddressContextEntryMapper())
                .registerRowMapper(new TapBasedSignalStrengthResultMapper())
                .registerRowMapper(new RestrictedSSIDSubstringMapper())
                .registerRowMapper(new ClientSignalStrengthResultMapper())
                .registerRowMapper(new TenantLocationEntryMapper())
                .registerRowMapper(new TenantLocationFloorEntryMapper())
                .registerRowMapper(new TapBasedSignalStrengthResultHistogramEntryMapper())
                .registerRowMapper(new TapMetricsTimerHistogramAggregationMapper())
                .registerRowMapper(new Dot11FrequencyAndChannelWidthEntryMapper())
                .registerRowMapper(new TcpSessionEntryMapper())
                .registerRowMapper(new DNSEntropyLogEntryMapper())
                .registerRowMapper(new DNSLogEntryMapper())
                .registerRowMapper(new SocksTunnelEntryMapper())
                .registerRowMapper(new SSHSessionEntryMapper())
                .registerRowMapper(new DateTimeNumberAggregationResultMapper())
                .registerRowMapper(new TimerEntryMapper())
                .registerRowMapper(new BluetoothDeviceEntryMapper())
                .registerRowMapper(new BluetoothDeviceSummaryMapper())
                .registerRowMapper(new GenericIntegerHistogramEntryMapper())
                .registerRowMapper(new MonitoredProbeRequestEntryMapper())
                .registerRowMapper(new MacAddressTransparentContextEntryMapper())
                .registerRowMapper(new SSIDWithOrganizationAndTenantMapper())
                .registerRowMapper(new Dot11KnownNetworkMapper())
                .registerRowMapper(new Dot11KnownClientMapper())
                .registerRowMapper(new UavEntryMapper())
                .registerRowMapper(new UavTimelineEntryMapper())
                .registerRowMapper(new UavTypeEntryMapper())
                .registerRowMapper(new UavVectorEntryMapper())
                .registerRowMapper(new DHCPTransactionMapper())
                .registerRowMapper(new AssetEntryMapper())
                .registerRowMapper(new AssetHostnameEntryMapper())
                .registerRowMapper(new AssetIpAddressEntryMapper())
                .registerRowMapper(new UdpConversationEntryMapper())
                .registerRowMapper(new L4SessionMapper())
                .registerRowMapper(new ArpPacketEntryMapper())
                .registerRowMapper(new ARPStatisticsBucketMapper())
                .registerRowMapper(new ArpSenderTargetCountPairMapper())
                .registerRowMapper(new LatLonResultMapper())
                .registerRowMapper(new TimerEntryAverageMapper())
                .registerRowMapper(new GaugeEntryAverageMapper())
                .registerRowMapper(new TapMetricsGaugeAggregationMapper())
                .registerRowMapper(new TapMetricsTimerAggregationMapper())
                .registerRowMapper(new EngagementLogEntryMapper())
                .registerRowMapper(new L4StatisticsBucketMapper())
                .registerRowMapper(new L4NumbersMapper())
                .registerRowMapper(new NumberNumberAggregationResultMapper())
                .registerRowMapper(new StringNumberAggregationResultMapper())
                .registerRowMapper(new NumberNumberNumberAggregationResultMapper())
                .registerRowMapper(new StringNumberNumberAggregationResultMapper())
                .registerRowMapper(new L4AddressDataAddressNumberNumberAggregationResultMapper())
                .registerRowMapper(new DHCPStatisticsBucketMapper())
                .registerRowMapper(new NTPTransactionEntryMapper())
                .registerRowMapper(new StringDoubleDoubleNumberAggregationResultMapper())
                .registerRowMapper(new StringStringNumberAggregationResultMapper())
                .registerRowMapper(new L4AddressDataMapper())
                .registerRowMapper(new MonitorEntryMapper())
                .registerRowMapper(new TimelineEventEntryMapper())
                .registerRowMapper(new RTSPStreamEntryMapper())
                .registerRowMapper(new NATTraversalDiscoveryEntryMapper())
                .registerRowMapper(new NATTraversalDiscoveryHistogramBucketMapper())
                .registerRowMapper(new STUNNegotiationEntryMapper());

        if (configuration.slowQueryLogThreshold().isPresent()) {
            LOG.info("Slow query log enabled with threshold <{}ms>.", configuration.slowQueryLogThreshold().get());

            this.jdbi.setSqlLogger(new SqlLogger() {
                @Override
                public void logAfterExecution(StatementContext context) {
                    if (context.getElapsedTime(ChronoUnit.MILLIS) > configuration.slowQueryLogThreshold().get()) {
                        LOG.info("Slow query: <{}ms> [{}]",
                                context.getElapsedTime(ChronoUnit.MILLIS), context.getParsedSql().getSql());
                    }
                }
            });
        }

        // Try to establish connection, retry if connection fails.
        JdbcConnection connection;
        while (true) {
            try {
                connection = new JdbcConnection(jdbi.open().getConnection());
                break;
            } catch (ConnectionException e) {
                LOG.warn("Could not connect to PostgreSQL. Retrying.", e);

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        Liquibase liquibase = null;
        try {
            liquibase.database.Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(connection);
            liquibase = new liquibase.Liquibase("db/migrations.xml", new ClassLoaderResourceAccessor(), database);

            routeLiquibaseLogging(liquibase);

            if (!liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression()).isEmpty()) {
                throw new RuntimeException("There are pending database changesets. Please run migrations.");
            }
        } finally {
            if (liquibase != null) {
                liquibase.close();
            }
            if (!connection.isClosed()) {
                connection.close();
            }
        }
    }

    public void migrate() throws LiquibaseException {
        Jdbi migrationJdbi = Jdbi.create("jdbc:" + configuration.databasePath())
                .installPlugin(new PostgresPlugin());

        JdbcConnection migrationConnection;

        // Try to establish connection, retry if connection fails.
        while (true) {
            try {
                migrationConnection = new JdbcConnection(migrationJdbi.open().getConnection());
                break;
            } catch (ConnectionException e) {
                LOG.warn("Could not connect to PostgreSQL. Retrying.", e);

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        Liquibase liquibase = null;
        try {
            LOG.info("Running database migrations.");
            liquibase.database.Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(migrationConnection);
            liquibase = new liquibase.Liquibase(
                    "db/migrations.xml", new ClassLoaderResourceAccessor(), database
            );

            routeLiquibaseLogging(liquibase);

            liquibase.update(new Contexts(), new LabelExpression());
            LOG.info("All database migrations complete.");
        } finally {
            if (liquibase != null) {
                liquibase.close();
            }
            if (!migrationConnection.isClosed()) {
                migrationConnection.close();
            }
        }
    }

    public long getTotalSize() {
        return withHandle(handle ->
                handle.createQuery("SELECT pg_database_size(current_database())")
                .mapTo(Long.class)
                .one());
    }

    public long getTableSize(String tableName) {
        return withHandle(handle ->
                handle.createQuery("SELECT pg_total_relation_size(:table)")
                        .bind("table", tableName)
                        .mapTo(Long.class)
                        .one());
    }

    public List<DataTableInformation> getTablesOfDataCategory(DataCategory category) {
        List<DataTableInformation> tables = Lists.newArrayList();

        switch (category) {
            case DOT11 -> {
                tables.add(new DataTableInformation(
                        "dot11_bssids",
                        "SELECT COUNT(*) FROM dot11_bssids WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM dot11_bssids WHERE created_at < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "dot11_bssid_clients",
                        "SELECT COUNT(*) FROM dot11_bssid_clients LEFT JOIN dot11_bssids ON dot11_bssids.id = dot11_bssid_clients.bssid_id WHERE dot11_bssids.tap_uuid IN (<taps>)",
                        null
                ));

                tables.add(new DataTableInformation(
                        "dot11_ssids",
                        "SELECT COUNT(*) FROM dot11_ssids WHERE tap_uuid IN (<taps>)",
                        null
                ));

                tables.add(new DataTableInformation(
                        "dot11_channel_histograms",
                        "SELECT COUNT(*) FROM dot11_channel_histograms LEFT JOIN dot11_ssids ON dot11_ssids.id = dot11_channel_histograms.ssid_id WHERE dot11_ssids.tap_uuid IN (<taps>)",
                        null
                ));

                tables.add(new DataTableInformation(
                        "dot11_channels",
                        "SELECT COUNT(*) FROM dot11_channels LEFT JOIN dot11_ssids ON dot11_ssids.id = dot11_channels.ssid_id WHERE dot11_ssids.tap_uuid IN (<taps>)",
                        null
                ));

                tables.add(new DataTableInformation(
                        "dot11_disco_activity",
                        "SELECT COUNT(*) FROM dot11_disco_activity WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM dot11_disco_activity WHERE created_at < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "dot11_disco_activity_receivers",
                        "SELECT COUNT(*) FROM dot11_disco_activity_receivers LEFT JOIN dot11_disco_activity ON dot11_disco_activity.id = dot11_disco_activity_receivers.disco_activity_id WHERE dot11_disco_activity.tap_uuid IN (<taps>)",
                        null
                ));

                tables.add(new DataTableInformation(
                        "dot11_clients",
                        "SELECT COUNT(*) FROM dot11_clients WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM dot11_clients WHERE created_at < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "dot11_client_probereq_ssids",
                        "SELECT COUNT(*) FROM dot11_client_probereq_ssids WHERE tap_uuid IN (<taps>)",
                        null
                ));
            }
            case BLUETOOTH -> {
                tables.add(new DataTableInformation(
                        "bluetooth_devices",
                        "SELECT COUNT(*) FROM bluetooth_devices WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM bluetooth_devices WHERE last_seen < :since AND tap_uuid IN (<taps>)"
                ));
            }
            case ETHERNET_L4 -> {
                tables.add(new DataTableInformation(
                        "arp_packets",
                        "SELECT COUNT(*) FROM arp_packets WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM arp_packets WHERE timestamp < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "dhcp_transactions",
                        "SELECT COUNT(*) FROM dhcp_transactions WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM dhcp_transactions WHERE latest_packet < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "l4_sessions",
                        "SELECT COUNT(*) FROM l4_sessions WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM l4_sessions WHERE most_recent_segment_time < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "l4_statistics",
                        "SELECT COUNT(*) FROM l4_statistics WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM l4_statistics WHERE timestamp < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "ssh_sessions",
                        "SELECT COUNT(*) FROM ssh_sessions WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM ssh_sessions WHERE most_recent_segment_time < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "socks_tunnels",
                        "SELECT COUNT(*) FROM socks_tunnels WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM socks_tunnels WHERE most_recent_segment_time < :since AND tap_uuid IN (<taps>)"
                ));
                tables.add(new DataTableInformation(
                        "ntp_transactions",
                        "SELECT COUNT(*) FROM ntp_transactions WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM ntp_transactions WHERE (timestamp_client_tap_receive < :since OR timestamp_server_tap_receive < :since) AND tap_uuid IN (<taps>)"
                ));
            }
            case ETHERNET_DNS -> {
                tables.add(new DataTableInformation(
                        "dns_log",
                        "SELECT COUNT(*) FROM dns_log WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM dns_log WHERE timestamp < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "dns_entropy_log",
                        "SELECT COUNT(*) FROM dns_entropy_log WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM dns_entropy_log WHERE timestamp < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "dns_pairs",
                        "SELECT COUNT(*) FROM dns_pairs WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM dns_pairs WHERE created_at < :since AND tap_uuid IN (<taps>)"
                ));

                tables.add(new DataTableInformation(
                        "dns_statistics",
                        "SELECT COUNT(*) FROM dns_statistics WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM dns_statistics WHERE created_at < :since AND tap_uuid IN (<taps>)"
                ));
            }
            case UAV -> {
                tables.add(new DataTableInformation(
                        "uavs",
                        "SELECT COUNT(*) FROM uavs WHERE tap_uuid IN (<taps>)",
                        "DELETE FROM uavs WHERE last_seen < :since AND tap_uuid IN (<taps>)"
                ));
            }
            case TIMELINE_EVENTS_DOT11 -> {
                tables.add(new DataTableInformation(
                        "dot11_timeline_events",
                        "SELECT COUNT(*) FROM dot11_timeline_events e WHERE (e.organization_id, e.tenant_id) IN (SELECT t.organization_id, t.tenant_id FROM taps t WHERE t.uuid IN (<taps>))",
                        "DELETE FROM dot11_timeline_events WHERE (organization_id, tenant_id) IN (SELECT t.organization_id, t.tenant_id FROM taps t WHERE t.uuid IN (<taps>)) AND timestamp < :since"
                ));
            }
        }

        return tables;
    }

    @Override
    public DateTime getDatabaseClock() {
        Timestamp now = withHandle(handle ->
                handle.createQuery("SELECT NOW()")
                        .mapTo(Timestamp.class)
                        .one()
        );

        return new DateTime(now);
    }

    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
        return jdbi.withHandle(callback);
    }

    public <R, X extends Exception> R inTransaction(HandleCallback<R, X> callback) throws X {
        return jdbi.inTransaction(callback);
    }

    public <X extends Exception> void useHandle(final HandleConsumer<X> callback) throws X {
        jdbi.useHandle(callback);
    }

    public <X extends Exception> void useTransaction(final HandleConsumer<X> callback) throws X {
        jdbi.useTransaction(callback);
    }

    private void routeLiquibaseLogging(Liquibase liquibase) {
        try {
            liquibase.setShowSummaryOutput(UpdateSummaryOutputEnum.LOG);
            Scope.enter(Map.of(Scope.Attr.ui.name(), new NullUIService()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class NullUIService extends LoggerUIService {
        @Override
        public void sendMessage(String message) {
            LOG.info(message);
        }

        @Override
        public void sendErrorMessage(String message, Throwable exception) {
            LOG.error(message, exception);
        }
    }


}
