package app.nzyme.core.rest.responses.ethernet.portalintegrity;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class PortalIntegrityReportHopDetailsResponse {

    @JsonProperty("hop_index")
    public abstract int hopIndex();

    @JsonProperty("url")
    public abstract String url();

    @JsonProperty("resolved_address")
    public abstract L4AddressResponse resolvedAddress();

    @JsonProperty("status")
    public abstract int status();

    @Nullable @JsonProperty("followed_to")
    public abstract String followedTo();

    @JsonProperty("completeness")
    public abstract String completeness();

    @JsonProperty("raw")
    public abstract String raw();

    @Nullable @JsonProperty("body_sha256")
    public abstract String bodySha256();

    @Nullable @JsonProperty("tls")
    public abstract String tls(); // TODO struct

    public static PortalIntegrityReportHopDetailsResponse create(int hopIndex, String url, L4AddressResponse resolvedAddress, int status, String followedTo, String completeness, String raw, String bodySha256, String tls) {
        return builder()
                .hopIndex(hopIndex)
                .url(url)
                .resolvedAddress(resolvedAddress)
                .status(status)
                .followedTo(followedTo)
                .completeness(completeness)
                .raw(raw)
                .bodySha256(bodySha256)
                .tls(tls)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityReportHopDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder hopIndex(int hopIndex);

        public abstract Builder url(String url);

        public abstract Builder resolvedAddress(L4AddressResponse resolvedAddress);

        public abstract Builder status(int status);

        public abstract Builder followedTo(String followedTo);

        public abstract Builder completeness(String completeness);

        public abstract Builder raw(String raw);

        public abstract Builder bodySha256(String bodySha256);

        public abstract Builder tls(String tls);

        public abstract PortalIntegrityReportHopDetailsResponse build();
    }
}
