package app.nzyme.core.rest.responses.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class NetworkContextDetailsResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("cidr")
    public abstract String cidr();

    @Nullable
    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("description")
    public abstract String description();

    @Nullable
    @JsonProperty("notes")
    public abstract String notes();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    public static NetworkContextDetailsResponse create(UUID uuid, String cidr, String name, String description, String notes, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .cidr(cidr)
                .name(name)
                .description(description)
                .notes(notes)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NetworkContextDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder cidr(String cidr);

        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder notes(String notes);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract NetworkContextDetailsResponse build();
    }
}
