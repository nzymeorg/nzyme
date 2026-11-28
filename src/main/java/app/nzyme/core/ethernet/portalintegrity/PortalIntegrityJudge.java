package app.nzyme.core.ethernet.portalintegrity;

import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityControlUrlEntry;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class PortalIntegrityJudge {

    public static final int MAX_ERROR_LENGTH = 1024;

    private static final int STALE_AFTER_SECONDS_MINIMUM = 15 * 60;
    private static final int STALE_AFTER_SECONDS_MAXIMUM_FROM_REPORTS = 24 * 60 * 60;
    private static final int STALE_AFTER_SECONDS_UNKNOWN_INTERVAL = 60 * 60;

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    public enum Reason {
        URL,
        ADDRESS,
        CERTIFICATE
    }

    public record Hop(String url, int status, @Nullable String resolvedAddress, @Nullable String certificate,
                      byte[] raw) {}

    // What one report showed. Without an error, the three final values are of the last hop.
    public record Observation(@Nullable String error,
                              int hopCount,
                              @Nullable Boolean portalPresented,
                              @Nullable String finalUrl,
                              @Nullable String finalAddress,
                              @Nullable String finalCertificate) {}

    public record Judgement(PortalIntegrityResult result, Set<Reason> reasons) {

        static Judgement of(PortalIntegrityResult result) {
            return new Judgement(result, Set.of());
        }

    }

    // Where a control URL stands, with what the tap says about running its probe. Null if the tap did not say.
    public record Status(PortalIntegrityResult result,
                         Set<Reason> reasons,
                         boolean stale,
                         @Nullable Boolean inTapConfiguration,
                         int staleAfterSeconds) {}

    private PortalIntegrityJudge() {}

    public static Observation observe(String controlUrl, @Nullable String error, List<Hop> hops) {
        if (error != null || hops.isEmpty()) {
            // The error of a tap quotes bytes of the response.
            String reason = error == null ? "The probe recorded no response."
                    : CONTROL_CHARACTERS.matcher(error).replaceAll(" ");

            return new Observation(
                    reason.length() > MAX_ERROR_LENGTH ? reason.substring(0, MAX_ERROR_LENGTH) : reason,
                    hops.size(), null, null, null, null
            );
        }

        Hop last = hops.get(hops.size() - 1);

        return new Observation(
                null,
                hops.size(),
                presentsPortal(controlUrl, hops),
                PortalUrls.withoutQuery(last.url()),
                PortalUrls.normalizeAddress(last.resolvedAddress()).orElse(null),
                PortalUrls.normalizeCertificate(last.certificate()).orElse(null)
        );
    }

    public static Judgement judge(PortalIntegrityControlUrlEntry entry) {
        return judge(
                entry.expectation(),
                entry.expectedFinalUrls(),
                entry.expectedAddresses(),
                entry.expectedCertificates(),
                new Observation(entry.lastError(), entry.lastHopCount(), entry.lastPortalPresented(),
                        entry.lastFinalUrl(), entry.lastFinalAddress(), entry.lastFinalCertificate())
        );
    }

    public static Judgement judge(PortalExpectation expectation,
                                  List<String> expectedFinalUrls,
                                  List<String> expectedAddresses,
                                  List<String> expectedCertificates,
                                  Observation observation) {
        if (expectation == PortalExpectation.NOT_MONITORED) {
            return Judgement.of(PortalIntegrityResult.NOT_MONITORED);
        }

        if (observation.error() != null || observation.portalPresented() == null) {
            return Judgement.of(PortalIntegrityResult.REQUEST_FAILED);
        }

        if (expectation == PortalExpectation.NO_PORTAL) {
            return Judgement.of(observation.portalPresented()
                    ? PortalIntegrityResult.PORTAL_UNEXPECTED : PortalIntegrityResult.AS_EXPECTED);
        }

        if (!observation.portalPresented()) {
            return Judgement.of(PortalIntegrityResult.PORTAL_MISSING);
        }

        Set<Reason> reasons = EnumSet.noneOf(Reason.class);
        if (!contains(expectedFinalUrls, observation.finalUrl())) {
            reasons.add(Reason.URL);
        }
        // An empty list means that the property is not checked.
        if (!expectedAddresses.isEmpty() && !contains(expectedAddresses, observation.finalAddress())) {
            reasons.add(Reason.ADDRESS);
        }
        /*
         * Only a page that presented a certificate can present the wrong one. An expected portal without TLS
         * next to ones with TLS stays expected, and a portal that lost its TLS has another final URL.
         */
        if (!expectedCertificates.isEmpty() && observation.finalCertificate() != null
                && !contains(expectedCertificates, observation.finalCertificate())) {
            reasons.add(Reason.CERTIFICATE);
        }

        return reasons.isEmpty() ? Judgement.of(PortalIntegrityResult.AS_EXPECTED)
                : new Judgement(PortalIntegrityResult.PORTAL_WRONG, reasons);
    }

    /*
     * Reports that keep coming are judged whatever the tap configuration says. Only once they stop does it
     * matter whether the tap still runs the probe: if it does, or did not say, the probe stopped reporting.
     */
    public static Status status(PortalIntegrityControlUrlEntry entry, PortalProbeConfigurations probes, DateTime now) {
        Boolean inTapConfiguration = probes
                .requests(entry.tapUuid(), entry.probeName(), entry.controlUrl(), entry.ipVersion())
                .orElse(null);
        int staleAfter = staleAfterSeconds(
                entry.reportIntervalSeconds(),
                probes.intervalSeconds(entry.tapUuid(), entry.probeName()).orElse(null)
        );
        boolean stale = entry.lastReportAt().plusSeconds(staleAfter).isBefore(now);

        PortalIntegrityResult result;
        Set<Reason> reasons = Set.of();
        if (entry.expectation() == PortalExpectation.NOT_MONITORED) {
            result = PortalIntegrityResult.NOT_MONITORED;
        } else if (stale) {
            result = Boolean.FALSE.equals(inTapConfiguration)
                    ? PortalIntegrityResult.NOT_IN_TAP_CONFIGURATION : PortalIntegrityResult.NOT_REPORTING;
        } else {
            Judgement judgement = judge(entry);
            result = judgement.result();
            reasons = judgement.reasons();
        }

        return new Status(result, reasons, stale, inTapConfiguration, staleAfter);
    }

    /*
     * A control URL that missed about three cycles says nothing about the network any more. The interval is the
     * configured one. The gap between the last two reports only stands in for a tap that did not say which probes
     * it runs: a gap is also what an outage leaves behind, and would make the next silence go unnoticed for long.
     */
    public static int staleAfterSeconds(@Nullable Integer reportIntervalSeconds,
                                        @Nullable Integer configuredIntervalSeconds) {
        if (configuredIntervalSeconds != null) {
            return (int) Math.max(STALE_AFTER_SECONDS_MINIMUM, 3L * configuredIntervalSeconds + 300);
        }

        if (reportIntervalSeconds == null) {
            return STALE_AFTER_SECONDS_UNKNOWN_INTERVAL;
        }

        return (int) Math.max(STALE_AFTER_SECONDS_MINIMUM,
                Math.min(STALE_AFTER_SECONDS_MAXIMUM_FROM_REPORTS, 3L * reportIntervalSeconds + 300));
    }

    // The lists can be immutable ones, which refuse to be asked for null.
    private static boolean contains(List<String> expected, @Nullable String value) {
        return value != null && expected.contains(value);
    }

    private static boolean presentsPortal(String controlUrl, List<Hop> hops) {
        Optional<ConnectivityChecks.CleanResponse> clean = ConnectivityChecks.of(controlUrl);
        if (clean.isPresent()) {
            if (hops.size() != 1) {
                return true;
            }

            // The response is whatever the network sent. One that cannot be read is not the clean answer.
            try {
                return !clean.get().matches(hops.get(0).status(), hops.get(0).raw());
            } catch (RuntimeException e) {
                return true;
            }
        }

        // Without a known answer, a request that stayed on the site of the control URL was not intercepted.
        Optional<String> controlHost = PortalUrls.hostOf(controlUrl);
        if (controlHost.isEmpty()) {
            return hops.size() > 1;
        }

        return hops.stream().anyMatch(hop -> PortalUrls.hostOf(hop.url())
                .map(host -> !PortalUrls.sameSite(controlHost.get(), host))
                .orElse(true));
    }

}
