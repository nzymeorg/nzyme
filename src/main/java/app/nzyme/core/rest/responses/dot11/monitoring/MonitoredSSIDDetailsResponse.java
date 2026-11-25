package app.nzyme.core.rest.responses.dot11.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class MonitoredSSIDDetailsResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("is_enabled")
    public abstract boolean isEnabled();

    @JsonProperty("ssid")
    public abstract String ssid();

    @JsonProperty("organization_id")
    @Nullable
    public abstract UUID organizationId();

    @JsonProperty("tenant_id")
    @Nullable
    public abstract UUID tenantId();

    @JsonProperty("bssids")
    @Nullable
    public abstract List<MonitoredBSSIDDetailsResponse> bssids();

    @JsonProperty("channels")
    @Nullable
    public abstract List<MonitoredChannelResponse> channels();

    @JsonProperty("security_suites")
    @Nullable
    public abstract List<MonitoredSecuritySuiteResponse> securitySuites();

    @JsonProperty("similar_looking_ssid_threshold")
    @Nullable
    public abstract Integer similarLookingSSIDThreshold();

    @JsonProperty("channel_switch_burst_threshold")
    public abstract int channelSwitchBurstThreshold();

    @JsonProperty("channel_switch_disabled_rules")
    public abstract List<String> channelSwitchDisabledRules();

    @JsonProperty("allowed_similar_ssids")
    public abstract List<String> allowedSimilarSSIDs();

    @JsonProperty("restricted_ssid_substrings")
    public abstract List<RestrictedSSIDSubstringDetailsResponse> restrictedSSIDSubstrings();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    @JsonProperty("is_alerted")
    public abstract boolean isAlerted();

    @JsonProperty("status_unexpected_bssid")
    public abstract boolean statusUnexpectedBSSID();

    @JsonProperty("status_unexpected_channel")
    public abstract boolean statusUnexpectedChannel();

    @JsonProperty("status_unexpected_security_suites")
    public abstract boolean statusUnexpectedSecuritySuites();

    @JsonProperty("status_unexpected_fingerprint")
    public abstract boolean statusUnexpectedFingerprint();

    @JsonProperty("status_unexpected_signal_tracks")
    public abstract boolean statusUnexpectedSignalTracks();

    @JsonProperty("status_disco_monitor")
    public abstract boolean statusDiscoMonitor();

    @JsonProperty("status_channel_switch_monitor")
    public abstract boolean statusChannelSwitchMonitor();

    @JsonProperty("status_multiple_channels")
    public abstract boolean statusMultipleChannels();

    @JsonProperty("status_similar_ssids")
    public abstract boolean statusSimilarSSIDs();

    @JsonProperty("status_restricted_ssid_substrings")
    public abstract boolean statusRestrictedSSIDSubstrings();

    @JsonProperty("status_unapproved_client")
    public abstract boolean statusUnapprovedClient();

    @JsonProperty("enabled_unexpected_bssid")
    public abstract boolean enabledUnexpectedBSSID();

    @JsonProperty("enabled_unexpected_channel")
    public abstract boolean enabledUnexpectedChannel();

    @JsonProperty("enabled_unexpected_security_suites")
    public abstract boolean enabledUnexpectedSecuritySuites();

    @JsonProperty("enabled_unexpected_fingerprint")
    public abstract boolean enabledUnexpectedFingerprint();

    @JsonProperty("enabled_unexpected_signal_tracks")
    public abstract boolean enabledUnexpectedSignalTracks();

    @JsonProperty("enabled_disco_monitor")
    public abstract boolean enabledDiscoMonitor();

    @JsonProperty("enabled_channel_switch_monitor")
    public abstract boolean enabledChannelSwitchMonitor();

    @JsonProperty("enabled_multiple_channels")
    public abstract boolean enabledMultipleChannels();

    @JsonProperty("enabled_similar_ssids")
    public abstract boolean enabledSimilarSSIDs();

    @JsonProperty("enabled_restricted_ssid_substrings")
    public abstract boolean enabledRestrictedSSIDSubstrings();

    @JsonProperty("enabled_unapproved_client")
    public abstract boolean enabledClientMonitor();

    public static MonitoredSSIDDetailsResponse create(UUID uuid, boolean isEnabled, String ssid, UUID organizationId, UUID tenantId, List<MonitoredBSSIDDetailsResponse> bssids, List<MonitoredChannelResponse> channels, List<MonitoredSecuritySuiteResponse> securitySuites, Integer similarLookingSSIDThreshold, int channelSwitchBurstThreshold, List<String> channelSwitchDisabledRules, List<String> allowedSimilarSSIDs, List<RestrictedSSIDSubstringDetailsResponse> restrictedSSIDSubstrings, DateTime createdAt, DateTime updatedAt, boolean isAlerted, boolean statusUnexpectedBSSID, boolean statusUnexpectedChannel, boolean statusUnexpectedSecuritySuites, boolean statusUnexpectedFingerprint, boolean statusUnexpectedSignalTracks, boolean statusDiscoMonitor, boolean statusChannelSwitchMonitor, boolean statusMultipleChannels, boolean statusSimilarSSIDs, boolean statusRestrictedSSIDSubstrings, boolean statusUnapprovedClient, boolean enabledUnexpectedBSSID, boolean enabledUnexpectedChannel, boolean enabledUnexpectedSecuritySuites, boolean enabledUnexpectedFingerprint, boolean enabledUnexpectedSignalTracks, boolean enabledDiscoMonitor, boolean enabledChannelSwitchMonitor, boolean enabledMultipleChannels, boolean enabledSimilarSSIDs, boolean enabledRestrictedSSIDSubstrings, boolean enabledClientMonitor) {
        return builder()
                .uuid(uuid)
                .isEnabled(isEnabled)
                .ssid(ssid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .bssids(bssids)
                .channels(channels)
                .securitySuites(securitySuites)
                .similarLookingSSIDThreshold(similarLookingSSIDThreshold)
                .channelSwitchBurstThreshold(channelSwitchBurstThreshold)
                .channelSwitchDisabledRules(channelSwitchDisabledRules)
                .allowedSimilarSSIDs(allowedSimilarSSIDs)
                .restrictedSSIDSubstrings(restrictedSSIDSubstrings)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .isAlerted(isAlerted)
                .statusUnexpectedBSSID(statusUnexpectedBSSID)
                .statusUnexpectedChannel(statusUnexpectedChannel)
                .statusUnexpectedSecuritySuites(statusUnexpectedSecuritySuites)
                .statusUnexpectedFingerprint(statusUnexpectedFingerprint)
                .statusUnexpectedSignalTracks(statusUnexpectedSignalTracks)
                .statusDiscoMonitor(statusDiscoMonitor)
                .statusChannelSwitchMonitor(statusChannelSwitchMonitor)
                .statusMultipleChannels(statusMultipleChannels)
                .statusSimilarSSIDs(statusSimilarSSIDs)
                .statusRestrictedSSIDSubstrings(statusRestrictedSSIDSubstrings)
                .statusUnapprovedClient(statusUnapprovedClient)
                .enabledUnexpectedBSSID(enabledUnexpectedBSSID)
                .enabledUnexpectedChannel(enabledUnexpectedChannel)
                .enabledUnexpectedSecuritySuites(enabledUnexpectedSecuritySuites)
                .enabledUnexpectedFingerprint(enabledUnexpectedFingerprint)
                .enabledUnexpectedSignalTracks(enabledUnexpectedSignalTracks)
                .enabledDiscoMonitor(enabledDiscoMonitor)
                .enabledChannelSwitchMonitor(enabledChannelSwitchMonitor)
                .enabledMultipleChannels(enabledMultipleChannels)
                .enabledSimilarSSIDs(enabledSimilarSSIDs)
                .enabledRestrictedSSIDSubstrings(enabledRestrictedSSIDSubstrings)
                .enabledClientMonitor(enabledClientMonitor)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MonitoredSSIDDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder isEnabled(boolean isEnabled);

        public abstract Builder ssid(String ssid);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder bssids(List<MonitoredBSSIDDetailsResponse> bssids);

        public abstract Builder channels(List<MonitoredChannelResponse> channels);

        public abstract Builder securitySuites(List<MonitoredSecuritySuiteResponse> securitySuites);

        public abstract Builder similarLookingSSIDThreshold(Integer similarLookingSSIDThreshold);

        public abstract Builder channelSwitchBurstThreshold(int channelSwitchBurstThreshold);

        public abstract Builder channelSwitchDisabledRules(List<String> channelSwitchDisabledRules);

        public abstract Builder allowedSimilarSSIDs(List<String> allowedSimilarSSIDs);

        public abstract Builder restrictedSSIDSubstrings(List<RestrictedSSIDSubstringDetailsResponse> restrictedSSIDSubstrings);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract Builder isAlerted(boolean isAlerted);

        public abstract Builder statusUnexpectedBSSID(boolean statusUnexpectedBSSID);

        public abstract Builder statusUnexpectedChannel(boolean statusUnexpectedChannel);

        public abstract Builder statusUnexpectedSecuritySuites(boolean statusUnexpectedSecuritySuites);

        public abstract Builder statusUnexpectedFingerprint(boolean statusUnexpectedFingerprint);

        public abstract Builder statusUnexpectedSignalTracks(boolean statusUnexpectedSignalTracks);

        public abstract Builder statusDiscoMonitor(boolean statusDiscoMonitor);

        public abstract Builder statusChannelSwitchMonitor(boolean statusChannelSwitchMonitor);

        public abstract Builder statusMultipleChannels(boolean statusMultipleChannels);

        public abstract Builder statusSimilarSSIDs(boolean statusSimilarSSIDs);

        public abstract Builder statusRestrictedSSIDSubstrings(boolean statusRestrictedSSIDSubstrings);

        public abstract Builder statusUnapprovedClient(boolean statusUnapprovedClient);

        public abstract Builder enabledUnexpectedBSSID(boolean enabledUnexpectedBSSID);

        public abstract Builder enabledUnexpectedChannel(boolean enabledUnexpectedChannel);

        public abstract Builder enabledUnexpectedSecuritySuites(boolean enabledUnexpectedSecuritySuites);

        public abstract Builder enabledUnexpectedFingerprint(boolean enabledUnexpectedFingerprint);

        public abstract Builder enabledUnexpectedSignalTracks(boolean enabledUnexpectedSignalTracks);

        public abstract Builder enabledDiscoMonitor(boolean enabledDiscoMonitor);

        public abstract Builder enabledChannelSwitchMonitor(boolean enabledChannelSwitchMonitor);

        public abstract Builder enabledMultipleChannels(boolean enabledMultipleChannels);

        public abstract Builder enabledSimilarSSIDs(boolean enabledSimilarSSIDs);

        public abstract Builder enabledRestrictedSSIDSubstrings(boolean enabledRestrictedSSIDSubstrings);

        public abstract Builder enabledClientMonitor(boolean enabledClientMonitor);

        public abstract MonitoredSSIDDetailsResponse build();
    }
}
