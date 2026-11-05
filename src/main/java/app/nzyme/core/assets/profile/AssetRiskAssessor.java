package app.nzyme.core.assets.profile;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.*;

public class AssetRiskAssessor {

    public enum Severity { CRITICAL, WARNING, NOTICE }

    public record RiskSignal(String signal, String severity, long count, @Nullable String detail,
                             @Nullable DateTime lastSeen) {}

    // The certificate signals only see TLS 1.2 and older: TLS 1.3 encrypts the certificate handshake.
    public record Assessment(List<RiskSignal> signals, AssetProfiles.TlsSummary tls) {}

    private static final Set<String> LEGACY_TLS_VERSIONS = Set.of("SSLv2", "SSLv3", "TLSv1.0", "TLSv1.1");

    private static final Set<String> ACTOR_KEYS = Set.of(
            "source_mac", "spoofer_mac", "sender_mac", "responder_mac", "client_mac", "server_mac"
    );
    private static final Set<String> VICTIM_KEYS = Set.of("owner_mac", "target_mac", "destination_mac");

    private final NzymeNode nzyme;

    public AssetRiskAssessor(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public static String alertRole(List<String> attributeKeys) {
        boolean actor = false;
        boolean victim = false;
        for (String key : attributeKeys) {
            if (ACTOR_KEYS.contains(key)) {
                actor = true;
            }
            if (VICTIM_KEYS.contains(key)) {
                victim = true;
            }
        }

        if (actor && victim) {
            return "ACTOR_AND_VICTIM";
        }
        if (actor) {
            return "ACTOR";
        }
        if (victim) {
            return "VICTIM";
        }

        return "SUBJECT";
    }

    public Assessment assess(AssetEntry asset, TimeRange timeRange) {
        AssetProfiles profiles = nzyme.getAssetsManager().profiles();
        UUID organizationId = asset.organizationId();
        UUID tenantId = asset.tenantId();
        String mac = asset.mac();

        List<RiskSignal> signals = new ArrayList<>();

        long plaintextHttp = 0, plaintextRtsp = 0, legacyTls = 0;
        DateTime plaintextHttpSeen = null, plaintextRtspSeen = null, legacyTlsSeen = null;
        Set<String> legacyVersions = new TreeSet<>();
        for (AssetProfiles.AttributeEntry a : profiles.attributes(organizationId, tenantId, mac)) {
            if (a.dimension().equals(AssetAttributeDimension.PROTOCOL_CLIENT.name())) {
                if (a.value().equals("HTTP")) {
                    plaintextHttp += a.count();
                    plaintextHttpSeen = later(plaintextHttpSeen, a.lastSeen());
                } else if (a.value().equals("RTSP")) {
                    plaintextRtsp += a.count();
                    plaintextRtspSeen = later(plaintextRtspSeen, a.lastSeen());
                }
            } else if (a.dimension().equals(AssetAttributeDimension.TLS_VERSION.name())
                    && LEGACY_TLS_VERSIONS.contains(a.value())) {
                legacyTls += a.count();
                legacyVersions.add(a.value());
                legacyTlsSeen = later(legacyTlsSeen, a.lastSeen());
            }
        }

        if (plaintextHttp > 0) {
            signals.add(new RiskSignal("PLAINTEXT_HTTP", Severity.WARNING.name(), plaintextHttp, null, plaintextHttpSeen));
        }
        if (plaintextRtsp > 0) {
            signals.add(new RiskSignal("PLAINTEXT_RTSP", Severity.WARNING.name(), plaintextRtsp, null, plaintextRtspSeen));
        }
        if (legacyTls > 0) {
            signals.add(new RiskSignal("LEGACY_TLS", Severity.WARNING.name(), legacyTls,
                    String.join(", ", legacyVersions), legacyTlsSeen));
        }

        AssetProfiles.TlsSummary tls = profiles.tls(organizationId, tenantId, mac, timeRange);
        if (tls.selfSignedServers() > 0) {
            signals.add(new RiskSignal("SELF_SIGNED_TLS_SERVERS", Severity.NOTICE.name(), tls.selfSignedServers(), null, null));
        }
        if (tls.untrustedChains() > 0) {
            signals.add(new RiskSignal("UNTRUSTED_TLS_CHAINS", Severity.WARNING.name(), tls.untrustedChains(), null, null));
        }

        long inboundExternal = 0;
        DateTime inboundExternalSeen = null;
        Set<String> inboundCountries = new TreeSet<>();
        for (AssetProfiles.CommunicationEdge e : profiles.communication(organizationId, tenantId, mac, timeRange, 2000)) {
            if (e.direction().equals("INBOUND") && e.peerType().equals("EXTERNAL")) {
                inboundExternal += e.sessions();
                inboundExternalSeen = later(inboundExternalSeen, e.lastSeen());
                if (e.peerCountryCode() != null) {
                    inboundCountries.add(e.peerCountryCode());
                }
            }
        }
        if (inboundExternal > 0) {
            signals.add(new RiskSignal("INBOUND_EXTERNAL_SESSIONS", Severity.WARNING.name(), inboundExternal,
                    inboundCountries.isEmpty() ? null : String.join(", ", inboundCountries), inboundExternalSeen));
        }

        for (AssetProfiles.FindingSummary f : profiles.findings(organizationId, tenantId, mac)) {
            if (!f.role().equals("ACTOR")) {
                continue;
            }

            switch (f.type()) {
                case "DNS_TUNNEL" -> signals.add(new RiskSignal("DNS_TUNNEL", Severity.CRITICAL.name(), f.count(), null, f.lastSeen()));
                case "ICMP_TUNNEL" -> signals.add(new RiskSignal("ICMP_TUNNEL", Severity.CRITICAL.name(), f.count(), null, f.lastSeen()));
                case "BEACON_MALICIOUS" -> signals.add(new RiskSignal("BEACON_MALICIOUS", Severity.CRITICAL.name(), f.count(), null, f.lastSeen()));
                case "BEACON_SUSPICIOUS" -> signals.add(new RiskSignal("BEACON_SUSPICIOUS", Severity.WARNING.name(), f.count(), null, f.lastSeen()));
                case "BEACON_UNKNOWN" -> signals.add(new RiskSignal("BEACON_UNCLASSIFIED", Severity.NOTICE.name(), f.count(), null, f.lastSeen()));
                case "RECON_SCAN" -> signals.add(new RiskSignal("RECON_SCANNER", Severity.WARNING.name(), f.count(), null, f.lastSeen()));
                case "ICMP_REDIRECT" -> signals.add(new RiskSignal("ICMP_REDIRECT_SENDER", Severity.WARNING.name(), f.count(), null, f.lastSeen()));
                case "ICMP_TRACEROUTE" -> signals.add(new RiskSignal("TRACEROUTE_SOURCE", Severity.NOTICE.name(), f.count(), null, f.lastSeen()));
                case "SPOOFING_ARP", "SPOOFING_DHCP", "SPOOFING_DNS", "SPOOFING_NAME_RESOLUTION" ->
                        signals.add(new RiskSignal(f.type() + "_ACTOR", Severity.CRITICAL.name(), f.count(), null, f.lastSeen()));
                default -> { }
            }
        }

        long activeAlerts = 0;
        DateTime activeAlertSeen = null;
        for (AssetProfiles.AlertSummary a : profiles.alerts(organizationId, tenantId, mac)) {
            if (a.activeCount() > 0) {
                activeAlerts += a.activeCount();
                activeAlertSeen = later(activeAlertSeen, a.lastSeen());
            }
        }
        if (activeAlerts > 0) {
            signals.add(new RiskSignal("ACTIVE_ALERTS", Severity.CRITICAL.name(), activeAlerts, null, activeAlertSeen));
        }

        if (Tools.macAddressIsRandomized(mac)) {
            signals.add(new RiskSignal("RANDOMIZED_MAC", Severity.NOTICE.name(), 1, null, null));
        }
        if (nzyme.getContextService().findMacAddressContext(mac, organizationId, tenantId).isEmpty()) {
            signals.add(new RiskSignal("NO_CONTEXT", Severity.NOTICE.name(), 1, null, null));
        }

        signals.sort(Comparator.comparing(s -> Severity.valueOf(s.severity()).ordinal()));

        return new Assessment(signals, tls);
    }

    @Nullable
    private static DateTime later(@Nullable DateTime a, @Nullable DateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return b.isAfter(a) ? b : a;
    }

}
