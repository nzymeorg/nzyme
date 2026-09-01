package app.nzyme.core.rest.responses.bluetooth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class BluetoothDeviceSummaryListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("devices")
    public abstract List<BluetoothDeviceSummaryDetailsResponse> devices();

    public static BluetoothDeviceSummaryListResponse create(long total, List<BluetoothDeviceSummaryDetailsResponse> devices) {
        return builder()
                .total(total)
                .devices(devices)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BluetoothDeviceSummaryListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder devices(List<BluetoothDeviceSummaryDetailsResponse> devices);

        public abstract BluetoothDeviceSummaryListResponse build();
    }
}
