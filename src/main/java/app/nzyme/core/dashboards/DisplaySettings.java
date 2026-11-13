package app.nzyme.core.dashboards;

import app.nzyme.core.ethernet.CIDR;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public record DisplaySettings(String name,
                              @Nullable UUID locationId,
                              Theme theme,
                              @Nullable String lightFrom,
                              @Nullable String lightUntil,
                              String timeZone,
                              ClockFormat clockFormat,
                              int scalePercent,
                              int rotationSeconds,
                              int refreshSeconds,
                              List<String> allowedNetworks,
                              boolean redacted,
                              boolean hideCursor,
                              @Nullable DateTime expiresAt) {

    public enum Theme { DARK, LIGHT, SCHEDULED }
    public enum ClockFormat { H24, H12 }

    public static final int MAXIMUM_NAME_LENGTH = 128;
    public static final int MINIMUM_SCALE_PERCENT = 50;
    public static final int MAXIMUM_SCALE_PERCENT = 200;
    public static final int MINIMUM_ROTATION_SECONDS = 5;
    public static final int MAXIMUM_ROTATION_SECONDS = 3600;
    public static final int MINIMUM_REFRESH_SECONDS = 10;
    public static final int MAXIMUM_REFRESH_SECONDS = 3600;
    public static final int MAXIMUM_ALLOWED_NETWORKS = 25;

    private static final Pattern TIME_OF_DAY = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    public static DisplaySettings validated(@Nullable String name,
                                            @Nullable UUID locationId,
                                            @Nullable String theme,
                                            @Nullable String lightFrom,
                                            @Nullable String lightUntil,
                                            @Nullable String timeZone,
                                            @Nullable String clockFormat,
                                            @Nullable Integer scalePercent,
                                            @Nullable Integer rotationSeconds,
                                            @Nullable Integer refreshSeconds,
                                            @Nullable List<String> allowedNetworks,
                                            @Nullable Boolean redacted,
                                            @Nullable Boolean hideCursor,
                                            @Nullable DateTime expiresAt) {
        String trimmedName = DashboardSettings.storable(name == null ? "" : name.trim());
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("A name is required.");
        }
        if (trimmedName.length() > MAXIMUM_NAME_LENGTH) {
            throw new IllegalArgumentException("The name cannot be longer than " + MAXIMUM_NAME_LENGTH + " characters.");
        }

        Theme parsedTheme;
        try {
            parsedTheme = theme == null ? Theme.DARK : Theme.valueOf(theme.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The theme must be DARK, LIGHT or SCHEDULED.");
        }

        String from = null;
        String until = null;
        if (parsedTheme == Theme.SCHEDULED) {
            if (lightFrom == null || lightUntil == null
                    || !TIME_OF_DAY.matcher(lightFrom).matches() || !TIME_OF_DAY.matcher(lightUntil).matches()) {
                throw new IllegalArgumentException("A scheduled theme needs a start and an end time in the format HH:mm.");
            }
            if (lightFrom.equals(lightUntil)) {
                throw new IllegalArgumentException("The start and end time of the light theme cannot be the same.");
            }

            from = lightFrom;
            until = lightUntil;
        }

        String zone = timeZone == null || timeZone.trim().isEmpty() ? "UTC" : timeZone.trim();
        if (!ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new IllegalArgumentException("Unknown time zone.");
        }

        ClockFormat parsedClockFormat;
        try {
            parsedClockFormat = clockFormat == null ? ClockFormat.H24 : ClockFormat.valueOf(clockFormat.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The clock format must be H24 or H12.");
        }

        int scale = inRange(scalePercent, 100, MINIMUM_SCALE_PERCENT, MAXIMUM_SCALE_PERCENT, "scale", "percent");
        int rotation = inRange(rotationSeconds, 30, MINIMUM_ROTATION_SECONDS, MAXIMUM_ROTATION_SECONDS,
                "rotation interval", "seconds");
        int refresh = inRange(refreshSeconds, 60, MINIMUM_REFRESH_SECONDS, MAXIMUM_REFRESH_SECONDS,
                "refresh interval", "seconds");

        if (allowedNetworks != null && allowedNetworks.size() > MAXIMUM_ALLOWED_NETWORKS) {
            throw new IllegalArgumentException("A display cannot have more than " + MAXIMUM_ALLOWED_NETWORKS
                    + " allowed networks.");
        }

        List<String> networks = Lists.newArrayList();
        if (allowedNetworks != null) {
            for (String network : allowedNetworks) {
                if (network == null || network.trim().isEmpty()) {
                    continue;
                }

                String normalized;
                try {
                    normalized = CIDR.parse(network.trim()).toString();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("\"" + abbreviate(network.trim()) + "\" is not a valid IP "
                            + "address or CIDR network.");
                }

                if (!networks.contains(normalized)) {
                    networks.add(normalized);
                }
            }
        }
        return new DisplaySettings(trimmedName, locationId, parsedTheme, from, until, zone, parsedClockFormat,
                scale, rotation, refresh, networks, redacted != null && redacted, hideCursor != null && hideCursor,
                expiresAt);
    }

    public boolean allowsAddress(@Nullable String address) {
        if (allowedNetworks.isEmpty()) {
            return true;
        }

        if (address == null || !InetAddresses.isInetAddress(address)) {
            return false;
        }

        for (String network : allowedNetworks) {
            if (CIDR.parse(network).contains(InetAddresses.forString(address))) {
                return true;
            }
        }

        return false;
    }

    private static String abbreviate(String value) {
        return value.length() <= 64 ? value : value.substring(0, 64) + "...";
    }

    private static int inRange(@Nullable Integer value, int defaultValue, int minimum, int maximum, String label,
                               String unit) {
        if (value == null) {
            return defaultValue;
        }

        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("The " + label + " must be between " + minimum + " and " + maximum
                    + " " + unit + ".");
        }

        return value;
    }

}
