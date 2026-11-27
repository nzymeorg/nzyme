package app.nzyme.core.rest.responses.dot11;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class AccessPointSiblingResponse {

    @JsonProperty("bssid")
    public abstract Dot11MacAddressResponse bssid();

    @Schema(description = "Up to 5 SSIDs the BSSID advertised in the time range, those in the most reports first. "
            + "Empty if it only advertised without a network name.")
    @JsonProperty("ssids")
    public abstract List<String> ssids();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static AccessPointSiblingResponse create(Dot11MacAddressResponse bssid, List<String> ssids, DateTime lastSeen) {
        return builder()
                .bssid(bssid)
                .ssids(ssids)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AccessPointSiblingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bssid(Dot11MacAddressResponse bssid);

        public abstract Builder ssids(List<String> ssids);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract AccessPointSiblingResponse build();
    }

}
