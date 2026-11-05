package app.nzyme.core.tables.ethernet;

import app.nzyme.core.taps.Tap;
import app.nzyme.core.assets.profile.AssetProfileBatch;
import app.nzyme.core.assets.profile.AssetAttributeDimension;
import app.nzyme.core.ethernet.tls.CertificateDetails;
import app.nzyme.core.ethernet.tls.CertificateStore;
import app.nzyme.core.ethernet.tls.JA4;
import app.nzyme.core.ethernet.tls.TlsChainValidator;
import app.nzyme.core.ethernet.tls.TlsHostnameMatcher;
import app.nzyme.core.rest.resources.taps.reports.tables.tls.TlsAlertReport;
import app.nzyme.core.rest.resources.taps.reports.tables.tls.TlsClientHelloReport;
import app.nzyme.core.rest.resources.taps.reports.tables.tls.TlsServerHelloReport;
import app.nzyme.core.rest.resources.taps.reports.tables.tls.TlsSessionReport;
import app.nzyme.core.rest.resources.taps.reports.tables.tls.TlsSessionsReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.Tools;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Types;
import java.util.*;

public class TLSTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(TLSTable.class);

    private static final List<String> VERSION_ORDER = List.of(
            "SSLv2", "SSLv3", "TLSv1.0", "TLSv1.1", "TLSv1.2", "TLSv1.3"
    );

    private final TablesService tablesService;
    private final Timer totalReportTimer;
    private final ObjectMapper om;
    private final TlsChainValidator chainValidator;

    public TLSTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();
        this.chainValidator = new TlsChainValidator();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.TLS_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, TlsSessionsReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            tablesService.getNzyme().getDatabase().useTransaction(handle -> {
                Map<String, CertificateDetails> certificates = writeCertificates(handle, timestamp, report);
                writeSessions(handle, tapUuid, report.sessions(), certificates);
            });
        }
    }

    // Returns the parsed certificates keyed by hex SHA-256, as the sessions reference them.
    private Map<String, CertificateDetails> writeCertificates(Handle handle, DateTime seenAt, TlsSessionsReport report) {
        Map<String, CertificateDetails> certificates = Maps.newHashMap();

        for (Map.Entry<String, String> entry : report.certificates().entrySet()) {
            byte[] der;
            try {
                der = Base64.getDecoder().decode(entry.getValue());
            } catch (IllegalArgumentException e) {
                LOG.warn("Skipping certificate [{}] in TLS report: Invalid base64.", entry.getKey());
                continue;
            }

            CertificateDetails details = CertificateStore.upsert(handle, der, seenAt);

            String fingerprint = BaseEncoding.base16().lowerCase().encode(details.sha256());
            if (!fingerprint.equals(entry.getKey().toLowerCase())) {
                LOG.warn("Certificate fingerprint mismatch in TLS report. Tap says [{}], DER hashes to [{}].",
                        entry.getKey(), fingerprint);
            }
            certificates.put(fingerprint, details);
        }

        return certificates;
    }

    private static final String UPSERT = "INSERT INTO tls_sessions(uuid, tap_uuid, tcp_session_key, " +
            "source_mac, destination_mac, source_address, source_port, destination_address, destination_port, " +
            "connection_status, bytes_count, server_name, version, client_max_version, cipher_suite, " +
            "alpn_selected, session_resumed, hello_retry_request, encrypted_client_hello, " +
            "client_certificate_requested, client_change_cipher_spec, server_change_cipher_spec, " +
            "client_app_data_records, client_app_data_bytes, server_app_data_records, server_app_data_bytes, " +
            "client_hello, server_hello, alerts, server_leaf_sha256, server_chain_sha256, client_leaf_sha256, " +
            "client_chain_sha256, server_leaf_self_signed, server_chain_length, sni_matches_certificate, " +
            "leaf_valid_at_handshake, chain_trusted, chain_trust_error, ja4, established_at, terminated_at, " +
            "most_recent_segment_time, updated_at, created_at) " +
            "VALUES(:uuid, :tap_uuid, :tcp_session_key, :source_mac, :destination_mac, :source_address::inet, " +
            ":source_port, :destination_address::inet, :destination_port, :connection_status, :bytes_count, " +
            ":server_name, :version, :client_max_version, :cipher_suite, :alpn_selected, :session_resumed, " +
            ":hello_retry_request, :encrypted_client_hello, :client_certificate_requested, " +
            ":client_change_cipher_spec, :server_change_cipher_spec, :client_app_data_records, " +
            ":client_app_data_bytes, :server_app_data_records, :server_app_data_bytes, :client_hello::jsonb, " +
            ":server_hello::jsonb, :alerts::jsonb, :server_leaf_sha256, :server_chain_sha256, " +
            ":client_leaf_sha256, :client_chain_sha256, :server_leaf_self_signed, :server_chain_length, " +
            ":sni_matches_certificate, :leaf_valid_at_handshake, :chain_trusted, :chain_trust_error, :ja4, " +
            ":established_at, :terminated_at, :most_recent_segment_time, NOW(), NOW()) " +
            "ON CONFLICT (tap_uuid, tcp_session_key) DO UPDATE SET " +
            "connection_status = EXCLUDED.connection_status, bytes_count = EXCLUDED.bytes_count, " +
            "server_name = EXCLUDED.server_name, version = EXCLUDED.version, " +
            "client_max_version = EXCLUDED.client_max_version, cipher_suite = EXCLUDED.cipher_suite, " +
            "alpn_selected = EXCLUDED.alpn_selected, session_resumed = EXCLUDED.session_resumed, " +
            "hello_retry_request = EXCLUDED.hello_retry_request, " +
            "encrypted_client_hello = EXCLUDED.encrypted_client_hello, " +
            "client_certificate_requested = EXCLUDED.client_certificate_requested, " +
            "client_change_cipher_spec = EXCLUDED.client_change_cipher_spec, " +
            "server_change_cipher_spec = EXCLUDED.server_change_cipher_spec, " +
            "client_app_data_records = EXCLUDED.client_app_data_records, " +
            "client_app_data_bytes = EXCLUDED.client_app_data_bytes, " +
            "server_app_data_records = EXCLUDED.server_app_data_records, " +
            "server_app_data_bytes = EXCLUDED.server_app_data_bytes, client_hello = EXCLUDED.client_hello, " +
            "server_hello = EXCLUDED.server_hello, alerts = EXCLUDED.alerts, " +
            "server_leaf_sha256 = EXCLUDED.server_leaf_sha256, server_chain_sha256 = EXCLUDED.server_chain_sha256, " +
            "client_leaf_sha256 = EXCLUDED.client_leaf_sha256, client_chain_sha256 = EXCLUDED.client_chain_sha256, " +
            "server_leaf_self_signed = EXCLUDED.server_leaf_self_signed, " +
            "server_chain_length = EXCLUDED.server_chain_length, " +
            "sni_matches_certificate = EXCLUDED.sni_matches_certificate, " +
            "leaf_valid_at_handshake = EXCLUDED.leaf_valid_at_handshake, chain_trusted = EXCLUDED.chain_trusted, " +
            "chain_trust_error = EXCLUDED.chain_trust_error, ja4 = EXCLUDED.ja4, " +
            "terminated_at = EXCLUDED.terminated_at, " +
            "most_recent_segment_time = EXCLUDED.most_recent_segment_time, updated_at = NOW() " +
            "WHERE EXCLUDED.most_recent_segment_time >= tls_sessions.most_recent_segment_time";

    private void writeSessions(Handle handle,
                               UUID tapUuid,
                               List<TlsSessionReport> sessions,
                               Map<String, CertificateDetails> certificates) {
        if (sessions.isEmpty()) {
            return;
        }

        PreparedBatch batch = handle.prepareBatch(UPSERT);

        for (TlsSessionReport session : sessions) {
            String tcpSessionKey = Tools.buildL4Key(
                    session.establishedAt(),
                    session.sourceAddress(),
                    session.destinationAddress(),
                    session.sourcePort(),
                    session.destinationPort());

            bindSession(batch, session, certificates)
                    .bind("uuid", UUID.randomUUID())
                    .bind("tap_uuid", tapUuid)
                    .bind("tcp_session_key", tcpSessionKey)
                    .bind("source_mac", session.sourceMac())
                    .bind("destination_mac", session.destinationMac())
                    .bind("source_address", session.sourceAddress())
                    .bind("source_port", session.sourcePort())
                    .bind("destination_address", session.destinationAddress())
                    .bind("destination_port", session.destinationPort())
                    .bind("established_at", session.establishedAt())
                    .add();
        }

        batch.execute();

        writeAssetProfiles(handle, tapUuid, sessions);
    }

    private void writeAssetProfiles(Handle handle, UUID tapUuid, List<TlsSessionReport> sessions) {
        Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);
        if (tap.isEmpty()) {
            return;
        }

        AssetProfileBatch profile = new AssetProfileBatch(tap.get());

        for (TlsSessionReport session : sessions) {
            DateTime timestamp = session.mostRecentSegmentTime() != null
                    ? session.mostRecentSegmentTime() : session.establishedAt();

            if (session.clientHello() != null) {
                profile.attribute(session.sourceMac(), AssetAttributeDimension.JA4,
                        JA4.calculate(session.clientHello()), timestamp);
            }

            if (session.serverHello() != null && session.serverHello().version() != null) {
                profile.attribute(session.sourceMac(), AssetAttributeDimension.TLS_VERSION,
                        session.serverHello().version(), timestamp);
            }
        }

        profile.flush(handle);
    }

    private PreparedBatch bindSession(PreparedBatch batch, TlsSessionReport s, Map<String, CertificateDetails> certificates) {
        TlsClientHelloReport ch = s.clientHello();
        String serverName = ch == null ? null : ch.serverName();

        List<CertificateDetails> serverChain = resolveChain(s.serverChainSha256(), certificates);
        CertificateDetails leaf = serverChain.isEmpty() ? null : serverChain.get(0);

        Boolean sniMatches = null;
        Boolean leafValid = null;
        Boolean leafSelfSigned = null;
        if (leaf != null && leaf.parsed()) {
            leafSelfSigned = leaf.selfSigned();
            leafValid = !leaf.notBefore().isAfter(s.establishedAt()) && !leaf.notAfter().isBefore(s.establishedAt());
            if (serverName != null) {
                sniMatches = TlsHostnameMatcher.matches(serverName, leaf);
            }
        }

        TlsChainValidator.Result trust = chainValidator.validate(serverChain);

        return batch
                .bind("connection_status", s.connectionStatus())
                .bind("bytes_count", s.bytesCount())
                .bind("server_name", serverName)
                .bind("version", s.serverHello() == null ? null : s.serverHello().version())
                .bind("client_max_version", clientMaxVersion(ch))
                .bind("cipher_suite", s.serverHello() == null ? null : s.serverHello().cipherSuite())
                .bind("alpn_selected", s.serverHello() == null ? null : s.serverHello().alpn())
                .bind("session_resumed", s.serverHello() != null && s.serverHello().sessionResumed())
                .bind("hello_retry_request", s.helloRetryRequest())
                .bind("encrypted_client_hello", ch != null && ch.encryptedClientHello())
                .bind("client_certificate_requested", s.clientCertificateRequested())
                .bind("client_change_cipher_spec", s.clientChangeCipherSpec())
                .bind("server_change_cipher_spec", s.serverChangeCipherSpec())
                .bind("client_app_data_records", s.clientApplicationDataRecords())
                .bind("client_app_data_bytes", s.clientApplicationDataBytes())
                .bind("server_app_data_records", s.serverApplicationDataRecords())
                .bind("server_app_data_bytes", s.serverApplicationDataBytes())
                .bind("client_hello", ch == null ? null : om.writeValueAsString(clientHelloJson(ch)))
                .bind("server_hello", s.serverHello() == null ? null : om.writeValueAsString(serverHelloJson(s.serverHello())))
                .bind("alerts", s.alerts().isEmpty() ? null : om.writeValueAsString(alertsJson(s.alerts())))
                .bind("server_leaf_sha256", leafHash(s.serverChainSha256()))
                .bindBySqlType("server_chain_sha256", chainHashes(s.serverChainSha256()), Types.ARRAY)
                .bind("client_leaf_sha256", leafHash(s.clientChainSha256()))
                .bindBySqlType("client_chain_sha256", chainHashes(s.clientChainSha256()), Types.ARRAY)
                .bind("server_leaf_self_signed", leafSelfSigned)
                .bind("server_chain_length", serverChain.isEmpty() ? null : (short) serverChain.size())
                .bind("sni_matches_certificate", sniMatches)
                .bind("leaf_valid_at_handshake", leafValid)
                .bind("chain_trusted", trust.trusted())
                .bind("chain_trust_error", trust.error())
                .bind("ja4", JA4.calculate(ch))
                .bind("terminated_at", s.terminatedAt())
                .bind("most_recent_segment_time", s.mostRecentSegmentTime());
    }

    private ObjectNode clientHelloJson(TlsClientHelloReport ch) {
        ObjectNode node = om.createObjectNode();
        node.put("record_version", ch.recordVersion());
        node.put("legacy_version", ch.legacyVersion());
        putStrings(node.putArray("supported_versions"), ch.supportedVersions());
        putInts(node.putArray("cipher_suites"), ch.cipherSuites());
        putInts(node.putArray("extensions"), ch.extensions());
        putInts(node.putArray("supported_groups"), ch.supportedGroups());
        putInts(node.putArray("ec_point_formats"), ch.ecPointFormats());
        putInts(node.putArray("signature_algorithms"), ch.signatureAlgorithms());
        putStrings(node.putArray("alpn"), ch.alpn());
        node.put("session_id_present", ch.sessionIdPresent());
        node.put("offers_session_ticket", ch.offersSessionTicket());
        node.put("offers_pre_shared_key", ch.offersPreSharedKey());
        return node;
    }

    private ObjectNode serverHelloJson(TlsServerHelloReport sh) {
        ObjectNode node = om.createObjectNode();
        node.put("record_version", sh.recordVersion());
        node.put("legacy_version", sh.legacyVersion());
        node.put("compression_method", sh.compressionMethod());
        putInts(node.putArray("extensions"), sh.extensions());
        return node;
    }

    private ArrayNode alertsJson(List<TlsAlertReport> alerts) {
        ArrayNode array = om.createArrayNode();
        for (TlsAlertReport alert : alerts) {
            ObjectNode node = array.addObject();
            node.put("direction", alert.direction());
            node.put("level", alert.level());
            node.put("description", alert.description());
            node.put("description_name", alert.descriptionName());
            node.put("encrypted", alert.encrypted());
        }
        return array;
    }

    private static void putStrings(ArrayNode array, List<String> values) {
        values.forEach(array::add);
    }

    private static void putInts(ArrayNode array, List<Integer> values) {
        values.forEach(array::add);
    }

    private static List<CertificateDetails> resolveChain(List<String> hashes, Map<String, CertificateDetails> certificates) {
        List<CertificateDetails> chain = Lists.newArrayList();
        for (String hash : hashes) {
            CertificateDetails details = certificates.get(hash.toLowerCase());
            if (details == null) {
                // The tap always ships every referenced certificate. Treat a gap as no chain.
                LOG.warn("TLS report references certificate [{}] that it did not ship.", hash);
                return List.of();
            }
            chain.add(details);
        }
        return chain;
    }

    private static byte[] leafHash(List<String> hashes) {
        return hashes.isEmpty() ? null : BaseEncoding.base16().lowerCase().decode(hashes.get(0).toLowerCase());
    }

    private static byte[][] chainHashes(List<String> hashes) {
        if (hashes.isEmpty()) {
            return null;
        }

        byte[][] result = new byte[hashes.size()][];
        for (int i = 0; i < hashes.size(); i++) {
            result[i] = BaseEncoding.base16().lowerCase().decode(hashes.get(i).toLowerCase());
        }
        return result;
    }

    private static String clientMaxVersion(TlsClientHelloReport ch) {
        if (ch == null) {
            return null;
        }

        String max = ch.legacyVersion();
        for (String v : ch.supportedVersions()) {
            if (VERSION_ORDER.indexOf(v) > VERSION_ORDER.indexOf(max)) {
                max = v;
            }
        }
        return max;
    }

    @Override
    public void retentionClean() {
        // NOOP. Database cleaned by category/tenant independently.
    }

}
