package app.nzyme.core.dot11.channelswitches;

import app.nzyme.core.rest.resources.taps.reports.tables.dot11.Dot11ChannelSwitchReport;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Set;

public final class ChannelSwitchRules {

    public static final String FRAME_TYPE_BEACON = "BEACON";
    public static final Set<String> FRAME_TYPES = Set.of(FRAME_TYPE_BEACON, "PROBE_RESPONSE", "ACTION");
    public static final Set<String> ANNOUNCEMENT_TYPES = Set.of("CSA", "ECSA");

    public static final int REPEATED_TIMESTAMP_FRAMES = 8;
    public static final int TIMESTAMP_MISMATCH_FRAMES = 3;
    public static final int INTERLEAVED_BEACONS = 3;
    public static final int STATIC_COUNT_FRAMES = 8;

    // Beacons that say they were sent on a channel. Fewer can be damaged frames that passed their check.
    public static final int OUTCOME_MINIMUM_FRAMES = 5;
    public static final int OUTCOME_MINIMUM_FRAMES_PER_HALF = 3;

    // Long enough for a tap to hop through all of its channels at least once.
    public static final int OUTCOME_EVIDENCE_MINUTES = 5;

    public static final int BURST_THRESHOLD_DEFAULT = 50;
    public static final int BURST_THRESHOLD_MINIMUM = 10;
    public static final int BURST_THRESHOLD_MAXIMUM = 1000;

    private static final int CHANNEL_14_FREQUENCY = 2484;

    private enum Band { GHZ_2_4, GHZ_5, GHZ_6 }

    private ChannelSwitchRules() {}

    /*
     * Channel 14 is open to 802.11b access points in Japan and to nothing else. An access point that advertises
     * OFDM cannot move there in any country. Unknown, as in action frames, counts as possible.
     */
    public static boolean channel14Usable(@Nullable Boolean dsssOnly, @Nullable String countryCode) {
        return !Boolean.FALSE.equals(dsssOnly) && (countryCode == null || "JP".equals(countryCode));
    }

    @Nullable
    public static Integer newFrequency(Dot11ChannelSwitchReport report) {
        return newFrequency(report.frequency(), report.newOperatingClass(), report.newChannel());
    }

    // Empty if the band is unknown or has no such channel. Says nothing about who may operate there.
    @Nullable
    public static Integer newFrequency(@Nullable Integer frequency,
                                       @Nullable Integer operatingClass,
                                       int channel) {
        Band band = band(frequency, operatingClass);
        if (band == null) {
            return null;
        }

        return switch (band) {
            case GHZ_2_4 -> {
                if (channel == 14) {
                    yield CHANNEL_14_FREQUENCY;
                }

                yield channel >= 1 && channel <= 13 ? 2407 + channel * 5 : null;
            }
            case GHZ_5 -> isFiveGhzChannel(channel) ? 5000 + channel * 5 : null;
            case GHZ_6 -> {
                if (channel == 2) {
                    yield 5935;
                }

                yield channel >= 1 && channel <= 233 && channel % 4 == 1 ? 5950 + channel * 5 : null;
            }
        };
    }

    public static boolean isInvalidChannel(@Nullable Integer frequency,
                                           @Nullable Integer operatingClass,
                                           int channel,
                                           boolean channel14Usable) {
        if (band(frequency, operatingClass) == null) {
            return false;
        }

        Integer newFrequency = newFrequency(frequency, operatingClass, channel);

        return newFrequency == null || (newFrequency == CHANNEL_14_FREQUENCY && !channel14Usable);
    }

    private static boolean isFiveGhzChannel(int channel) {
        return (channel >= 36 && channel <= 64 && channel % 4 == 0)
                || (channel >= 100 && channel <= 144 && channel % 4 == 0)
                || (channel >= 149 && channel <= 177 && channel % 4 == 1);
    }

    // Global operating classes name the band. Others are country specific, and the switch then stays in its band.
    @Nullable
    private static Band band(@Nullable Integer frequency, @Nullable Integer operatingClass) {
        if (operatingClass != null) {
            if (operatingClass >= 81 && operatingClass <= 84) {
                return Band.GHZ_2_4;
            }

            if (operatingClass >= 115 && operatingClass <= 130) {
                return Band.GHZ_5;
            }

            if (operatingClass >= 131 && operatingClass <= 137) {
                return Band.GHZ_6;
            }
        }

        if (frequency == null) {
            return null;
        }

        if (frequency >= 2400 && frequency <= 2500) {
            return Band.GHZ_2_4;
        }

        if (frequency >= 4900 && frequency < 5925) {
            return Band.GHZ_5;
        }

        if (frequency >= 5925 && frequency <= 7125) {
            return Band.GHZ_6;
        }

        return null;
    }

    public static List<ChannelSwitchAnomaly> anomalies(Dot11ChannelSwitchReport report) {
        List<ChannelSwitchAnomaly> anomalies = Lists.newArrayList();

        if (isInvalidChannel(report.frequency(), report.newOperatingClass(), report.newChannel(),
                channel14Usable(report.dsssOnly(), report.countryCode()))) {
            anomalies.add(ChannelSwitchAnomaly.INVALID_CHANNEL);
        }

        // An access point that announces a switch does so in every beacon, whatever frame carried this one.
        boolean interleaved = report.interleavedBeacons() >= INTERLEAVED_BEACONS;

        // Only beacons are sent once each on a schedule. Probe responses and action frames are retried.
        if (!FRAME_TYPE_BEACON.equals(report.frameType())) {
            if (interleaved) {
                anomalies.add(ChannelSwitchAnomaly.INTERLEAVED_BEACONS);
            }

            return anomalies;
        }

        if (report.timestampRepetitions() >= REPEATED_TIMESTAMP_FRAMES) {
            anomalies.add(ChannelSwitchAnomaly.REPEATED_TIMESTAMP);
        }

        if (report.timestampMismatchFrames() >= TIMESTAMP_MISMATCH_FRAMES) {
            anomalies.add(ChannelSwitchAnomaly.TIMESTAMP_MISMATCH);
        }

        if (interleaved) {
            anomalies.add(ChannelSwitchAnomaly.INTERLEAVED_BEACONS);
        }

        if (report.frameCount() >= STATIC_COUNT_FRAMES && report.countMin() == report.countMax()) {
            anomalies.add(ChannelSwitchAnomaly.STATIC_COUNT);
        }

        if (report.trailingElementFrames() > 0) {
            anomalies.add(ChannelSwitchAnomaly.TRAILING_ELEMENT);
        }

        return anomalies;
    }

}
