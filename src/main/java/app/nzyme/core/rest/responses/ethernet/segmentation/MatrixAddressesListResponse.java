package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@AutoValue
public abstract class MatrixAddressesListResponse {

    @Schema(description = "Number of all addresses that pass the filters, of which this is one page.")
    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("addresses")
    public abstract List<MatrixAddressResponse> addresses();

    @Schema(description = "Unanswered sessions that went to addresses of the group that are not stored and "
            + "therefore not listed.")
    @JsonProperty("folded_sessions")
    public abstract long foldedSessions();

    @Schema(description = "Unanswered connections of one source to more than this many destinations within one "
            + "evaluation window are stored as one flow per destination segment, without an address.")
    @JsonProperty("destinations_folded_above")
    public abstract int destinationsFoldedAbove();

    public static MatrixAddressesListResponse create(long total,
                                                     List<MatrixAddressResponse> addresses,
                                                     long foldedSessions,
                                                     int destinationsFoldedAbove) {
        return builder()
                .total(total)
                .addresses(addresses)
                .foldedSessions(foldedSessions)
                .destinationsFoldedAbove(destinationsFoldedAbove)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MatrixAddressesListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder addresses(List<MatrixAddressResponse> addresses);
        public abstract Builder foldedSessions(long foldedSessions);
        public abstract Builder destinationsFoldedAbove(int destinationsFoldedAbove);

        public abstract MatrixAddressesListResponse build();
    }

}
