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
        return Hashing.sha256()
                .hashString(sessionEstablishedAt.getMillis()
                        + sourceAddress
                        + destinationAddress
                        + sourcePort
                        + destinationPort, Charsets.UTF_8)
                .toString();
    }

    public static String buildUntimedL4Key(String sourceAddress,
                                           String destinationAddress,
                                           int sourcePort,
                                           int destinationPort) {
        return Hashing.sha256()
                .hashString(sourceAddress + destinationAddress + sourcePort + destinationPort, Charsets.UTF_8)
                .toString();
    }

    public static InetAddress stringtoInetAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            // This shouldn't happen because we pass IP addresses.
            throw new RuntimeException(e);
        }
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
