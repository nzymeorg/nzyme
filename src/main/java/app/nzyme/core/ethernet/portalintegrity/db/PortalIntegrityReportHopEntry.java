package app.nzyme.core.ethernet.portalintegrity.db;

import app.nzyme.core.ethernet.l4.db.L4AddressData;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class PortalIntegrityReportHopEntry {

    public abstract int hopIndex();
    public abstract String url();
    public abstract L4AddressData resolvedAddress();
    public abstract int status();
    @Nullable
    public abstract String followedTo();
    public abstract String completeness();
    public abstract String raw();
    @Nullable
    public abstract String bodySha256();
    @Nullable
    public abstract String tls();

    public static PortalIntegrityReportHopEntry create(int hopIndex, String url, L4AddressData resolvedAddress, int status, String followedTo, String completeness, String raw, String bodySha256, String tls) {
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
        return new AutoValue_PortalIntegrityReportHopEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder hopIndex(int hopIndex);

        public abstract Builder url(String url);

        public abstract Builder resolvedAddress(L4AddressData resolvedAddress);

        public abstract Builder status(int status);

        public abstract Builder followedTo(String followedTo);

        public abstract Builder completeness(String completeness);

        public abstract Builder raw(String raw);

        public abstract Builder bodySha256(String bodySha256);

        public abstract Builder tls(String tls);

        public abstract PortalIntegrityReportHopEntry build();
    }
}