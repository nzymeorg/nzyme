package app.nzyme.core.ethernet.segmentation;

import jakarta.annotation.Nullable;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

// The address is not set for external sides that were read back from the flow rollup. A broadcast or multicast
// address is neither internal nor external and in no segment.
public record Endpoint(@Nullable InetAddress address,
                       boolean internal,
                       @Nullable Segment segment,
                       Set<UUID> tags,
                       @Nullable UUID asset,
                       @Nullable Long asn,
                       @Nullable String countryCode,
                       boolean broadcast,
                       @Nullable String asnName,
                       @Nullable String asnDomain) {

    public Endpoint(@Nullable InetAddress address,
                    boolean internal,
                    @Nullable Segment segment,
                    Set<UUID> tags,
                    @Nullable UUID asset,
                    @Nullable Long asn,
                    @Nullable String countryCode) {
        this(address, internal, segment, tags, asset, asn, countryCode, false, null, null);
    }

    // Without an address it stands for many broadcast or multicast addresses.
    public static Endpoint broadcast(@Nullable InetAddress address) {
        return new Endpoint(address, false, null, Collections.emptySet(), null, null, null, true, null, null);
    }

    // Who runs the autonomous system. Only a description: it is no part of what a side is matched or keyed by.
    public Endpoint withAsnDetails(@Nullable String name, @Nullable String domain) {
        if (asn == null || (name == null && domain == null)) {
            return this;
        }

        return new Endpoint(address, internal, segment, tags, asset, asn, countryCode, broadcast, name, domain);
    }

    public boolean hasAddress() {
        return address != null;
    }

}
