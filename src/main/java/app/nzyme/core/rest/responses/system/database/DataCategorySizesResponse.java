package app.nzyme.core.rest.responses.system.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class DataCategorySizesResponse {

    @Nullable
    @JsonProperty("bytes")
    public abstract Long bytes();

    @Nullable
    @JsonProperty("rows")
    public abstract Long rows();

    @JsonProperty("rows_estimated")
    public abstract boolean rowsEstimated();

    @JsonProperty("bytes_estimated")
    public abstract boolean bytesEstimated();

    public static DataCategorySizesResponse create(Long bytes, Long rows, boolean rowsEstimated, boolean bytesEstimated) {
        return builder()
                .bytes(bytes)
                .rows(rows)
                .rowsEstimated(rowsEstimated)
                .bytesEstimated(bytesEstimated)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DataCategorySizesResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bytes(Long bytes);
        public abstract Builder rows(Long rows);
        public abstract Builder rowsEstimated(boolean rowsEstimated);
        public abstract Builder bytesEstimated(boolean bytesEstimated);

        public abstract DataCategorySizesResponse build();
    }

}
