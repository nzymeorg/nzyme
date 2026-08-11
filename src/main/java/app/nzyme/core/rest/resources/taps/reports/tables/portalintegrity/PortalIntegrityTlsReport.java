package app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.List;

@AutoValue
public abstract class PortalIntegrityTlsReport {

    public abstract List<String> chainDer();
    public abstract String leafSha256();
    @Nullable
    public abstract String protocolVersion();
    @Nullable
    public abstract Integer cipherSuite();
    @Nullable
    public abstract String sni();

    @JsonCreator
    public static PortalIntegrityTlsReport create(@JsonProperty("chain_der") List<String> chainDer,
                                                  @JsonProperty("leaf_sha256") String leafSha256,
                                                  @JsonProperty("protocol_version") String protocolVersion,
                                                  @JsonProperty("cipher_suite") Integer cipherSuite,
                                                  @JsonProperty("sni") String sni) {
        return builder()
                .chainDer(chainDer)
                .leafSha256(leafSha256)
                .protocolVersion(protocolVersion)
                .cipherSuite(cipherSuite)
                .sni(sni)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityTlsReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder chainDer(List<String> chainDer);

        public abstract Builder leafSha256(String leafSha256);

        public abstract Builder protocolVersion(String protocolVersion);

        public abstract Builder cipherSuite(Integer cipherSuite);

        public abstract Builder sni(String sni);

        public abstract PortalIntegrityTlsReport build();
    }

}
