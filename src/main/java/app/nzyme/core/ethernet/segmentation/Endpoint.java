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
                       boolean broadcast) {

    public Endpoint(@Nullable InetAddress address,
                    boolean internal,
                    @Nullable Segment segment,
                    Set<UUID> tags,
                    @Nullable UUID asset,
                    @Nullable Long asn,
                    @Nullable String countryCode) {
        this(address, internal, segment, tags, asset, asn, countryCode, false);
    }

    // Without an address it stands for many broadcast or multicast addresses.
    public static Endpoint broadcast(@Nullable InetAddress address) {
        return new Endpoint(address, false, null, Collections.emptySet(), null, null, null, true);
    }

    public boolean hasAddress() {
        return address != null;
    }

}
