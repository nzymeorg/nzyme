package app.nzyme.core.ethernet;

import com.google.common.net.InetAddresses;

import java.net.InetAddress;

public record CIDR(InetAddress address, int prefix) {

    public static CIDR parse(String s) {
        if (s == null) throw new IllegalArgumentException("CIDR is null");

        int slash = s.indexOf('/');
        String addrPart = slash < 0 ? s : s.substring(0, slash);
        InetAddress addr = InetAddresses.forString(addrPart);

        byte[] bytes = addr.getAddress();
        int maxBits = bytes.length * 8;
        int prefix = maxBits;

        if (slash >= 0) {
            String p = s.substring(slash + 1);
            if (p.isEmpty() || p.length() > 3 || !p.chars().allMatch(c -> c >= '0' && c <= '9')) {
                throw new IllegalArgumentException("Invalid prefix: " + s);
            }
            prefix = Integer.parseInt(p);
            if (prefix > maxBits) throw new IllegalArgumentException("Prefix too long: " + s);
        }

        for (int i = prefix; i < maxBits; i++) {
            if ((bytes[i / 8] & (1 << (7 - i % 8))) != 0) {
                throw new IllegalArgumentException("Host bits set: " + s);
            }
        }
        return new CIDR(addr, prefix);
    }

    public boolean isSingleHost() {
        return prefix == address.getAddress().length * 8;
    }

    @Override
    public String toString() {
        return InetAddresses.toAddrString(address) + "/" + prefix;
    }

}