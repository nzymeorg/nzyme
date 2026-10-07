package app.nzyme.core.rest.requests;

import app.nzyme.core.rest.constraints.MacAddress;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "A BSSID and its fingerprints inside a monitored WiFi network data import.")
@AutoValue
public abstract class ImportMonitoredNetworkDataBSSIDRequest {

    @MacAddress
    public abstract String bssid();

    @NotNull
    public abstract List<String> fingerprints();

    @JsonCreator
    public static ImportMonitoredNetworkDataBSSIDRequest create(@Schema(description = "BSSID MAC address like \"00:11:22:33:44:55\".", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                @JsonProperty("bssid") String bssid,
                                                                @Schema(description = "Expected WiFi fingerprints of this BSSID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                @JsonProperty("fingerprints") List<String> fingerprints) {
        return builder()
                .bssid(bssid)
                .fingerprints(fingerprints)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ImportMonitoredNetworkDataBSSIDRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bssid(String bssid);

        public abstract Builder fingerprints(@NotNull List<String> fingerprints);

        public abstract ImportMonitoredNetworkDataBSSIDRequest build();
    }

}
