package app.nzyme.core.rest.responses.ethernet.portalintegrity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.UUID;

@AutoValue
public abstract class PortalIntegrityReportDetailsResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    public static PortalIntegrityReportDetailsResponse create(UUID uuid) {
        return builder()
                .uuid(uuid)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityReportDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract PortalIntegrityReportDetailsResponse build();
    }
}
