package app.nzyme.core.dot11.disco;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class DiscoDetectionRegistryKeys {

    public static final RegistryKey MONITORED_NETWORKS_ONLY =
            booleanKey("dot11_disco_detection_monitored_networks_only", false);

    public static final RegistryKey UNPROTECTED_FRAME_ENABLED =
            booleanKey("dot11_disco_detection_unprotected_frame_enabled", true);
    public static final RegistryKey UNPROTECTED_FRAME_IGNORE_MISSING_STATE =
            booleanKey("dot11_disco_detection_unprotected_frame_ignore_missing_state", true);

    public static final RegistryKey MIRRORED_FRAMES_ENABLED =
            booleanKey("dot11_disco_detection_mirrored_frames_enabled", true);

    public static final RegistryKey REPEATED_FRAMES_ENABLED =
            booleanKey("dot11_disco_detection_repeated_frames_enabled", true);
    // The tap counts at most 64 distinct sequence numbers per pair and report.
    public static final RegistryKey REPEATED_FRAMES_MINIMUM_SEQUENCE_NUMBERS =
            numberKey("dot11_disco_detection_repeated_frames_minimum_sequence_numbers", 2, 64, 5);
    public static final RegistryKey REPEATED_FRAMES_MINIMUM_FRAMES =
            numberKey("dot11_disco_detection_repeated_frames_minimum_frames", 2, 100000, 25);

    public static final RegistryKey SIGNAL_MISMATCH_ENABLED =
            booleanKey("dot11_disco_detection_signal_mismatch_enabled", true);
    public static final RegistryKey SIGNAL_MISMATCH_MINIMUM_DIFFERENCE =
            numberKey("dot11_disco_detection_signal_mismatch_minimum_difference", 5, 80, 20);

    public static final RegistryKey AUTHENTICATION_FLOOD_ENABLED =
            booleanKey("dot11_disco_detection_authentication_flood_enabled", true);
    public static final RegistryKey AUTHENTICATION_FLOOD_MINIMUM_TRANSMITTERS =
            numberKey("dot11_disco_detection_authentication_flood_minimum_transmitters", 2, 3000, 250);

    public static final RegistryKey EAPOL_FLOOD_ENABLED =
            booleanKey("dot11_disco_detection_eapol_flood_enabled", true);
    public static final RegistryKey EAPOL_FLOOD_MINIMUM_FRAMES =
            numberKey("dot11_disco_detection_eapol_flood_minimum_frames", 2, 100000, 20);

    public static final RegistryKey EAP_FAILURE_ENABLED =
            booleanKey("dot11_disco_detection_eap_failure_enabled", true);
    public static final RegistryKey EAP_FAILURE_MINIMUM_FAILURES =
            numberKey("dot11_disco_detection_eap_failure_minimum_failures", 1, 100000, 5);

    public static final RegistryKey FRAME_OVERFLOW_ENABLED =
            booleanKey("dot11_disco_detection_frame_overflow_enabled", true);

    public static final RegistryKey[] ALL = {
            MONITORED_NETWORKS_ONLY, FRAME_OVERFLOW_ENABLED,
            UNPROTECTED_FRAME_ENABLED, UNPROTECTED_FRAME_IGNORE_MISSING_STATE,
            MIRRORED_FRAMES_ENABLED,
            REPEATED_FRAMES_ENABLED, REPEATED_FRAMES_MINIMUM_SEQUENCE_NUMBERS, REPEATED_FRAMES_MINIMUM_FRAMES,
            SIGNAL_MISMATCH_ENABLED, SIGNAL_MISMATCH_MINIMUM_DIFFERENCE,
            AUTHENTICATION_FLOOD_ENABLED, AUTHENTICATION_FLOOD_MINIMUM_TRANSMITTERS,
            EAPOL_FLOOD_ENABLED, EAPOL_FLOOD_MINIMUM_FRAMES,
            EAP_FAILURE_ENABLED, EAP_FAILURE_MINIMUM_FAILURES
    };

    private static RegistryKey booleanKey(String key, boolean defaultValue) {
        return RegistryKey.create(
                key,
                Optional.of(new ArrayList<>() {{
                    add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
                }}),
                Optional.of(String.valueOf(defaultValue)),
                false
        );
    }

    private static RegistryKey numberKey(String key, int min, int max, int defaultValue) {
        return RegistryKey.create(
                key,
                Optional.of(new ArrayList<>() {{
                    add(ConfigurationEntryConstraint.createNumberRangeConstraint(min, max));
                }}),
                Optional.of(String.valueOf(defaultValue)),
                false
        );
    }

}
