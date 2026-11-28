package app.nzyme.core.rest.responses.ethernet.portalintegrity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class PortalIntegrityControlUrlsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("control_urls")
    public abstract List<PortalIntegrityControlUrlResponse> controlUrls();

    @Schema(description = "How many control URLs are recorded per tap. New control URLs of a tap at the limit are not recorded.")
    @JsonProperty("maximum_per_tap")
    public abstract int maximumPerTap();

    @Schema(description = "Names of the selected taps that are at the limit.")
    @JsonProperty("taps_at_limit")
    public abstract List<String> tapsAtLimit();

    public static PortalIntegrityControlUrlsListResponse create(long total, List<PortalIntegrityControlUrlResponse> controlUrls, int maximumPerTap, List<String> tapsAtLimit) {
        return builder()
                .total(total)
                .controlUrls(controlUrls)
                .maximumPerTap(maximumPerTap)
                .tapsAtLimit(tapsAtLimit)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityControlUrlsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder controlUrls(List<PortalIntegrityControlUrlResponse> controlUrls);

        public abstract Builder maximumPerTap(int maximumPerTap);

        public abstract Builder tapsAtLimit(List<String> tapsAtLimit);

        public abstract PortalIntegrityControlUrlsListResponse build();
    }
}
