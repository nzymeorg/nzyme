package app.nzyme.core.rest.responses.ethernet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.UUID;

@AutoValue
public abstract class EthernetMacAddressContextResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @Nullable
    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @Nullable
    @JsonProperty("notes")
    public abstract String notes();

    public static EthernetMacAddressContextResponse create(UUID uuid, String name, String description, String notes) {
        return builder()
                .uuid(uuid)
                .name(name)
                .description(description)
                .notes(notes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_EthernetMacAddressContextResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder notes(String notes);

        public abstract EthernetMacAddressContextResponse build();
    }
}
