package app.nzyme.core.ethernet.portalintegrity.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class PortalIntegrityReportEntry {

    public abstract UUID uuid();
    public abstract String controlUrl();
    public abstract String probeInterface();
    public abstract String probeMac();
    public abstract String probeName();
    public abstract String assignedAddress();
    @Nullable
    public abstract String gatewayAddress();
    @Nullable
    public abstract String dhcpServerAddress();
    public abstract List<String> dnsServers();
    public abstract int hopCount();
    @Nullable
    public abstract String error();
    public abstract DateTime probedAt();

    public static PortalIntegrityReportEntry create(UUID uuid, String controlUrl, String probeInterface, String probeMac, String probeName, String assignedAddress, String gatewayAddress, String dhcpServerAddress, List<String> dnsServers, int hopCount, String error, DateTime probedAt) {
        return builder()
                .uuid(uuid)
                .controlUrl(controlUrl)
                .probeInterface(probeInterface)
                .probeMac(probeMac)
                .probeName(probeName)
                .assignedAddress(assignedAddress)
                .gatewayAddress(gatewayAddress)
                .dhcpServerAddress(dhcpServerAddress)
                .dnsServers(dnsServers)
                .hopCount(hopCount)
                .error(error)
                .probedAt(probedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityReportEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder controlUrl(String controlUrl);

        public abstract Builder probeInterface(String probeInterface);

        public abstract Builder probeMac(String probeMac);

        public abstract Builder probeName(String probeName);

        public abstract Builder assignedAddress(String assignedAddress);

        public abstract Builder gatewayAddress(String gatewayAddress);

        public abstract Builder dhcpServerAddress(String dhcpServerAddress);

        public abstract Builder dnsServers(List<String> dnsServers);

        public abstract Builder hopCount(int hopCount);

        public abstract Builder error(String error);

        public abstract Builder probedAt(DateTime probedAt);

        public abstract PortalIntegrityReportEntry build();
    }
}
