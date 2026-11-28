package app.nzyme.core.ethernet.portalintegrity.db;

import app.nzyme.core.ethernet.portalintegrity.PortalExpectation;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class PortalIntegrityControlUrlEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract UUID tapUuid();
    public abstract String probeName();
    public abstract String controlUrl();
    public abstract int ipVersion();
    public abstract PortalExpectation expectation();
    public abstract List<String> expectedFinalUrls();
    public abstract List<String> expectedAddresses();
    public abstract List<String> expectedCertificates();
    @Nullable
    public abstract UUID lastReportUuid();
    public abstract DateTime lastReportAt();
    @Nullable
    public abstract Integer reportIntervalSeconds();
    @Nullable
    public abstract String lastError();
    public abstract int lastHopCount();
    @Nullable
    public abstract Boolean lastPortalPresented();
    @Nullable
    public abstract String lastFinalUrl();
    @Nullable
    public abstract String lastFinalAddress();
    @Nullable
    public abstract String lastFinalCertificate();
    @Nullable
    public abstract DateTime inTapConfigurationSince();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static PortalIntegrityControlUrlEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, UUID tapUuid, String probeName, String controlUrl, int ipVersion, PortalExpectation expectation, List<String> expectedFinalUrls, List<String> expectedAddresses, List<String> expectedCertificates, UUID lastReportUuid, DateTime lastReportAt, Integer reportIntervalSeconds, String lastError, int lastHopCount, Boolean lastPortalPresented, String lastFinalUrl, String lastFinalAddress, String lastFinalCertificate, DateTime inTapConfigurationSince, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .tapUuid(tapUuid)
                .probeName(probeName)
                .controlUrl(controlUrl)
                .ipVersion(ipVersion)
                .expectation(expectation)
                .expectedFinalUrls(expectedFinalUrls)
                .expectedAddresses(expectedAddresses)
                .expectedCertificates(expectedCertificates)
                .lastReportUuid(lastReportUuid)
                .lastReportAt(lastReportAt)
                .reportIntervalSeconds(reportIntervalSeconds)
                .lastError(lastError)
                .lastHopCount(lastHopCount)
                .lastPortalPresented(lastPortalPresented)
                .lastFinalUrl(lastFinalUrl)
                .lastFinalAddress(lastFinalAddress)
                .lastFinalCertificate(lastFinalCertificate)
                .inTapConfigurationSince(inTapConfigurationSince)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityControlUrlEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder uuid(UUID uuid);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder probeName(String probeName);

        public abstract Builder controlUrl(String controlUrl);

        public abstract Builder ipVersion(int ipVersion);

        public abstract Builder expectation(PortalExpectation expectation);

        public abstract Builder expectedFinalUrls(List<String> expectedFinalUrls);

        public abstract Builder expectedAddresses(List<String> expectedAddresses);

        public abstract Builder expectedCertificates(List<String> expectedCertificates);

        public abstract Builder lastReportUuid(UUID lastReportUuid);

        public abstract Builder lastReportAt(DateTime lastReportAt);

        public abstract Builder reportIntervalSeconds(Integer reportIntervalSeconds);

        public abstract Builder lastError(String lastError);

        public abstract Builder lastHopCount(int lastHopCount);

        public abstract Builder lastPortalPresented(Boolean lastPortalPresented);

        public abstract Builder lastFinalUrl(String lastFinalUrl);

        public abstract Builder lastFinalAddress(String lastFinalAddress);

        public abstract Builder lastFinalCertificate(String lastFinalCertificate);

        public abstract Builder inTapConfigurationSince(DateTime inTapConfigurationSince);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract PortalIntegrityControlUrlEntry build();
    }
}
