package app.nzyme.core.rest.responses.system.database.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class DatabaseIndexResponse {

    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("table")
    public abstract String table();

    @JsonProperty("bytes")
    public abstract long bytes();

    @JsonProperty("scans")
    public abstract long scans();

    @JsonProperty("primary")
    public abstract boolean primary();

    @JsonProperty("unique")
    public abstract boolean unique();

    @JsonProperty("valid")
    public abstract boolean valid();

    @Nullable
    @JsonProperty("bloat_percent")
    public abstract Double bloatPercent();

    @JsonProperty("bloat_bytes")
    public abstract long bloatBytes();

    public static DatabaseIndexResponse create(String name, String table, long bytes, long scans, boolean primary, boolean unique, boolean valid, Double bloatPercent, long bloatBytes) {
        return builder()
                .name(name)
                .table(table)
                .bytes(bytes)
                .scans(scans)
                .primary(primary)
                .unique(unique)
                .valid(valid)
                .bloatPercent(bloatPercent)
                .bloatBytes(bloatBytes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseIndexResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder table(String table);
        public abstract Builder bytes(long bytes);
        public abstract Builder scans(long scans);
        public abstract Builder primary(boolean primary);
        public abstract Builder unique(boolean unique);
        public abstract Builder valid(boolean valid);
        public abstract Builder bloatPercent(Double bloatPercent);
        public abstract Builder bloatBytes(long bloatBytes);

        public abstract DatabaseIndexResponse build();
    }

}
