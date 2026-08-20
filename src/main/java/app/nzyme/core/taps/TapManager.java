package app.nzyme.core.taps;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.ContextService;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.context.db.MacAddressTransparentContextEntry;
import app.nzyme.core.rest.resources.taps.reports.context.TapContextDataReport;
import app.nzyme.core.rest.resources.taps.reports.context.TapContextReport;
import app.nzyme.core.rest.resources.taps.reports.context.TapMacContextReport;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.floorplans.db.TenantLocationFloorEntry;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.resources.taps.reports.*;
import app.nzyme.core.taps.db.EngagementLogEntry;
import app.nzyme.core.taps.db.metrics.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TapManager {

    private static final Logger LOG = LogManager.getLogger(TapManager.class);

    private final NzymeNode nzyme;
    private final ObjectMapper om;

    public TapManager(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.om = new ObjectMapper();

        Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("taps-cleaner-%d")
                        .setDaemon(true)
                        .build()
        ).scheduleAtFixedRate(this::retentionCleanMetrics, 0, 5, TimeUnit.MINUTES);
    }

    public void registerTapHello(HelloReport report, UUID tapUUID) {
        List<Capture> c = findCapturesOfTap(tapUUID, new DateTime(0));
        if (c.isEmpty()) {
            return;
        }

        Map<String, Capture> captures = Maps.newHashMap();
        for (Capture capture : c) {
            captures.put(capture.interfaceName(), capture);
        }

        ObjectMapper om = new ObjectMapper();
        nzyme.getDatabase().useHandle(handle -> {
            // Remove all existing frequencies of all WiFi captures of this tap. We overwrite them.
            PreparedBatch deleteFrequencies = handle.prepareBatch("DELETE FROM tap_captures_frequencies " +
                    "WHERE interface_uuid = :interface_uuid");
            for (Capture capture : captures.values()) {
                if (capture.captureType().equals("WiFi")) {
                    deleteFrequencies.bind("interface_uuid", capture.uuid()).add();
                }
            }

            deleteFrequencies.execute();

            PreparedBatch insertFrequencies = handle.prepareBatch("INSERT INTO " +
                    "tap_captures_frequencies(interface_uuid, frequency, " +
                    "channel_widths) VALUES(:interface_uuid, :frequency, :channel_widths)");

            for (Map.Entry<String, List<WiFiSupportedFrequencyReport>> freq : report.wifiDeviceAssignments().entrySet()) {
                String captureName = freq.getKey();
                List<WiFiSupportedFrequencyReport> frequencies = freq.getValue();

                Capture capture = captures.get(captureName);
                if (capture == null) {
                    LOG.warn("Capture [{}] referenced by supported frequencies report not found.", captureName);
                    continue;
                }

                for (WiFiSupportedFrequencyReport frequency : frequencies) {
                    String widthJson;
                    try {
                        widthJson = om.writeValueAsString(frequency.channelWidths());
                    } catch (JacksonException e) {
                        throw new RuntimeException(e);
                    }

                    insertFrequencies
                            .bind("interface_uuid", capture.uuid())
                            .bind("frequency", frequency.frequency())
                            .bind("channel_widths", widthJson)
                            .add();
                }
            }

            insertFrequencies.execute();

            // Capture/Device cycle times.
            PreparedBatch insertCycleTimes = handle.prepareBatch("UPDATE tap_captures " +
                    "SET cycle_time = :cycle_time WHERE uuid = :capture_uuid");

            for (Map.Entry<String, Integer> device : report.wifiDeviceCycleTimes().entrySet()) {
                String captureName = device.getKey();
                Integer cycleTime = device.getValue();

                Capture capture = captures.get(captureName);
                if (capture == null) {
                    LOG.warn("Capture [{}] referenced by cycle time report not found.", captureName);
                    continue;
                }

                insertCycleTimes
                        .bind("capture_uuid", capture.uuid())
                        .bind("cycle_time", cycleTime)
                        .add();
            }

            insertCycleTimes.execute();
        });
    }

    public void registerTapStatus(StatusReport report, String remoteAddress, UUID tapUUID) {
        LOG.debug("Registering report from tap [{}].", tapUUID);

        String cpuCoresLoadJson;
        String configurationJson;
        try {
            cpuCoresLoadJson = this.om.writeValueAsString(report.systemMetrics().cpuCoresLoad());
            configurationJson = this.om.writeValueAsString(report.configuration());
        } catch (JacksonException e) {
            throw new RuntimeException("Could not serialize tap report JSON.", e);
        }

        nzyme.getDatabase().useHandle( handle -> {
                handle.createUpdate("UPDATE taps SET version = :version, clock = :clock, " +
                                "processed_bytes_total = :processed_bytes_total, " +
                                "processed_bytes_average = :processed_bytes_average, memory_total = :memory_total, " +
                                "memory_free = :memory_free, memory_used = :memory_used, cpu_load = :cpu_load, " +
                                "cpu_cores_load = :cpu_cores_load::jsonb, remote_address = :remote_address, " +
                                "rpi = :rpi, rpi_temperature = :rpi_temperature, " +
                                "configuration = :configuration::jsonb, last_report = NOW() " +
                                "WHERE deleted = false AND uuid = :uuid")
                        .bind("version", report.version())
                        .bind("clock", report.timestamp())
                        .bind("processed_bytes_total", report.processedBytes().total())
                        .bind("processed_bytes_average", report.processedBytes().average())
                        .bind("memory_total", report.systemMetrics().memoryTotal())
                        .bind("memory_free", report.systemMetrics().memoryFree())
                        .bind("memory_used", report.systemMetrics().memoryTotal()-report.systemMetrics().memoryFree())
                        .bind("cpu_load", report.systemMetrics().cpuLoad())
                        .bind("cpu_cores_load", cpuCoresLoadJson)
                        .bind("remote_address", remoteAddress)
                        .bind("rpi", report.rpi())
                        .bind("rpi_temperature", report.systemMetrics().rpiTemperature())
                        .bind("configuration", configurationJson)
                        .bind("uuid", tapUUID)
                        .execute();

            PreparedBatch gaugesBatch = handle.prepareBatch("INSERT INTO tap_metrics_gauges(tap_uuid, " +
                    "metric_name, metric_value, created_at) VALUES(:tap_uuid, :metric_name, :metric_value, :timestamp)");
            PreparedBatch timersBatch = handle.prepareBatch("INSERT INTO tap_metrics_timers(tap_uuid, " +
                    "metric_name, mean, p99, created_at) VALUES(:tap_uuid, :metric_name, :mean, :p99, NOW())");

            // Register captures.
            for (CapturesReport capture : report.captures()) {
                long captureCount = handle.createQuery("SELECT COUNT(*) AS count FROM tap_captures " +
                                "WHERE interface = :interface AND tap_uuid = :tap_uuid")
                        .bind("interface", capture.interfaceName())
                        .bind("tap_uuid", tapUUID)
                        .mapTo(Long.class)
                        .one();

                if (captureCount == 0) {
                    handle.createUpdate("INSERT INTO tap_captures(tap_uuid, uuid, interface, capture_type, " +
                                    "is_running, received, dropped_buffer, dropped_interface, updated_at, created_at) " +
                                    "VALUES(:tap_uuid, :uuid, :interface, :capture_type, :is_running, :received, " +
                                    ":dropped_buffer, :dropped_interface, NOW(), NOW())")
                            .bind("tap_uuid", tapUUID)
                            .bind("uuid", UUID.randomUUID())
                            .bind("interface", capture.interfaceName())
                            .bind("capture_type", capture.captureType())
                            .bind("is_running", capture.isRunning())
                            .bind("received", capture.received())
                            .bind("dropped_buffer", capture.droppedBuffer())
                            .bind("dropped_interface", capture.droppedInterface())
                            .execute();
                } else {
                    handle.createUpdate("UPDATE tap_captures SET capture_type = :capture_type, " +
                                    "is_running = :is_running, received = :received, dropped_buffer = :dropped_buffer, " +
                                    "dropped_interface = :dropped_interface, updated_at = NOW() " +
                                    "WHERE tap_uuid = :tap_uuid AND interface = :interface")
                            .bind("capture_type", capture.captureType())
                            .bind("is_running", capture.isRunning())
                            .bind("received", capture.received())
                            .bind("dropped_buffer", capture.droppedBuffer())
                            .bind("dropped_interface", capture.droppedInterface())
                            .bind("tap_uuid", tapUUID)
                            .bind("interface", capture.interfaceName())
                            .execute();
                }

                // Capture metrics.
                gaugesBatch.bind("tap_uuid", tapUUID)
                        .bind("metric_name", "captures." + capture.interfaceName().toLowerCase() + ".received")
                        .bind("metric_value",  capture.received().doubleValue())
                        .bind("timestamp", report.timestamp())
                        .add();

                gaugesBatch.bind("tap_uuid", tapUUID)
                        .bind("metric_name", "captures." + capture.interfaceName().toLowerCase() + ".dropped_if")
                        .bind("metric_value",  capture.droppedInterface().doubleValue())
                        .bind("timestamp", report.timestamp())
                        .add();

                gaugesBatch.bind("tap_uuid", tapUUID)
                        .bind("metric_name", "captures." + capture.interfaceName().toLowerCase() + ".dropped_buffer")
                        .bind("metric_value",  capture.droppedBuffer().doubleValue())
                        .bind("timestamp", report.timestamp())
                        .add();
            }

            // Register buses.
            for (BusReport bus : report.buses()) {
                long busCount = handle.createQuery("SELECT COUNT(*) AS count FROM tap_buses " +
                                "WHERE tap_uuid = :tap_uuid AND name = :name")
                        .bind("tap_uuid", tapUUID)
                        .bind("name", bus.name())
                        .mapTo(Long.class)
                        .one();

                if (busCount == 0) {
                    handle.createUpdate("INSERT INTO tap_buses(tap_uuid, name, created_at, updated_at) " +
                                    "VALUES(:tap_uuid, :name, NOW(), NOW())")
                            .bind("tap_uuid", tapUUID)
                            .bind("name", bus.name())
                            .execute();
                } else {
                    handle.createUpdate("UPDATE tap_buses SET updated_at = NOW() " +
                                    "WHERE tap_uuid = :tap_uuid AND name = :name")
                            .bind("tap_uuid", tapUUID)
                            .bind("name", bus.name())
                            .execute();
                }

                Long busId = handle.createQuery("SELECT id FROM tap_buses " +
                                "WHERE tap_uuid = :tap_uuid AND name = :name")
                        .bind("tap_uuid", tapUUID)
                        .bind("name", bus.name())
                        .mapTo(Long.class)
                        .one();

                // Register bus channels.
                for (ChannelReport channel : bus.channels()) {
                    long channelCount = handle.createQuery("SELECT COUNT(*) AS count FROM bus_channels " +
                                    "WHERE bus_id = :bus_id AND name = :channel_name")
                            .bind("bus_id", busId)
                            .bind("channel_name", channel.name())
                            .mapTo(Long.class)
                            .one();

                    if (channelCount == 0) {
                        handle.createUpdate("INSERT INTO bus_channels(name, bus_id, capacity, watermark, errors_total, " +
                                        "errors_average, throughput_bytes_total, throughput_bytes_average, " +
                                        "throughput_messages_total, throughput_messages_average, created_at, updated_at) " +
                                        "VALUES(:name, :bus_id, :capacity, :watermark, :errors_total, :errors_average, " +
                                        ":throughput_bytes_total, :throughput_bytes_average, :throughput_messages_total, " +
                                        ":throughput_messages_average, NOW(), NOW())")
                                .bind("name", channel.name())
                                .bind("bus_id", busId)
                                .bind("capacity", channel.capacity())
                                .bind("watermark", channel.watermark())
                                .bind("errors_total", channel.errors().total())
                                .bind("errors_average", channel.errors().average())
                                .bind("throughput_bytes_total", channel.throughputBytes().total())
                                .bind("throughput_bytes_average", channel.throughputBytes().average())
                                .bind("throughput_messages_total", channel.throughputMessages().total())
                                .bind("throughput_messages_average", channel.throughputMessages().average())
                                .execute();
                    } else {
                        handle.createUpdate("UPDATE bus_channels SET capacity = :capacity, watermark = :watermark, " +
                                        "errors_total = :errors_total, errors_average = :errors_average, " +
                                        "throughput_bytes_total = :throughput_bytes_total, " +
                                        "throughput_bytes_average = :throughput_bytes_average, " +
                                        "throughput_messages_total = :throughput_messages_total, " +
                                        "throughput_messages_average = :throughput_messages_average, " +
                                        "updated_at = NOW() WHERE bus_id = :bus_id AND name = :name")
                                .bind("name", channel.name())
                                .bind("bus_id", busId)
                                .bind("capacity", channel.capacity())
                                .bind("watermark", channel.watermark())
                                .bind("errors_total", channel.errors().total())
                                .bind("errors_average", channel.errors().average())
                                .bind("throughput_bytes_total", channel.throughputBytes().total())
                                .bind("throughput_bytes_average", channel.throughputBytes().average())
                                .bind("throughput_messages_total", channel.throughputMessages().total())
                                .bind("throughput_messages_average", channel.throughputMessages().average())
                                .execute();
                    }

                    // Capture metrics.
                    gaugesBatch.bind("tap_uuid", tapUUID)
                            .bind("metric_name", "channels." + bus.name().toLowerCase() + "." + channel.name().toLowerCase() + ".usage")
                            .bind("metric_value", channel.watermark().doubleValue())
                            .bind("timestamp", report.timestamp())
                            .add();

                    gaugesBatch.bind("tap_uuid", tapUUID)
                            .bind("metric_name", "channels." + bus.name().toLowerCase() + "." + channel.name().toLowerCase() + ".usage_percent")
                            .bind("metric_value",  channel.watermark() > 0 ? channel.watermark() * 100.0 / channel.capacity() : 0.0D)
                            .bind("timestamp", report.timestamp())
                            .add();

                    gaugesBatch.bind("tap_uuid", tapUUID)
                            .bind("metric_name", "channels." + bus.name().toLowerCase() + "." + channel.name().toLowerCase() + ".throughput_messages")
                            .bind("metric_value",  channel.throughputMessages().average() / 10.0)
                            .bind("timestamp", report.timestamp())
                            .add();

                    gaugesBatch.bind("tap_uuid", tapUUID)
                            .bind("metric_name", "channels." + bus.name().toLowerCase() + "." + channel.name().toLowerCase() + ".throughput_bytes")
                            .bind("metric_value",  channel.throughputBytes().average() / 10.0)
                            .bind("timestamp", report.timestamp())
                            .add();

                    gaugesBatch.bind("tap_uuid", tapUUID)
                            .bind("metric_name", "channels." + bus.name().toLowerCase() + "." + channel.name().toLowerCase() + ".errors")
                            .bind("metric_value",  channel.errors().average() / 10.0)
                            .bind("timestamp", report.timestamp())
                            .add();
                }
            }

            // Gauges.
            for (Map.Entry<String, Long> metric : report.gaugesLong().entrySet()) {
                gaugesBatch.bind("tap_uuid", tapUUID)
                        .bind("metric_name", metric.getKey())
                        .bind("metric_value", metric.getValue().doubleValue())
                        .bind("timestamp", report.timestamp())
                        .add();
            }
            for (Map.Entry<String, Double> metric : report.gaugesFloat().entrySet()) {
                gaugesBatch.bind("tap_uuid", tapUUID)
                        .bind("metric_name", metric.getKey())
                        .bind("metric_value", metric.getValue().doubleValue())
                        .bind("timestamp", report.timestamp())
                        .add();
            }

            // Timers.
            for (Map.Entry<String, TimersReport> timer : report.timers().entrySet()) {
                timersBatch
                        .bind("tap_uuid", tapUUID)
                        .bind("metric_name", timer.getKey())
                        .bind("mean", timer.getValue().mean())
                        .bind("p99", timer.getValue().p99())
                        .bind("timestamp", report.timestamp())
                        .add();
            }

            // Additional metrics.
            gaugesBatch.bind("tap_uuid", tapUUID)
                    .bind("metric_name", "system.captures.throughput_bit_sec")
                    .bind("metric_value", report.processedBytes().average() * 8 / 10.0)
                    .bind("timestamp", report.timestamp())
                    .add();

            gaugesBatch.bind("tap_uuid", tapUUID)
                    .bind("metric_name", "os.memory.bytes_used")
                    .bind("metric_value", report.systemMetrics().memoryTotal().doubleValue() - report.systemMetrics().memoryFree().doubleValue())
                    .bind("timestamp", report.timestamp())
                    .add();

            gaugesBatch.bind("tap_uuid", tapUUID)
                    .bind("metric_name", "os.cpu.load.percent")
                    .bind("metric_value", report.systemMetrics().cpuLoad())
                    .bind("timestamp", report.timestamp())
                    .add();

            // CPU core loads.
            for (Map.Entry<Integer, Double> core : report.systemMetrics().cpuCoresLoad().entrySet()) {
                gaugesBatch.bind("tap_uuid", tapUUID)
                        .bind("metric_name", "os.cpu.cores." + core.getKey() + ".load.percent")
                        .bind("metric_value", core.getValue())
                        .bind("timestamp", report.timestamp())
                        .add();
            }

            if (report.systemMetrics().rpiTemperature() != null) {
                gaugesBatch.bind("tap_uuid", tapUUID)
                        .bind("metric_name", "rpi.temperature")
                        .bind("metric_value", report.systemMetrics().rpiTemperature())
                        .bind("timestamp", report.timestamp())
                        .add();
            }

            // Log counts.
            for (Map.Entry<String, Long> lc : report.logCounts().entrySet()) {
                if (lc.getKey().equals("error")
                        || lc.getKey().equals("warn")
                        || lc.getKey().equals("info")
                        || lc.getKey().equals("debug")
                        || lc.getKey().equals("trace")) {

                    gaugesBatch.bind("tap_uuid", tapUUID)
                            .bind("metric_name", "logs.counts." + lc.getKey())
                            .bind("metric_value", lc.getValue().doubleValue())
                            .bind("timestamp", report.timestamp())
                            .add();
                } else {
                    LOG.error("Unexpected log level in tap [{}] log counts report: {}", tapUUID, lc.getKey());
                }
            }

            // Engagement capture logs.
            PreparedBatch engagementLogBatch = handle.prepareBatch("INSERT INTO tap_engagement_logs(message, " +
                    "tap_uuid, timestamp) VALUES(:message, :tap_uuid, :timestamp)");
            for (EngagementLogReport log : report.engagementLogs()) {
                engagementLogBatch
                        .bind("message", log.message())
                        .bind("timestamp", log.timestamp())
                        .bind("tap_uuid", tapUUID)
                        .add();
            }
            engagementLogBatch.execute();

            gaugesBatch.execute();
            timersBatch.execute();
        });
    }

    public void registerTapContext(TapContextReport report, UUID tapUuid) {
        Optional<Tap> tap = findTap(tapUuid);

        if (tap.isEmpty()) {
            LOG.error("No tap with UUID [{}] found. Not writing context.", tapUuid);
            return;
        }

        for (TapMacContextReport mac : report.macs()) {
            Optional<MacAddressContextEntry> existingContext = nzyme.getContextService()
                    .findMacAddressContextNoCache(mac.mac(), tap.get().organizationId(), tap.get().tenantId());

            if (mac.hostnames().isEmpty() && mac.ipAddresses().isEmpty()) {
                // Do not process empty context.
                LOG.debug("Skipping empty context for [{}] from tap [{}].", mac.mac(), tapUuid);
                continue;
            }

            if (mac.mac().equals("00:00:00:00:00:00")) {
                // Do not process invalid MAC. (This can happen on loopback interface captures. Maybe rawip, too.)
                LOG.debug("Skipping invalid MAC [{}] reported by tap [{}].", mac.mac(), tapUuid);
                continue;
            }

            long contextId;
            List<MacAddressTransparentContextEntry> transparentContext;
            if (existingContext.isPresent()) {
                // Update existing context.
                contextId = existingContext.get().id();

                nzyme.getContextService().updateMacAddressContext(
                        existingContext.get().uuid(),
                        tap.get().organizationId(),
                        tap.get().tenantId(),
                        existingContext.get().name(),
                        existingContext.get().description(),
                        existingContext.get().notes()
                );

                transparentContext = nzyme.getContextService()
                        .findTransparentMacAddressContext(existingContext.get().id());
            } else {
                // Write new context.
                contextId = nzyme.getContextService().createMacAddressContext(
                        mac.mac(),
                        null,
                        "Created via transparent context.",
                        null,
                        tap.get().organizationId(),
                        tap.get().tenantId()
                );

                transparentContext = Lists.newArrayList();
            }

            nzyme.getDatabase().useHandle(handle -> {
                for (TapContextDataReport ip : mac.ipAddresses()) {
                    try {
                        InetAddress ipAddr = InetAddress.getByName(ip.value());

                        if (!ipAddr.isSiteLocalAddress()) {
                            // We only handle local addresses.
                            continue;
                        }

                        boolean exists = false;
                        for (MacAddressTransparentContextEntry tcx : transparentContext) {
                            if (ContextService.TransparentDataType.valueOf(tcx.type()).equals(ContextService.TransparentDataType.IP_ADDRESS)
                                    && tcx.source().equals(ip.source())
                                    && tcx.ipAddress() != null
                                    && tcx.ipAddress().equals(ipAddr)) {
                                exists = true;
                            }
                        }

                        if (!exists) {
                            nzyme.getContextService().registerTransparentMacAddressIpAddress(
                                    handle,
                                    contextId,
                                    tapUuid,
                                    ip.source(),
                                    ipAddr,
                                    ip.lastSeen()
                            );
                        } else {
                            nzyme.getContextService().touchTransparentMacAddressIpAddress(
                                    handle,
                                    contextId,
                                    tapUuid,
                                    ip.source(),
                                    ipAddr,
                                    ip.lastSeen()
                            );
                        }

                        // Attach IP to asset.
                        nzyme.getAssetsManager().attachTransparentContextIpAddress(
                                mac.mac(),
                                tap.get().organizationId(),
                                tap.get().tenantId(),
                                ipAddr,
                                ip.source(),
                                ip.lastSeen()
                        );
                    } catch (UnknownHostException e) {
                        LOG.error("Could not parse IP address [{}] for context <{}>.",
                                ip.value(), contextId, e);
                    }
                }

                for (TapContextDataReport hostname : mac.hostnames()) {
                    boolean exists = false;
                    for (MacAddressTransparentContextEntry tcx : transparentContext) {
                        if (ContextService.TransparentDataType.valueOf(tcx.type()).equals(ContextService.TransparentDataType.HOSTNAME)
                                && tcx.tapUuid().equals(tapUuid)
                                && tcx.source().equals(hostname.source())
                                && tcx.hostname() != null
                                && tcx.hostname().equals(hostname.value())) {
                            exists = true;
                        }
                    }

                    if (!exists) {
                        nzyme.getContextService().registerTransparentMacAddressHostname(
                                handle,
                                contextId,
                                tapUuid,
                                hostname.source(),
                                hostname.value(),
                                hostname.lastSeen()
                        );
                    } else {
                        nzyme.getContextService().touchTransparentMacAddressHostname(
                                handle,
                                contextId,
                                tapUuid,
                                hostname.source(),
                                hostname.value(),
                                hostname.lastSeen()
                        );
                    }

                    // Attach hostname to asset.
                    nzyme.getAssetsManager().attachTransparentContextHostname(
                            mac.mac(),
                            tap.get().organizationId(),
                            tap.get().tenantId(),
                            hostname.value(),
                            hostname.source(),
                            hostname.lastSeen()
                    );
                }
            });
        }
    }

    private void retentionCleanMetrics() {
        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("DELETE FROM tap_metrics_gauges WHERE created_at < :created_at")
                    .bind("created_at", DateTime.now().minusHours(24))
                    .execute();
        });

        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("DELETE FROM tap_metrics_timers WHERE created_at < :created_at")
                    .bind("created_at", DateTime.now().minusHours(24))
                    .execute();
        });

        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("DELETE FROM tap_engagement_logs WHERE timestamp < :timestamp")
                    .bind("timestamp", DateTime.now().minusDays(30))
                    .execute();
        });
    }


    public List<Tap> findAllTapsByUUIDs(List<UUID> tapIds) {
        if (tapIds.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM taps WHERE deleted = false AND uuid IN (<ids>) " +
                                "ORDER BY name ASC")
                        .bindList("ids", tapIds)
                        .mapTo(Tap.class)
                        .list()
        );
    }

    public List<Tap> findAllTapsOfAllUsers() {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM taps WHERE deleted = false")
                        .mapTo(Tap.class)
                        .list()
        );
    }

    public List<Tap> findAllTapsOfOrganization(UUID organizationUUID) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM taps WHERE deleted = false " +
                                "AND organization_id = :organization_uuid")
                        .bind("organization_uuid", organizationUUID)
                        .mapTo(Tap.class)
                        .list()
        );
    }

    public List<Tap> findAllTapsOfTenant(UUID organizationUUID, UUID tenantUUID) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM taps " +
                                "WHERE deleted = false AND organization_id = :organization_uuid " +
                                "AND tenant_id = :tenant_uuid")
                        .bind("organization_uuid", organizationUUID)
                        .bind("tenant_uuid", tenantUUID)
                        .mapTo(Tap.class)
                        .list()
        );
    }

    public List<Tap> findAllTapsOnFloor(UUID organizationUUID, UUID tenantUUID, UUID locationUUID, UUID floorUUID) {
        if (organizationUUID == null && tenantUUID == null) {
            return nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT * FROM taps " +
                                    "WHERE deleted = false AND location_uuid = :location_uuid " +
                                    "AND floor_uuid = :floor_uuid")
                            .bind("location_uuid", locationUUID)
                            .bind("floor_uuid", floorUUID)
                            .mapTo(Tap.class)
                            .list()
            );
        }

        if (organizationUUID != null && tenantUUID == null) {
            // Org Admin.
            return nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT * FROM taps WHERE deleted = false " +
                                    "AND organization_id = :organization_uuid " +
                                    "AND location_uuid = :location_uuid AND floor_uuid = :floor_uuid")
                            .bind("organization_uuid", organizationUUID)
                            .bind("location_uuid", locationUUID)
                            .bind("floor_uuid", floorUUID)
                            .mapTo(Tap.class)
                            .list()
            );
        }

        // Tenant user.
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM taps WHERE deleted = false AND " +
                                "organization_id = :organization_uuid " +
                                "AND tenant_id = :tenant_uuid AND location_uuid = :location_uuid " +
                                "AND floor_uuid = :floor_uuid")
                        .bind("organization_uuid", organizationUUID)
                        .bind("tenant_uuid", tenantUUID)
                        .bind("location_uuid", locationUUID)
                        .bind("floor_uuid", floorUUID)
                        .mapTo(Tap.class)
                        .list()
        );
    }

    public List<Tap> findAllTapsAtLocation(UUID locationUUID) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM taps WHERE deleted = false AND location_uuid = :location_uuid")
                        .bind("location_uuid", locationUUID)
                        .mapTo(Tap.class)
                        .list()
        );
    }

    public List<UUID> allTapUUIDsAccessibleByUser(AuthenticatedUser user) {
        List<UUID> allTaps = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT uuid FROM taps WHERE deleted = false")
                        .mapTo(UUID.class)
                        .list()
        );

        if (user.isSuperAdministrator()) {
            return allTaps;
        }

        if (user.isOrganizationAdministrator()) {
            if (user.getOrganizationId() == null) {
                throw new RuntimeException("NULL organization ID.");
            }

            return nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT uuid FROM taps WHERE deleted = false " +
                                    "AND organization_id = :organization_id")
                            .bind("organization_id", user.getOrganizationId())
                            .mapTo(UUID.class)
                            .list()
            );
        }

        // User is tenant user, check that it has required fields.
        if (user.getOrganizationId() == null || user.getTenantId() == null) {
            throw new RuntimeException("NULL organization or tenant ID.");
        }

        // Get taps from user permissions.
        List<UUID> tapPermissions = nzyme.getAuthenticationService().findTapPermissionsOfUser(user.getUserId());

        if (tapPermissions.isEmpty()) {
            // User has no specific tap permissions. Check if all taps are allowed.
            if (user.accessAllTenantTaps) {
                return nzyme.getDatabase().withHandle(handle ->
                        handle.createQuery("SELECT uuid FROM taps " +
                                        "WHERE deleted = false AND organization_id = :organization_id " +
                                        "AND tenant_id = :tenant_id")
                                .bind("organization_id", user.getOrganizationId())
                                .bind("tenant_id", user.getTenantId())
                                .mapTo(UUID.class)
                                .list()
                );
            } else {
                // User is not allowed to use all taps and has no specific tap permissions.
                return Collections.emptyList();
            }
        } else {
            // Return only specifically allowed taps.
            List<UUID> validatedTaps = Lists.newArrayList();
            for (UUID permission : tapPermissions) {
                if (allTaps.contains(permission)) {
                    validatedTaps.add(permission);
                }
            }

            return validatedTaps;
        }
    }

    public List<UUID> allTapUUIDsAccessibleByScope(@Nullable UUID organizationId, @Nullable UUID tenantId) {
        List<Tap> taps;
        if (organizationId == null && tenantId == null) {
            // Super Admin.
            taps = findAllTapsOfAllUsers();
        } else if (organizationId != null && tenantId == null) {
            // Organization Admin.
            taps = findAllTapsOfOrganization(organizationId);
        } else {
            // Tenant User.
            taps = findAllTapsOfTenant(organizationId, tenantId);
        }

        List<UUID> tapUUIDs = Lists.newArrayList();
        for (Tap tap : taps) {
            tapUUIDs.add(tap.uuid());
        }

        return tapUUIDs;
    }

    public List<Tap> allTapsAccessibleByScope(@Nullable UUID organizationId, @Nullable UUID tenantId) {
        if (organizationId == null && tenantId == null) {
            // Super Admin.
            return findAllTapsOfAllUsers();
        } else if (organizationId != null && tenantId == null) {
            // Organization Admin.
            return findAllTapsOfOrganization(organizationId);
        } else {
            // Tenant User.
            return findAllTapsOfTenant(organizationId, tenantId);
        }
    }

    public Optional<Tap> findTap(UUID uuid) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM taps WHERE deleted = false AND uuid = :uuid")
                        .bind("uuid", uuid)
                        .mapTo(Tap.class)
                        .findOne()
        );
    }

    public List<TapMetricsGauge> findGaugesOfTap(UUID tapUUID) {
         return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT ON (metric_name) metric_name, tap_uuid, metric_value, created_at " +
                        "FROM tap_metrics_gauges WHERE tap_uuid = :tap_uuid AND created_at > :created_at " +
                        "ORDER BY metric_name, created_at DESC")
                        .bind("tap_uuid", tapUUID)
                        .bind("created_at", DateTime.now().minusMinutes(1))
                        .mapTo(TapMetricsGauge.class)
                        .list()
        );
    }

    public List<TapMetricsTimer> findTimersOfTap(UUID tapUUID) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT ON (metric_name) metric_name, tap_uuid, mean, p99, created_at " +
                                "FROM tap_metrics_timers WHERE tap_uuid = :tap_uuid AND created_at > :created_at " +
                                "ORDER BY metric_name, created_at DESC")
                        .bind("tap_uuid", tapUUID)
                        .bind("created_at", DateTime.now().minusMinutes(1))
                        .mapTo(TapMetricsTimer.class)
                        .list()
        );
    }

    public Optional<Map<DateTime, TapMetricsTimerHistogramAggregation>> findMetricsGaugeHistogram(@Nullable UUID tapUUID,
                                                                                                  String metricName,
                                                                                                  int hours,
                                                                                                  BucketSize bucketSize) {
        Map<DateTime, TapMetricsTimerHistogramAggregation> result = Maps.newHashMap();

        List<TapMetricsTimerHistogramAggregation> agg = nzyme.getDatabase().withHandle(handle -> {
            String query = "SELECT AVG(metric_value) AS average, MAX(metric_value) AS maximum, " +
                    "MIN(metric_value) AS minimum, date_trunc(:bucket_size, created_at) AS bucket " +
                    "FROM tap_metrics_gauges WHERE";

            if (tapUUID != null) {
                query += " tap_uuid = :tap_uuid AND ";
            }

            query += " metric_name = :metric_name AND created_at > :created_at GROUP BY bucket ORDER BY bucket DESC ";

            return handle.createQuery(query)
                    .bind("bucket_size", bucketSize.toString().toLowerCase())
                    .bind("tap_uuid", tapUUID)
                    .bind("metric_name", metricName)
                    .bind("created_at", DateTime.now().minusHours(hours))
                    .mapTo(TapMetricsTimerHistogramAggregation.class)
                    .list();
        });

        if (agg == null || agg.isEmpty()) {
            return Optional.empty();
        }

        for (TapMetricsTimerHistogramAggregation x : agg) {
            result.put(x.bucket(), x);
        }

        return Optional.of(result);
    }

    public Optional<Double> findLatestActiveMetricsGaugeValue(UUID tapUuid,
                                                              String metricName) {
        return nzyme.getDatabase().withHandle(
                handle -> findLatestActiveMetricsGaugeValue(tapUuid, metricName, handle)
        );
    }

    public Optional<Double> findLatestActiveMetricsGaugeValue(UUID tapUuid,
                                                              String metricName,
                                                              Handle handle) {
        return handle.createQuery("SELECT metric_value FROM tap_metrics_gauges " +
                        "WHERE tap_uuid = :tap_uuid AND metric_name = :metric_name " +
                        "AND created_at > :created_at " +
                        "ORDER BY created_at DESC " +
                        "LIMIT 1")
                .bind("tap_uuid", tapUuid)
                .bind("metric_name", metricName)
                .bind("created_at", DateTime.now().minusMinutes(2))
                .mapTo(Double.class)
                .findOne();
    }

    public Optional<Map<DateTime, TapMetricsTimerHistogramAggregation>> findMetricsTimerHistogram(UUID tapUUID,
                                                                                                  String metricName,
                                                                                                  int hours,
                                                                                                  BucketSize bucketSize) {
        Map<DateTime, TapMetricsTimerHistogramAggregation> result = Maps.newHashMap();

        List<TapMetricsTimerHistogramAggregation> agg = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT AVG(mean) AS average, MAX(mean) AS maximum, " +
                                "MIN(mean) AS minimum, date_trunc(:bucket_size, created_at) AS bucket " +
                                "FROM tap_metrics_timers WHERE tap_uuid = :tap_uuid AND metric_name = :metric_name " +
                                "AND created_at > :created_at GROUP BY bucket ORDER BY bucket DESC")
                        .bind("bucket_size", bucketSize.toString().toLowerCase())
                        .bind("tap_uuid", tapUUID)
                        .bind("metric_name", metricName)
                        .bind("created_at", DateTime.now().minusHours(hours))
                        .mapTo(TapMetricsTimerHistogramAggregation.class)
                        .list()
        );

        if (agg == null || agg.isEmpty()) {
            return Optional.empty();
        }

        for (TapMetricsTimerHistogramAggregation x : agg) {
            result.put(x.bucket(), x);
        }

        return Optional.of(result);
    }

    public List<TapMetricsTimerAggregation> findAverageTimerValuesOfTap(UUID tapUuid, DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
            handle.createQuery("SELECT metric_name, AVG(mean) AS mean, AVG(p99) AS p99, " +
                            "MAX(created_at) AS created_at FROM tap_metrics_timers " +
                            "WHERE tap_uuid = :tap_uuid AND created_at >= :since AND created_at <= NOW() " +
                            "GROUP BY metric_name")
                    .bind("tap_uuid", tapUuid)
                    .bind("since", since)
                    .mapTo(TapMetricsTimerAggregation.class)
                    .list()
        );
    }

    public List<TapMetricsGaugeAggregation> findAverageGaugeValuesOfTap(UUID tapUuid, DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT metric_name, AVG(metric_value) AS value FROM tap_metrics_gauges " +
                                "WHERE tap_uuid = :tap_uuid AND created_at >= :since AND created_at <= NOW() " +
                                "GROUP BY metric_name")
                        .bind("tap_uuid", tapUuid)
                        .bind("since", since)
                        .mapTo(TapMetricsGaugeAggregation.class)
                        .list()
        );
    }

    public Optional<List<Bus>> findBusesOfTap(UUID tapUUID) {
        List<Bus> buses = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM tap_buses WHERE tap_uuid = :tap_uuid AND updated_at > :last_seen")
                        .bind("tap_uuid", tapUUID)
                        .bind("last_seen", DateTime.now().minusMinutes(1))
                        .mapTo(Bus.class)
                        .list()
        );

        return buses == null || buses.isEmpty() ? Optional.empty() : Optional.of(buses);
    }

    public Optional<List<Channel>> findChannelsOfBus(long busId) {
        List<Channel> channels = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM bus_channels WHERE bus_id = :bus_id AND updated_at > :last_seen")
                        .bind("bus_id", busId)
                        .bind("last_seen", DateTime.now().minusHours(1))
                        .mapTo(Channel.class)
                        .list()
        );

        return channels == null || channels.isEmpty() ? Optional.empty() : Optional.of(channels);
    }

    public List<Capture> findActiveCapturesOfTap(UUID tapUUID) {
        return findCapturesOfTap(tapUUID, DateTime.now().minusMinutes(1));
    }

    public List<Capture> findCapturesOfTap(UUID tapUUID, DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM tap_captures WHERE tap_uuid = :tap_uuid AND updated_at > :last_seen")
                        .bind("tap_uuid", tapUUID)
                        .bind("last_seen", since)
                        .mapTo(Capture.class)
                        .list()
        );
    }

    public List<Dot11FrequencyAndChannelWidthEntry> findDot11FrequenciesOfTap(UUID tapUuid) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT f.id, f.interface_uuid, f.frequency, f.channel_widths FROM tap_captures_frequencies f " +
                        "LEFT JOIN tap_captures AS c ON f.interface_uuid = c.uuid " +
                        "WHERE c.tap_uuid = :tap_uuid AND c.capture_type = 'WiFi'")
                .bind("tap_uuid", tapUuid)
                .mapTo(Dot11FrequencyAndChannelWidthEntry.class)
                .list());
    }

    public long countEngagementLogOfTap(UUID tapUUID) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM tap_engagement_logs WHERE tap_uuid = :tap_uuid")
                        .bind("tap_uuid", tapUUID)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<EngagementLogEntry> findEngagementLogOfTap(UUID tapUUID, int limit, int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM tap_engagement_logs WHERE tap_uuid = :tap_uuid " +
                                "ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
                        .bind("tap_uuid", tapUUID)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(EngagementLogEntry.class)
                        .list()
        );
    }

    public Optional<TenantLocationFloorEntry> guessFloorOfSignalSource(List<TapBasedSignalStrengthResult> signalStrengths) {
        Map<TapPositionKey, Integer> floorSummaries = Maps.newHashMap();
        Map<TapPositionKey, Integer> floorTapCounts = Maps.newHashMap();

        for (TapBasedSignalStrengthResult ss : signalStrengths) {
            Tap tap = findTap(ss.tapUuid()).orElseThrow();

            if (tap.locationId() == null || tap.floorId() == null) {
                continue;
            }

            TapPositionKey key = TapPositionKey.create(tap.locationId(), tap.floorId());

            Integer signalStrength = floorSummaries.get(key);
            if (signalStrength != null) {
                floorSummaries.put(key, (int) (signalStrength+ss.signalStrength()));
            } else {
                floorSummaries.put(key, (int) ss.signalStrength());
            }

            Integer tapCount = floorTapCounts.get(key);
            if (tapCount != null) {
                floorTapCounts.put(key, tapCount+1);
            } else {
                floorTapCounts.put(key, 1);
            }
        }

        // Filter out all floors that have less than three placed taps.
        Map<TapPositionKey, Integer> filteredFloorSummaries = Maps.newHashMap();
        for (Map.Entry<TapPositionKey, Integer> tc : floorTapCounts.entrySet()) {
            if (tc.getValue() < 3) {
                continue;
            }

            Integer floorSummary = floorSummaries.get(tc.getKey());
            if (floorSummary == null) {
                continue;
            }

            filteredFloorSummaries.put(tc.getKey(), floorSummary);
        }

        if (filteredFloorSummaries.isEmpty()) {
            return Optional.empty();
        }

        int highest = Integer.MIN_VALUE;
        TapPositionKey result = null;
        for (Map.Entry<TapPositionKey, Integer> summary : filteredFloorSummaries.entrySet()) {
            if (summary.getValue() > highest) {
                highest = summary.getValue();
                result = summary.getKey();
            }
        }

        if (result == null) {
            return Optional.empty();
        }

        return Optional.of(
                nzyme.getAuthenticationService()
                        .findFloorOfTenantLocation(result.locationId(), result.floorId())
                        .orElseThrow()
        );
    }

    @AutoValue
    public static abstract class TapPositionKey {
        public abstract UUID locationId();
        public abstract UUID floorId();

        public static TapPositionKey create(UUID locationId, UUID floorId) {
            return builder()
                    .locationId(locationId)
                    .floorId(floorId)
                    .build();
        }

        public static Builder builder() {
            return new AutoValue_TapManager_TapPositionKey.Builder();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            public abstract Builder locationId(UUID locationId);

            public abstract Builder floorId(UUID floorId);

            public abstract TapPositionKey build();
        }
    }
}
