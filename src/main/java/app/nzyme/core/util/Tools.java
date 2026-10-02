/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.util;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.floorplans.db.TenantLocationFloorEntry;
import app.nzyme.core.taps.Tap;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Tools {

    private static final Logger LOG = LogManager.getLogger(Tools.class);

    private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9-_]+$");
    private static final Pattern VALID_MAC = Pattern.compile("^[a-fA-F0-9]{2}:[a-fA-F0-9]{2}:[a-fA-F0-9]{2}:[a-fA-F0-9]{2}:[a-fA-F0-9]{2}:[a-fA-F0-9]{2}$");

    public static boolean isSafeNodeName(String x) {
        if (x == null) {
            return false;
        }

        if (x.trim().isEmpty()) {
            return false;
        }

        return x.length() < 255 && SAFE_ID.matcher(x).matches();
    }

    public static boolean isValidMacAddress(String addr) {
        if (Strings.isNullOrEmpty(addr)) {
            return false;
        }

        Matcher m = VALID_MAC.matcher(addr);
        return m.find();
    }

    public static String durationToHumanReadable(Duration duration) {
        if (duration.getStandardSeconds() == 0) {
            return "n/a";
        }

        if (duration.getStandardSeconds() < 60) {
            return duration.getStandardSeconds() + " seconds";
        }

        if (duration.getStandardHours() < 2) {
            return duration.getStandardMinutes() + " minutes";
        }

        if (duration.getStandardDays() <= 3) {
            return duration.getStandardHours() + " hours";
        }

        return duration.getStandardDays() + " days";
    }

    public static String sanitizeSSID(@NotNull String ssid) {
        return ssid.replaceAll("\0", "") // NULL bytes
                .replaceAll("\\p{C}", "") // invisible control characters and unused code points.
                .replaceAll("\\p{Zl}", "") // line separator character U+2028
                .replaceAll("\\p{Zp}", "") // paragraph separator character U+2029
                .replaceAll("\t*", "") // Tabs
                .replaceAll(" +", " ") // Multiple whitespaces in succession
                .trim();
    }

    public static boolean isTapActive(DateTime lastReport) {
        return lastReport != null && lastReport.isAfter(DateTime.now().minusMinutes(2));
    }

    public static float round(float f, int decimalPlaces) {
        BigDecimal bd = new BigDecimal(Float.toString(f));
        bd = bd.setScale(decimalPlaces, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    public static String buildL4Key(DateTime sessionEstablishedAt,
                                    String sourceAddress,
                                    String destinationAddress,
                                    int sourcePort,
                                    int destinationPort) {
        String[] ordered = canonicalEndpoints(sourceAddress, destinationAddress, sourcePort, destinationPort);

        return Hashing.sha256()
                .hashString(sessionEstablishedAt.getMillis()
                                + ordered[0]   // low address
                                + ordered[1]   // high address
                                + ordered[2]   // low port
                                + ordered[3]   // high port
                        , Charsets.UTF_8)
                .toString();
    }

    public static String buildUntimedL4Key(String sourceAddress,
                                           String destinationAddress,
                                           int sourcePort,
                                           int destinationPort) {
        String[] ordered = canonicalEndpoints(sourceAddress, destinationAddress, sourcePort, destinationPort);

        return Hashing.sha256()
                .hashString(ordered[0] + ordered[1] + ordered[2] + ordered[3], Charsets.UTF_8)
                .toString();
    }

    private static String[] canonicalEndpoints(String sourceAddress,
                                               String destinationAddress,
                                               int sourcePort,
                                               int destinationPort) {
        boolean sourceIsLow;
        int addrCmp = sourceAddress.compareTo(destinationAddress);
        if (addrCmp < 0) {
            sourceIsLow = true;
        } else if (addrCmp > 0) {
            sourceIsLow = false;
        } else {
            // Same address; order by port.
            sourceIsLow = sourcePort <= destinationPort;
        }

        if (sourceIsLow) {
            return new String[]{
                    sourceAddress, destinationAddress,
                    String.valueOf(sourcePort), String.valueOf(destinationPort)
            };
        } else {
            return new String[]{
                    destinationAddress, sourceAddress,
                    String.valueOf(destinationPort), String.valueOf(sourcePort)
            };
        }
    }

    public static InetAddress stringtoInetAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            // This shouldn't happen because we pass IP addresses.
            throw new RuntimeException(e);
        }
    }

    public static boolean isValidCidr(String s) {
        if (s == null) return false;

        int slash = s.indexOf('/');
        String addrPart = slash < 0 ? s : s.substring(0, slash);

        byte[] bytes;
        try {
            bytes = InetAddresses.forString(addrPart).getAddress();
        } catch (IllegalArgumentException e) {
            return false;
        }

        int maxBits = bytes.length * 8;
        int prefix = maxBits;

        if (slash >= 0) {
            String p = s.substring(slash + 1);
            if (p.isEmpty() || p.length() > 3) return false;
            for (char c : p.toCharArray()) {
                if (c < '0' || c > '9') return false;
            }
            prefix = Integer.parseInt(p);
            if (prefix > maxBits) return false;
        }

        for (int i = prefix; i < maxBits; i++) {
            if ((bytes[i / 8] & (1 << (7 - i % 8))) != 0) return false;
        }

        return true;
    }

    public static boolean macAddressIsRandomized(String mac) {
        if (mac == null || mac.trim().isEmpty()) {
            return false;
        }

        if (mac.length() != 17 || !mac.contains(":")) {
            LOG.warn("Passed invalid MAC address [{}]", mac);
            return false;
        }

        // Extract the first octet of the MAC address
        String firstOctet = mac.split(":")[0];

        // Convert the first octet to an integer
        int firstOctetInt = Integer.parseInt(firstOctet, 16);

        // Check if the second least significant bit is 1 (i.e., if the address is locally administered)
        return (firstOctetInt & 0b00000010) != 0;
    }

    public static List<UUID> getTapUuids(NzymeNode nzyme, @Nullable UUID organizationId, @Nullable UUID tenantId) {
        List<Tap> taps;
        if (organizationId == null && tenantId == null) {
            taps = nzyme.getTapManager().findAllTapsOfAllUsers();
        } else if (organizationId != null && tenantId == null) {
            taps = nzyme.getTapManager().findAllTapsOfOrganization(organizationId);
        } else {
            taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId);
        }

        return taps.stream()
                .map(Tap::uuid)
                .collect(Collectors.toList());
    }

    public static String buildFloorName(TenantLocationFloorEntry floor) {
        if (floor.name() == null) {
            return "Floor " + floor.number();
        } else {
            return floor.name();
        }
    }

}
