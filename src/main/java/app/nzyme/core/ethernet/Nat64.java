package app.nzyme.core.ethernet;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public final class Nat64 {

    private static final byte[] WELL_KNOWN_PREFIX = {0x00, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0};

    private Nat64() {}

    public static Optional<InetAddress> embeddedAddress(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return Optional.empty();
        }

        byte[] bytes = address.getAddress();
        for (int i = 0; i < WELL_KNOWN_PREFIX.length; i++) {
            if (bytes[i] != WELL_KNOWN_PREFIX[i]) {
                return Optional.empty();
            }
        }

        try {
            return Optional.of(InetAddress.getByAddress(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]}));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    public static InetAddress destination(InetAddress address) {
        return embeddedAddress(address).orElse(address);
    }

}
