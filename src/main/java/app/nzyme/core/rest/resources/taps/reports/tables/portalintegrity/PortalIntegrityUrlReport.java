package app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class PortalIntegrityUrlReport {

    public abstract String controlUrl();
    public abstract List<PortalIntegrityHopReport> hops();
    public abstract PortalIntegrityCheckContext context();
    @Nullable
    public abstract String error();
    public abstract DateTime probedAt();

    @JsonCreator
    public static PortalIntegrityUrlReport create(@JsonProperty("control_url") String controlUrl,
                                                  @JsonProperty("hops") List<PortalIntegrityHopReport> hops,
                                                  @JsonProperty("context") PortalIntegrityCheckContext context,
                                                  @JsonProperty("error") String error,
                                                  @JsonProperty("probed_at") DateTime probedAt) {
        return builder()
                .controlUrl(controlUrl)
                .hops(hops)
                .context(context)
                .error(error)
                .probedAt(probedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityUrlReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder controlUrl(String controlUrl);

        public abstract Builder hops(List<PortalIntegrityHopReport> hops);

        public abstract Builder context(PortalIntegrityCheckContext context);

        public abstract Builder error(String error);

        public abstract Builder probedAt(DateTime probedAt);

        public abstract PortalIntegrityUrlReport build();
    }

}
