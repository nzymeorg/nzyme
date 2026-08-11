package app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Null;

@AutoValue
public abstract class PortalIntegrityHopReport {

    public abstract String url();
    public abstract String resolvedIp();
    public abstract int status();
    @Nullable
    public abstract String followedTo();
    public abstract String raw();
    public abstract String completeness();
    @Nullable
    public abstract PortalIntegrityTlsReport tls();

    @JsonCreator
    public static PortalIntegrityHopReport create(@JsonProperty("url") String url,
                                                  @JsonProperty("resolved_ip") String resolvedIp,
                                                  @JsonProperty("status") int status,
                                                  @JsonProperty("followed_to") String followedTo,
                                                  @JsonProperty("raw") String raw,
                                                  @JsonProperty("completeness") String completeness,
                                                  @JsonProperty("tls") PortalIntegrityTlsReport tls) {
        return builder()
                .url(url)
                .resolvedIp(resolvedIp)
                .status(status)
                .followedTo(followedTo)
                .raw(raw)
                .completeness(completeness)
                .tls(tls)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityHopReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder url(String url);

        public abstract Builder resolvedIp(String resolvedIp);

        public abstract Builder status(int status);

        public abstract Builder followedTo(String followedTo);

        public abstract Builder raw(String raw);

        public abstract Builder completeness(String completeness);

        public abstract Builder tls(PortalIntegrityTlsReport tls);

        public abstract PortalIntegrityHopReport build();
    }

}
