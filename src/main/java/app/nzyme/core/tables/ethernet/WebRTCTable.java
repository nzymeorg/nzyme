package app.nzyme.core.tables.ethernet;

import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.webrtc.db.WebRTCRTPStreamData;
import app.nzyme.core.rest.resources.taps.reports.tables.webrtc.WebRTCConversationReport;
import app.nzyme.core.rest.resources.taps.reports.tables.webrtc.WebRTCConversationsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.webrtc.WebRTCRTPStreamReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.Tools;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WebRTCTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(WebRTCTable.class);

    private final TablesService tablesService;
    private final ObjectMapper om;

    private final Timer totalReportTimer;
    private final Timer conversationsTimer;

    public WebRTCTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.WEBRTC_TOTAL_REPORT_PROCESSING_TIMER);
        this.conversationsTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.WEBRTC_CONVERSATIONS_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, WebRTCConversationsReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            tablesService.getProcessorPool().submit(() -> {
                tablesService.getNzyme().getDatabase().useHandle(handle -> {
                    try (Timer.Context ignored2 = conversationsTimer.time()) {
                        writeConversations(handle, tapUuid, timestamp, report.conversations());
                    }
                });
            });
        }
    }

    private void writeConversations(Handle handle,
                                    UUID tapUuid,
                                    DateTime timestamp,
                                    List<WebRTCConversationReport> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return;
        }

        Map<String, WebRTCConversationReport> deduped = new LinkedHashMap<>();
        for (WebRTCConversationReport c : conversations) {
            String sessionKey = Tools.buildL4Key(
                    c.firstSeen(),
                    c.sourceAddress(),
                    c.destinationAddress(),
                    c.sourcePort(),
                    c.destinationPort()
            );
            String dedupeKey = sessionKey + "|" + c.firstSeen().getMillis();
            deduped.merge(dedupeKey, c, (a, b) ->
                    b.lastActivity().isAfter(a.lastActivity()) ? b : a);
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO webrtc_conversations(uuid, " +
                "tap_uuid, l4_session_key, transport, negotiation_key, negotiation_key_sha256, " +
                "has_rtp, has_dtls, has_audio, has_video, stream_count, " +
                "rtp_streams, dtls_app_data_records, " +
                "first_seen, last_activity, updated_at, created_at) " +
                "VALUES(:uuid, :tap_uuid, :l4_session_key, :transport, :negotiation_key, " +
                ":negotiation_key_sha256, :has_rtp, :has_dtls, :has_audio, :has_video, :stream_count, " +
                ":rtp_streams::jsonb, :dtls_app_data_records, " +
                ":first_seen, :last_activity, NOW(), NOW()) " +
                "ON CONFLICT (tap_uuid, l4_session_key, first_seen) DO UPDATE SET " +
                "has_rtp = webrtc_conversations.has_rtp OR EXCLUDED.has_rtp, " +
                "has_dtls = webrtc_conversations.has_dtls OR EXCLUDED.has_dtls, " +
                "has_audio = webrtc_conversations.has_audio OR EXCLUDED.has_audio, " +
                "has_video = webrtc_conversations.has_video OR EXCLUDED.has_video, " +
                "stream_count = GREATEST(webrtc_conversations.stream_count, EXCLUDED.stream_count), " +
                "rtp_streams = EXCLUDED.rtp_streams, " +
                "dtls_app_data_records = EXCLUDED.dtls_app_data_records, " +
                "last_activity = EXCLUDED.last_activity, updated_at = NOW() " +
                // Skip the write when the report carries nothing new.
                "WHERE (webrtc_conversations.has_rtp, webrtc_conversations.has_dtls, " +
                "webrtc_conversations.has_audio, webrtc_conversations.has_video, " +
                "webrtc_conversations.stream_count, webrtc_conversations.rtp_streams, " +
                "webrtc_conversations.dtls_app_data_records, webrtc_conversations.last_activity) " +
                "IS DISTINCT FROM (webrtc_conversations.has_rtp OR EXCLUDED.has_rtp, " +
                "webrtc_conversations.has_dtls OR EXCLUDED.has_dtls, " +
                "webrtc_conversations.has_audio OR EXCLUDED.has_audio, " +
                "webrtc_conversations.has_video OR EXCLUDED.has_video, " +
                "GREATEST(webrtc_conversations.stream_count, EXCLUDED.stream_count), EXCLUDED.rtp_streams, " +
                "EXCLUDED.dtls_app_data_records, EXCLUDED.last_activity)");

        for (WebRTCConversationReport c : deduped.values()) {
            try {
                String sessionKey = Tools.buildL4Key(
                        c.firstSeen(),
                        c.sourceAddress(),
                        c.destinationAddress(),
                        c.sourcePort(),
                        c.destinationPort()
                );

                String negotiationKeySha256 = Hashing.sha256()
                        .hashString(c.negotiationKey(), StandardCharsets.UTF_8)
                        .toString();

                List<WebRTCRTPStreamData> rtpStreamsData = Lists.newArrayList();
                for (WebRTCRTPStreamReport s : c.rtpStreams()) {
                    rtpStreamsData.add(WebRTCRTPStreamData.create(
                            s.ssrc(),
                            s.direction(),
                            s.payloadTypes(),
                            s.packetCount(),
                            s.byteCount(),
                            s.mediaKind(),
                            s.estimatedLost(),
                            s.sequenceMin(),
                            s.sequenceMax(),
                            s.timestampSpan(),
                            s.markerCount()
                    ));
                }

                batch
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tapUuid)
                        .bind("l4_session_key", sessionKey)
                        .bind("transport", L4Type.UDP)
                        .bind("negotiation_key", c.negotiationKey())
                        .bind("negotiation_key_sha256", negotiationKeySha256)
                        .bind("has_rtp", c.hasRtp())
                        .bind("has_dtls", c.hasDtls())
                        .bind("has_audio", c.hasAudio())
                        .bind("has_video", c.hasVideo())
                        .bind("stream_count", c.streamCount())
                        .bind("rtp_streams", om.writeValueAsString(rtpStreamsData))
                        .bind("dtls_app_data_records", c.dtlsAppDataRecords())
                        .bind("first_seen", c.firstSeen())
                        .bind("last_activity", c.lastActivity())
                        .add();
            } catch (Exception e) {
                LOG.error("Could not prepare WebRTC conversation for write.", e);
            }
        }

        try {
            batch.execute();
        } catch (Exception e) {
            LOG.error("Could not write WebRTC conversations.", e);
        }
    }

    @Override
    public void retentionClean() {
        // NOOP
    }
}