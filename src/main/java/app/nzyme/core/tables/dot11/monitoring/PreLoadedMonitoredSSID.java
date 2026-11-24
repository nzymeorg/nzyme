package app.nzyme.core.tables.dot11.monitoring;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

@AutoValue
public abstract class PreLoadedMonitoredSSID {

    public abstract long id();
    public abstract UUID uuid();
    public abstract String ssid();
    public abstract Map<String, PreLoadedMonitoredBSSID> bssids();
    public abstract List<Integer> channels();
    public abstract List<String> securitySuites();

    public abstract boolean enabledUnexpectedBSSID();
    public abstract boolean enabledUnexpectedChannel();
    public abstract boolean enabledUnexpectedSecuritySuites();
    public abstract boolean enabledUnexpectedFingerprint();
    public abstract boolean enabledUnexpectedSignalTracks();
    public abstract boolean enabledSimilarLookingSSID();
    public abstract boolean enabledSSIDSubstring();

    public abstract Integer detectionConfigSimilarLookingSSIDThreshold();
    public abstract Set<String> allowedSimilarSSIDs();

    public static PreLoadedMonitoredSSID create(long id, UUID uuid, String ssid, Map<String, PreLoadedMonitoredBSSID> bssids, List<Integer> channels, List<String> securitySuites, boolean enabledUnexpectedBSSID, boolean enabledUnexpectedChannel, boolean enabledUnexpectedSecuritySuites, boolean enabledUnexpectedFingerprint, boolean enabledUnexpectedSignalTracks, boolean enabledSimilarLookingSSID, boolean enabledSSIDSubstring, Integer detectionConfigSimilarLookingSSIDThreshold, Set<String> allowedSimilarSSIDs) {
        return builder()
                .id(id)
                .uuid(uuid)
                .ssid(ssid)
                .bssids(bssids)
                .channels(channels)
                .securitySuites(securitySuites)
                .enabledUnexpectedBSSID(enabledUnexpectedBSSID)
                .enabledUnexpectedChannel(enabledUnexpectedChannel)
                .enabledUnexpectedSecuritySuites(enabledUnexpectedSecuritySuites)
                .enabledUnexpectedFingerprint(enabledUnexpectedFingerprint)
                .enabledUnexpectedSignalTracks(enabledUnexpectedSignalTracks)
                .enabledSimilarLookingSSID(enabledSimilarLookingSSID)
                .enabledSSIDSubstring(enabledSSIDSubstring)
                .detectionConfigSimilarLookingSSIDThreshold(detectionConfigSimilarLookingSSIDThreshold)
                .allowedSimilarSSIDs(allowedSimilarSSIDs)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PreLoadedMonitoredSSID.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder uuid(UUID uuid);

        public abstract Builder ssid(String ssid);

        public abstract Builder bssids(Map<String, PreLoadedMonitoredBSSID> bssids);

        public abstract Builder channels(List<Integer> channels);

        public abstract Builder securitySuites(List<String> securitySuites);

        public abstract Builder enabledUnexpectedBSSID(boolean enabledUnexpectedBSSID);

        public abstract Builder enabledUnexpectedChannel(boolean enabledUnexpectedChannel);

        public abstract Builder enabledUnexpectedSecuritySuites(boolean enabledUnexpectedSecuritySuites);

        public abstract Builder enabledUnexpectedFingerprint(boolean enabledUnexpectedFingerprint);

        public abstract Builder enabledUnexpectedSignalTracks(boolean enabledUnexpectedSignalTracks);

        public abstract Builder enabledSimilarLookingSSID(boolean enabledSimilarLookingSSID);

        public abstract Builder enabledSSIDSubstring(boolean enabledSSIDSubstring);

        public abstract Builder detectionConfigSimilarLookingSSIDThreshold(Integer detectionConfigSimilarLookingSSIDThreshold);

        public abstract Builder allowedSimilarSSIDs(Set<String> allowedSimilarSSIDs);

        public abstract PreLoadedMonitoredSSID build();
    }
}
