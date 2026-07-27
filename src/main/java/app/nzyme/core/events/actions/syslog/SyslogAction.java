package app.nzyme.core.events.actions.syslog;

import app.nzyme.core.events.actions.Action;
import app.nzyme.core.events.actions.ActionExecutionResult;
import app.nzyme.core.events.types.DetectionEvent;
import app.nzyme.core.events.types.SystemEvent;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SyslogAction implements Action {

    private static final Logger LOG = LogManager.getLogger(SyslogAction.class);

    private final String protocol;
    private final String syslogHostname;
    private final String host;
    private final int port;

    public SyslogAction(SyslogActionConfiguration configuration) {
        this.protocol = configuration.protocol();
        this.syslogHostname = configuration.syslogHostname();
        this.host = configuration.host();
        this.port = configuration.port();
    }

    @Override
    public ActionExecutionResult execute(SystemEvent event) {
        LOG.info("Executing [{}] for event type [{}].", this.getClass().getCanonicalName(), event.type());

        Map<String, Object> payload = Maps.newHashMap();
        payload.put("timestamp", event.timestamp().toString());
        payload.put("event_supertype", "system");
        payload.put("event_type", event.type().name());
        payload.put("event_type_category", event.type().getCategory());
        payload.put("event_type_human_readable", event.type().getHumanReadableName());

        return execute(event.timestamp(), payload, "system", event.details());
    }

    @Override
    public ActionExecutionResult execute(DetectionEvent event) {
        LOG.info("Executing [{}] for event type [{}].", this.getClass().getCanonicalName(), event.detectionType());

        Map<String, Object> payload = Maps.newHashMap();
        payload.put("timestamp", event.timestamp().toString());
        payload.put("event_supertype", "detection");
        payload.put("event_type", event.detectionType().name());
        payload.put("event_type_subsystem", event.detectionType().getSubsystem().name());
        payload.put("event_type_human_readable", event.detectionType().getTitle());

        return execute(event.timestamp(), payload, "detection", event.details());
    }

    private ActionExecutionResult execute(DateTime timestamp, Map<String, Object> payload, String eventIdentifier, String messageDetails) {
        if (!protocol.equals("UDP_RFC5424")) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }

        if (messageDetails.isEmpty()) {
            throw new IllegalArgumentException("Message details are empty");
        }

        int facility = 16; // Local0.
        int severity = 5;  // Notice.
        int pri = facility * 8 + severity;

        String version = "1";
        String appName = "nzyme";
        String procId = "-";
        String msgId = "-";

        // Structured data.
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(eventIdentifier).append("@63705");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = sanitizeSDName(entry.getKey());
            String value = sanitizeSDValue(entry.getValue().toString());
            sb.append(" ").append(key).append("=\"").append(value).append("\"");
        }
        sb.append("]");
        String structuredData = sb.toString();

        // Assemble header.
        String header = String.format("<%d>%s %s %s %s %s %s %s ",
                pri,
                version,
                timestamp.toString(),
                sanitizeHostname(syslogHostname),
                appName,
                procId,
                msgId,
                structuredData
        );
        String fullMessage = header + messageDetails;

        // Send via UDP.
        try {
            byte[] data = fullMessage.getBytes(StandardCharsets.UTF_8);
            InetAddress serverAddr = InetAddress.getByName(host);

            try (DatagramSocket socket = new DatagramSocket()) {
                DatagramPacket packet = new DatagramPacket(data, data.length, serverAddr, port);
                socket.send(packet);
            }
        } catch (Exception e) {
            LOG.error("Could not send syslog action message.", e);
            return ActionExecutionResult.FAILURE;
        }

        return ActionExecutionResult.SUCCESS;
    }

    private String sanitizeSDName(String name) {
        return name.replaceAll("[\\]\\[\\s=\"]", "_")
                .substring(0, Math.min(name.length(), 32));
    }

    private String sanitizeSDValue(String val) {
        return val.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("]", "\\]");
    }

    public static String sanitizeHostname(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return "-";
        }

        String replaced = hostname.replaceAll("[^A-Za-z0-9\\-\\.]", "_");

        if (replaced.length() > 255) {
            return replaced.substring(0, 255);
        }

        return replaced;
    }

}
