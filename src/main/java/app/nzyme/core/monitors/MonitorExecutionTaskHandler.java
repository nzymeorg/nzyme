package app.nzyme.core.monitors;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.monitors.db.MonitorEntry;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.TimeRangeFactory;
import app.nzyme.core.util.filters.FilterParser;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.distributed.tasksqueue.ReceivedTask;
import app.nzyme.plugin.distributed.tasksqueue.TaskHandler;
import app.nzyme.plugin.distributed.tasksqueue.TaskProcessingResult;
import com.codahale.metrics.Timer;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MonitorExecutionTaskHandler implements TaskHandler {

    private static final Logger LOG = LogManager.getLogger(MonitorExecutionTaskHandler.class);

    private final NzymeNode nzyme;
    private final ObjectMapper om;

    public MonitorExecutionTaskHandler(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.om = JsonMapper.builder()
                .addModule(new JodaModule())
                .build();
    }

    @Override
    public TaskProcessingResult handle(ReceivedTask task) {
        LOG.trace("Received task {}", task);

        DateTime now = DateTime.now();

        MonitorEntry monitor;
        try {
            monitor = this.om.readValue(
                    (String) task.parametersMap().get("monitor"),
                    MonitorEntry.class
            );
        } catch (JacksonException e) {
            LOG.error("Could not deserialize monitor.", e);
            return TaskProcessingResult.FAILURE;
        }

        LOG.debug("Received task to execute monitor [{}].", monitor.uuid());

        MonitorType type;
        try {
            type = MonitorType.valueOf(monitor.type());
        } catch (IllegalArgumentException e) {
            LOG.error("Unknown monitor type [{}]. Skipping.", monitor.type());
            return TaskProcessingResult.FAILURE;
        }

        Timer timer = nzyme.getMetrics().timer(MetricNames.MONITOR_EXECUTION_TIMER_BASE + monitor.uuid());
        try (Timer.Context ignored = timer.time()) {
            nzyme.getMonitors().setMonitorStatus(monitor.uuid(), MonitorStatus.EXECUTING);

            Filters filters = FilterParser.parseFiltersQueryParameter(monitor.filters());
            List<UUID> taps;
            if (monitor.taps() != null) {
                taps = monitor.taps();
            } else {
                taps = nzyme.getTapManager().findAllTapsOfTenant(monitor.organizationId(), monitor.tenantId())
                        .stream()
                        .map(Tap::uuid)
                        .toList();
            }

            TimeRange lookback = TimeRangeFactory.relative(monitor.lookback());
            long count = switch (type) {
                case DOT11_BSSID -> nzyme.getDot11()
                        .countBSSIDs(lookback, filters, taps);
                case DOT11_CLIENT_CONNECTED -> nzyme.getDot11()
                        .countBSSIDClients(lookback, filters, taps);
                case DOT11_CLIENT_DISCONNECTED -> nzyme.getDot11()
                        .countClients(lookback, filters, false, taps);
                case BLUETOOTH_DEVICE -> nzyme.getBluetooth()
                        .countAllDevices(lookback, filters, taps);
                case ETHERNET_ASSET -> nzyme.getAssetsManager()
                        .countAssets(lookback, filters, monitor.organizationId(), monitor.tenantId());
                case ETHERNET_L4_SESSION -> nzyme.getEthernet().l4()
                        .countAllSessions(lookback, filters, taps);
                case ETHERNET_DNS_TRANSACTION -> nzyme.getEthernet().dns()
                        .countAllQueries(lookback, filters, taps);
                case ETHERNET_DHCP_TRANSACTION -> nzyme.getEthernet().dhcp()
                        .countAllTransactions(lookback, filters, taps);
                case ETHERNET_ARP_PACKET -> nzyme.getEthernet().arp()
                        .countAllPackets(lookback, filters, taps);
                case ETHERNET_SSH_SESSION -> nzyme.getEthernet().ssh()
                        .countAllSessions(lookback, filters, taps);
                case ETHERNET_SOCKS_TUNNEL -> nzyme.getEthernet().socks()
                        .countAllTunnels(lookback, filters, taps);
                case ETHERNET_VPN_TUNNEL -> nzyme.getEthernet().vpn()
                        .countAllTunnels(lookback, filters, taps);
                case ETHERNET_TLS_SESSION -> nzyme.getEthernet().tls()
                        .countAllSessions(lookback, filters, taps);
                case ETHERNET_TLS_CERTIFICATE -> nzyme.getEthernet().tls()
                        .countAllCertificates(lookback, filters, taps);
                case ETHERNET_RTSP_STREAM -> nzyme.getEthernet().rtsp()
                        .countAllStreams(lookback, filters, taps);
                case ETHERNET_WEBRTC_SESSION -> nzyme.getEthernet().webRtc()
                        .countAllSessions(lookback, filters, taps);
                case ETHERNET_NTP_TRANSACTION -> nzyme.getEthernet().ntp()
                        .countAllTransactions(lookback, filters, taps);
                case ETHERNET_STUN_DISCOVERY -> nzyme.getEthernet().nat()
                        .countAllDiscoveries(lookback, filters, taps);
                case ETHERNET_STUN_CONNECTION -> nzyme.getEthernet().nat()
                        .countAllNegotiations(lookback, filters, taps);
                case ETHERNET_PORTAL_INTEGRITY_REPORT -> nzyme.getEthernet().portalIntegrity()
                        .countAllIntegrityReports(lookback, filters, taps);
            };

            if (count > monitor.triggerCondition()) {
                // Raise alert.
                Map<String, String> attributes = Maps.newHashMap();
                attributes.put("monitor_uuid", monitor.uuid().toString());
                attributes.put("monitor_name", monitor.name());
                attributes.put("monitor_type", monitor.type());
                attributes.put("trigger_condition", String.valueOf(monitor.triggerCondition()));
                attributes.put("result_count", String.valueOf(count));

                nzyme.getDetectionAlertService().raiseAlert(
                        monitor.organizationId(),
                        monitor.tenantId(),
                        null,
                        null,
                        type.getDetectionType(),
                        type.getSubsystem(),
                        "Monitor \"" + monitor.name() + "\" triggered.",
                        attributes,
                        Set.of("monitor_uuid")
                );

                // Update `last_event` state of monitor.
                nzyme.getMonitors().setLastEventOfMonitor(monitor.uuid(), now);
            } else {
                LOG.debug("Monitor [{}] result count <{}> is below trigger condition <{}>. No alert.",
                        monitor.uuid(), count, monitor.triggerCondition());
            }

            nzyme.getMonitors().setLastExecutionTimeOfMonitor(monitor.uuid(), now);
        } catch (Exception e) {
            LOG.error("Could not execute monitor [{}].", monitor.uuid(), e);
        } finally {
            try {
                nzyme.getMonitors().setMonitorStatus(monitor.uuid(), MonitorStatus.IDLE);
            } catch (Exception e) {
                LOG.error("Could not set monitor [{}] status to IDLE. Skipping.", monitor.uuid(), e);
            }
        }

        return TaskProcessingResult.SUCCESS;
    }

    @Override
    public String getName() {
        return "Monitor Execution";
    }
}
