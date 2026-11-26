package app.nzyme.core.rest.responses.dot11;

import app.nzyme.core.dot11.db.BSSIDClientDetails;
import app.nzyme.core.rest.responses.shared.TapBasedSignalStrengthResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class BSSIDDetailsResponse {

    @JsonProperty("summary")
    public abstract BSSIDSummaryDetailsResponse summary();

    @JsonProperty("clients")
    public abstract List<BSSIDClientDetails> clients();

    @JsonProperty("signal_strength")
    public abstract List<TapBasedSignalStrengthResponse> signalStrength();

    @JsonProperty("frequencies")
    public abstract List<Integer> frequencies();

    @JsonProperty("channels")
    public abstract List<Integer> channels();

    public static BSSIDDetailsResponse create(BSSIDSummaryDetailsResponse summary, List<BSSIDClientDetails> clients, List<TapBasedSignalStrengthResponse> signalStrength, List<Integer> frequencies, List<Integer> channels) {
        return builder()
                .summary(summary)
                .clients(clients)
                .signalStrength(signalStrength)
                .frequencies(frequencies)
                .channels(channels)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BSSIDDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder summary(BSSIDSummaryDetailsResponse summary);

        public abstract Builder clients(List<BSSIDClientDetails> clients);

        public abstract Builder signalStrength(List<TapBasedSignalStrengthResponse> signalStrength);

        public abstract Builder frequencies(List<Integer> frequencies);

        public abstract Builder channels(List<Integer> channels);

        public abstract BSSIDDetailsResponse build();
    }
}
