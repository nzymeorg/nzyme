package app.nzyme.core.rest.responses.ethernet.portalintegrity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import org.joda.time.DateTime;

@AutoValue
public abstract class PortalIntegrityObservedOutcomeResponse {

    @Schema(description = "PORTAL for a captive portal, NO_PORTAL for a request that was not intercepted, FAILED for all failed requests together.")
    @JsonProperty("kind")
    public abstract String kind();

    @Schema(description = "URL of the last hop, without query string. Not set for failed requests.")
    @Nullable @JsonProperty("final_url")
    public abstract String finalUrl();

    @Schema(description = "Address the host of the last hop resolved to.")
    @Nullable @JsonProperty("final_address")
    public abstract String finalAddress();

    @Schema(description = "SHA-256 fingerprint of the certificate the last hop presented, if it was served over TLS.")
    @Nullable @JsonProperty("final_certificate")
    public abstract String finalCertificate();

    @Schema(description = "For failed requests, the error of the latest one.")
    @Nullable @JsonProperty("error")
    public abstract String error();

    @Schema(description = "Number of reports that showed this.")
    @JsonProperty("report_count")
    public abstract long reportCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @Schema(description = "How this compares to the expectation of the control URL: NOT_MONITORED, AS_EXPECTED, PORTAL_MISSING, PORTAL_UNEXPECTED, PORTAL_WRONG or REQUEST_FAILED.")
    @JsonProperty("result")
    public abstract String result();

    @Schema(description = "For PORTAL_WRONG, what differs from the expected portal: URL, ADDRESS, CERTIFICATE.")
    @JsonProperty("reasons")
    public abstract List<String> reasons();

    public static PortalIntegrityObservedOutcomeResponse create(String kind, String finalUrl, String finalAddress, String finalCertificate, String error, long reportCount, DateTime firstSeen, DateTime lastSeen, String result, List<String> reasons) {
        return builder()
                .kind(kind)
                .finalUrl(finalUrl)
                .finalAddress(finalAddress)
                .finalCertificate(finalCertificate)
                .error(error)
                .reportCount(reportCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .result(result)
                .reasons(reasons)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityObservedOutcomeResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder kind(String kind);

        public abstract Builder finalUrl(String finalUrl);

        public abstract Builder finalAddress(String finalAddress);

        public abstract Builder finalCertificate(String finalCertificate);

        public abstract Builder error(String error);

        public abstract Builder reportCount(long reportCount);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder result(String result);

        public abstract Builder reasons(List<String> reasons);

        public abstract PortalIntegrityObservedOutcomeResponse build();
    }
}
