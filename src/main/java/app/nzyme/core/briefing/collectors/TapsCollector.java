package app.nzyme.core.briefing.collectors;

import app.nzyme.core.briefing.BriefingContext;
import app.nzyme.core.briefing.BriefingItems;
import app.nzyme.core.briefing.BriefingLinks;
import app.nzyme.core.briefing.BriefingSeverity;
import app.nzyme.core.rest.responses.briefing.BriefingAttentionItemResponse;
import app.nzyme.core.rest.responses.briefing.BriefingCheckResponse;
import app.nzyme.core.rest.responses.briefing.BriefingTapCaptureResponse;
import app.nzyme.core.rest.responses.briefing.BriefingHistogramBucketResponse;
import app.nzyme.core.rest.responses.briefing.BriefingTapResponse;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.taps.Capture;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Tools;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.briefing.BriefingItems.plural;

public class TapsCollector {

    private final BriefingContext ctx;

    private List<BriefingTapResponse> taps;

    public TapsCollector(BriefingContext ctx) {
        this.ctx = ctx;
    }

    public TapsCollector load() {
        taps = Lists.newArrayList();

        for (UUID tapId : ctx.taps()) {
            Optional<Tap> tap = ctx.nzyme().getTapManager().findTap(tapId);
            if (tap.isEmpty()) {
                continue;
            }

            List<BriefingTapCaptureResponse> captures = Lists.newArrayList();
            for (Capture capture : ctx.nzyme().getTapManager().findActiveCapturesOfTap(tapId)) {
                long received = capture.received() == null ? 0 : capture.received();
                long dropped = (capture.droppedBuffer() == null ? 0 : capture.droppedBuffer())
                        + (capture.droppedInterface() == null ? 0 : capture.droppedInterface());
                double ratio = received + dropped == 0 ? 0 : (double) dropped / (double) (received + dropped);

                captures.add(BriefingTapCaptureResponse.create(
                        capture.interfaceName(), capture.captureType(),
                        capture.isRunning() != null && capture.isRunning(), received, dropped, ratio
                ));
            }

            String locationName = null;
            if (tap.get().locationId() != null) {
                Optional<TenantLocationEntry> location = ctx.nzyme().getAuthenticationService()
                        .findTenantLocation(tap.get().locationId(), ctx.organizationId(), ctx.tenantId());
                if (location.isPresent()) {
                    locationName = location.get().name();
                }
            }

            long activeAlerts = ctx.canViewAlerts()
                    ? ctx.nzyme().getDetectionAlertService().countActiveAlertsOfTap(ctx.organizationId(), ctx.tenantId(), tapId)
                    : 0;

            taps.add(BriefingTapResponse.create(
                    tap.get().uuid(),
                    tap.get().name(),
                    locationName,
                    Tools.isTapActive(tap.get().lastReport()),
                    tap.get().lastReport(),
                    tap.get().version(),
                    tap.get().clockDriftMs(),
                    activeAlerts,
                    captures
            ));
        }

        taps.sort((a, b) -> {
            if (a.online() != b.online()) {
                return a.online() ? 1 : -1;
            }
            return a.name().compareToIgnoreCase(b.name());
        });

        return this;
    }

    public List<BriefingTapResponse> taps() {
        return taps;
    }

    public long online() {
        return taps.stream().filter(BriefingTapResponse::online).count();
    }

    public long total() {
        return taps.size();
    }

    // Configured, not necessarily running. Subsystems are enabled by default, so this says if one is in use.
    public boolean capturesType(String captureType) {
        return taps.stream().anyMatch(t -> t.captures().stream()
                .anyMatch(c -> c.captureType().equalsIgnoreCase(captureType)));
    }

    public boolean hasCaptureOfType(BriefingTapResponse tap, String captureType) {
        return tap.captures().stream().anyMatch(c -> c.captureType().equalsIgnoreCase(captureType) && c.isRunning());
    }

    public List<BriefingTapResponse> tapsWithoutCaptureOfType(String captureType) {
        return taps.stream().filter(t -> t.online() && !hasCaptureOfType(t, captureType)).toList();
    }

    // Offline, drops, buffers and clock drift are health indicators already. Only stopped captures are left.
    public List<BriefingAttentionItemResponse> attention() {
        List<BriefingAttentionItemResponse> items = Lists.newArrayList();

        for (BriefingTapResponse tap : taps) {
            if (!tap.online()) {
                continue;
            }

            for (BriefingTapCaptureResponse capture : tap.captures()) {
                if (!capture.isRunning()) {
                    items.add(BriefingItems.attention(BriefingSeverity.ORANGE, "tap", null,
                            "Capture " + capture.interfaceName() + " on \"" + tap.name() + "\" is not running",
                            "The " + capture.captureType() + " capture stopped. The tap is online but blind on this interface.",
                            1, null, BriefingLinks.to("TAP", "uuid", tap.uuid().toString())));
                }
            }
        }

        return items;
    }

    public List<BriefingCheckResponse> checks() {
        List<BriefingCheckResponse> checks = Lists.newArrayList();

        if (taps.isEmpty()) {
            checks.add(BriefingItems.warn("taps_selected", "No taps in this tenant",
                    "This briefing has no data source until a tap reports for this tenant.",
                    BriefingLinks.to("TAPS")));
            return checks;
        }

        long online = online();
        if (online == taps.size()) {
            checks.add(BriefingItems.ok("taps_online", online + " of " + taps.size() + " "
                    + plural(taps.size(), "tap", "taps") + " reporting", null, BriefingLinks.to("TAPS")));
        } else {
            checks.add(BriefingItems.warn("taps_online", online + " of " + taps.size() + " "
                    + plural(taps.size(), "tap", "taps") + " reporting",
                    (taps.size() - online) + " offline.", BriefingLinks.to("TAPS")));
        }

        return checks;
    }

    public List<BriefingHistogramBucketResponse> throughputHistogram(TimeRange range, String dateTrunc) {
        if (ctx.taps().isEmpty()) {
            return List.of();
        }

        return ctx.nzyme().getDatabase().withHandle(handle ->
                handle.createQuery("SELECT bucket, SUM(value) AS value FROM (" +
                                "SELECT tap_uuid, DATE_TRUNC(:date_trunc, created_at) AS bucket, AVG(metric_value) AS value " +
                                "FROM tap_metrics_gauges WHERE metric_name = 'system.captures.throughput_bit_sec' " +
                                "AND tap_uuid IN (<taps>) AND created_at >= :from AND created_at <= :to " +
                                "GROUP BY tap_uuid, bucket) AS per_tap GROUP BY bucket ORDER BY bucket")
                        .bind("date_trunc", dateTrunc)
                        .bindList("taps", ctx.taps())
                        .bind("from", range.from())
                        .bind("to", range.to())
                        .map((rs, c) -> BriefingHistogramBucketResponse.create(
                                new DateTime(rs.getTimestamp("bucket")), rs.getDouble("value")))
                        .list()
        );
    }

    public static String describeAge(@Nullable DateTime timestamp) {
        if (timestamp == null) {
            return "never";
        }

        long minutes = (DateTime.now().getMillis() - timestamp.getMillis()) / 60000;
        if (minutes < 1) {
            return "moments ago";
        }
        if (minutes < 60) {
            return minutes + " " + plural(minutes, "minute", "minutes") + " ago";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + " " + plural(hours, "hour", "hours") + " ago";
        }
        long days = hours / 24;
        return days + " days ago";
    }

}
