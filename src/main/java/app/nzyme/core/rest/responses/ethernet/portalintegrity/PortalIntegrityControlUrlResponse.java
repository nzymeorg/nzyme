package app.nzyme.core.rest.responses.ethernet.portalintegrity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class PortalIntegrityControlUrlResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @Schema(description = "Name of the tap the probe runs on.")
    @Nullable @JsonProperty("tap_name")
    public abstract String tapName();

    @JsonProperty("probe_name")
    public abstract String probeName();

    @JsonProperty("control_url")
    public abstract String controlUrl();

    @Schema(description = "IP version the probe requests the control URL over, 4 or 6.")
    @JsonProperty("ip_version")
    public abstract int ipVersion();

    @Schema(description = "NOT_MONITORED, PORTAL or NO_PORTAL.")
    @JsonProperty("expectation")
    public abstract String expectation();

    @Schema(description = "Final URLs of the expected portal, without query string.")
    @JsonProperty("expected_final_urls")
    public abstract List<String> expectedFinalUrls();

    @Schema(description = "Addresses the host of the expected portal may resolve to. Empty if the address is not checked.")
    @JsonProperty("expected_addresses")
    public abstract List<String> expectedAddresses();

    @Schema(description = "SHA-256 fingerprints of the certificates the expected portal may present. Empty if the certificate is not checked.")
    @JsonProperty("expected_certificates")
    public abstract List<String> expectedCertificates();

    @Schema(description = "What the last report shows against the expectation: NOT_MONITORED, AS_EXPECTED, PORTAL_MISSING, PORTAL_UNEXPECTED, PORTAL_WRONG or REQUEST_FAILED. For a monitored control URL whose probe has missed several cycles: NOT_REPORTING, or NOT_IN_TAP_CONFIGURATION if the tap says that it no longer runs the probe.")
    @JsonProperty("result")
    public abstract String result();

    @Schema(description = "For PORTAL_WRONG, what differs from the expected portal: URL, ADDRESS, CERTIFICATE.")
    @JsonProperty("reasons")
    public abstract List<String> reasons();

    @Schema(description = "True if the probe has missed several cycles.")
    @JsonProperty("stale")
    public abstract boolean stale();

    @Schema(description = "Whether the tap says that it runs a probe of this name that requests the control URL over the IP version. Not set if the tap has not reported which probes it runs.")
    @Nullable @JsonProperty("in_tap_configuration")
    public abstract Boolean inTapConfiguration();

    @Nullable @JsonProperty("last_report_uuid")
    public abstract UUID lastReportUuid();

    @Schema(description = "When the node received the last report for this control URL.")
    @JsonProperty("last_report_at")
    public abstract DateTime lastReportAt();

    @Schema(description = "Why the last request failed, if it did.")
    @Nullable @JsonProperty("last_error")
    public abstract String lastError();

    @JsonProperty("last_hop_count")
    public abstract int lastHopCount();

    @Schema(description = "Whether the last request was intercepted. Not set for a failed request.")
    @Nullable @JsonProperty("last_portal_presented")
    public abstract Boolean lastPortalPresented();

    @Schema(description = "URL of the last hop of the last report, without query string.")
    @Nullable @JsonProperty("last_final_url")
    public abstract String lastFinalUrl();

    @Schema(description = "Address the host of the last hop resolved to.")
    @Nullable @JsonProperty("last_final_address")
    public abstract String lastFinalAddress();

    @Schema(description = "SHA-256 fingerprint of the certificate the last hop presented, if it was served over TLS.")
    @Nullable @JsonProperty("last_final_certificate")
    public abstract String lastFinalCertificate();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @Schema(description = "When the expectation was last changed.")
    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    @Schema(description = "How many values each of the three lists of an expected portal takes.")
    @JsonProperty("expected_values_limit")
    public abstract int expectedValuesLimit();

    public static PortalIntegrityControlUrlResponse create(UUID uuid, UUID tapUuid, String tapName, String probeName, String controlUrl, int ipVersion, String expectation, List<String> expectedFinalUrls, List<String> expectedAddresses, List<String> expectedCertificates, String result, List<String> reasons, boolean stale, Boolean inTapConfiguration, UUID lastReportUuid, DateTime lastReportAt, String lastError, int lastHopCount, Boolean lastPortalPresented, String lastFinalUrl, String lastFinalAddress, String lastFinalCertificate, DateTime createdAt, DateTime updatedAt, int expectedValuesLimit) {
        return builder()
                .uuid(uuid)
                .tapUuid(tapUuid)
                .tapName(tapName)
                .probeName(probeName)
                .controlUrl(controlUrl)
                .ipVersion(ipVersion)
                .expectation(expectation)
                .expectedFinalUrls(expectedFinalUrls)
                .expectedAddresses(expectedAddresses)
                .expectedCertificates(expectedCertificates)
                .result(result)
                .reasons(reasons)
                .stale(stale)
                .inTapConfiguration(inTapConfiguration)
                .lastReportUuid(lastReportUuid)
                .lastReportAt(lastReportAt)
                .lastError(lastError)
                .lastHopCount(lastHopCount)
                .lastPortalPresented(lastPortalPresented)
                .lastFinalUrl(lastFinalUrl)
                .lastFinalAddress(lastFinalAddress)
                .lastFinalCertificate(lastFinalCertificate)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .expectedValuesLimit(expectedValuesLimit)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityControlUrlResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder tapName(String tapName);

        public abstract Builder probeName(String probeName);

        public abstract Builder controlUrl(String controlUrl);

        public abstract Builder ipVersion(int ipVersion);

        public abstract Builder expectation(String expectation);

        public abstract Builder expectedFinalUrls(List<String> expectedFinalUrls);

        public abstract Builder expectedAddresses(List<String> expectedAddresses);

        public abstract Builder expectedCertificates(List<String> expectedCertificates);

        public abstract Builder result(String result);

        public abstract Builder reasons(List<String> reasons);

        public abstract Builder stale(boolean stale);

        public abstract Builder inTapConfiguration(Boolean inTapConfiguration);

        public abstract Builder lastReportUuid(UUID lastReportUuid);

        public abstract Builder lastReportAt(DateTime lastReportAt);

        public abstract Builder lastError(String lastError);

        public abstract Builder lastHopCount(int lastHopCount);

        public abstract Builder lastPortalPresented(Boolean lastPortalPresented);

        public abstract Builder lastFinalUrl(String lastFinalUrl);

        public abstract Builder lastFinalAddress(String lastFinalAddress);

        public abstract Builder lastFinalCertificate(String lastFinalCertificate);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract Builder expectedValuesLimit(int expectedValuesLimit);

        public abstract PortalIntegrityControlUrlResponse build();
    }
}
