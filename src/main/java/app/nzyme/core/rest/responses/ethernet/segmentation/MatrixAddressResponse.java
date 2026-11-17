package app.nzyme.core.rest.responses.ethernet.segmentation;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class MatrixAddressResponse {

    @Schema(description = "The address with what is known about it. Its MAC address is the one of the asset "
            + "that used the address in the time range. It is not set if no asset or more than one asset did. "
            + "Not set if the address could not be looked up.")
    @Nullable
    @JsonProperty("address")
    public abstract L4AddressResponse address();

    @JsonProperty("address_text")
    public abstract String addressText();

    @Schema(description = "Sessions the address opened.")
    @JsonProperty("sessions_from")
    public abstract long sessionsFrom();

    @Schema(description = "Sessions that went to the address.")
    @JsonProperty("sessions_to")
    public abstract long sessionsTo();

    @Schema(description = "Sessions that went to the address and that it answered.")
    @JsonProperty("answered")
    public abstract long answered();

    @Schema(description = "The taps that recorded traffic of the address in the time range.")
    @JsonProperty("tap_uuids")
    public abstract List<UUID> tapUuids();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static MatrixAddressResponse create(L4AddressResponse address,
                                               String addressText,
                                               long sessionsFrom,
                                               long sessionsTo,
                                               long answered,
                                               List<UUID> tapUuids,
                                               DateTime firstSeen,
                                               DateTime lastSeen) {
        return builder()
                .address(address)
                .addressText(addressText)
                .sessionsFrom(sessionsFrom)
                .sessionsTo(sessionsTo)
                .answered(answered)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MatrixAddressResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(L4AddressResponse address);
        public abstract Builder addressText(String addressText);
        public abstract Builder sessionsFrom(long sessionsFrom);
        public abstract Builder sessionsTo(long sessionsTo);
        public abstract Builder answered(long answered);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract MatrixAddressResponse build();
    }

}
