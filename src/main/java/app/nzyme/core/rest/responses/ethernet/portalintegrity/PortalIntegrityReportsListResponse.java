package app.nzyme.core.rest.responses.ethernet.portalintegrity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class PortalIntegrityReportsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("reports")
    public abstract List<PortalIntegrityReportDetailsResponse> reports();

    public static PortalIntegrityReportsListResponse create(long total, List<PortalIntegrityReportDetailsResponse> reports) {
        return builder()
                .total(total)
                .reports(reports)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityReportsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder reports(List<PortalIntegrityReportDetailsResponse> reports);

        public abstract PortalIntegrityReportsListResponse build();
    }
}
