package app.nzyme.core.dot11.monitoring.disco;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.dot11.monitoring.disco.monitormethods.DiscoMonitorFactory;
import app.nzyme.core.dot11.monitoring.disco.monitormethods.DiscoMonitorMethodType;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.OrganizationEntry;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.core.taps.Tap;
import app.nzyme.plugin.Subsystem;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Dot11DiscoMonitor extends Periodical {

    private static final Logger LOG = LogManager.getLogger(Dot11DiscoMonitor.class);

    private final NzymeNode nzyme;
    private final ObjectMapper om;

    public Dot11DiscoMonitor(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.om = JsonMapper.builder()
                .addModule(new JodaModule())
                .build();
    }

    @Override
    protected void execute() {
        for (OrganizationEntry org : nzyme.getAuthenticationService().findAllOrganizations()) {
            for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfOrganization(org.uuid())) {
                for (MonitoredSSID monitoredNetwork : nzyme.getDot11().findAllMonitoredSSIDs(org.uuid(), tenant.uuid())) {
                    if (!monitoredNetwork.enabledDiscoMonitor() || monitoredNetwork.discoMonitorType() == null) {
                        continue;
                    }

                    DiscoMonitorMethodType method;
                    try {
                        method = DiscoMonitorMethodType.valueOf(monitoredNetwork.discoMonitorType());
                    } catch(IllegalArgumentException e) {
                        LOG.error("Unknown 802.11 disconnection monitor method [{}] for monitored network " +
                                        "[{}]. Skipping.", monitoredNetwork.discoMonitorType(), monitoredNetwork.uuid());
                        continue;
                    }

                    List<Tap> tenantTaps = nzyme.getTapManager()
                            .findAllTapsOfTenant(monitoredNetwork.organizationId(), monitoredNetwork.tenantId());

                    Map<Tap, List<DiscoMonitorAnomaly>> anomalies = DiscoMonitorFactory
                            .build(nzyme, method, monitoredNetwork)
                            .execute(tenantTaps);

                    // Trigger alerts.
                    for (Map.Entry<Tap, List<DiscoMonitorAnomaly>> anomaly : anomalies.entrySet()) {
                        Tap tap = anomaly.getKey();

                        String anomaliesAttribute;
                        try {
                            anomaliesAttribute = this.om.writeValueAsString(anomaly.getValue());
                        } catch(Exception e) {
                            throw new RuntimeException("Could not build anomalies alert parameter.", e);
                        }

                        Map<String, String> attributes = Maps.newHashMap();
                        attributes.put("anomalies", anomaliesAttribute);
                        attributes.put("tap_id", tap.uuid().toString());
                        attributes.put("tap_name", tap.name());

                        nzyme.getDetectionAlertService().raiseAlert(
                                org.uuid(),
                                tenant.uuid(),
                                monitoredNetwork.uuid(),
                                AlertOrigin.tap(tap.uuid()),
                                DetectionType.DOT11_MONITOR_DISCO_ANOMALIES,
                                Subsystem.DOT11,
                                "Detected disconnection activity anomalies for monitored " +
                                        "network \"" + monitoredNetwork.ssid() + "\" (Tap: \"" + tap.name() + "\")",
                                attributes,
                                Set.of("tap_id")
                        );
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return "802.11 Disco Monitor";
    }

}
