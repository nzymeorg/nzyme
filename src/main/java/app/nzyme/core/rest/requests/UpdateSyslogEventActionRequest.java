package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for updating a syslog event action.")
@AutoValue
public abstract class UpdateSyslogEventActionRequest {


    @NotEmpty
    public abstract String name();

    @NotEmpty
    public abstract String description();

    @NotEmpty
    public abstract String protocol();

    @NotEmpty
    public abstract String syslogHostname();

    @NotEmpty
    public abstract String host();

    @Min(1)
    @Max(65535)
    public abstract int port();

    @JsonCreator
    public static UpdateSyslogEventActionRequest create(@Schema(description = "New name of the event action.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                        @JsonProperty("name") String name,
                                                        @Schema(description = "New description of the event action.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                        @JsonProperty("description") String description,
                                                        @Schema(description = "Syslog protocol to use.", allowableValues = {"UDP_RFC5424"}, requiredMode = Schema.RequiredMode.REQUIRED)
                                                        @JsonProperty("protocol") String protocol,
                                                        @Schema(description = "Hostname Nzyme reports as the source of the syslog messages.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                        @JsonProperty("syslog_hostname") String syslogHostname,
                                                        @Schema(description = "Hostname or IP address of the syslog receiver.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                        @JsonProperty("host") String host,
                                                        @Schema(description = "Port of the syslog receiver. Between 1 and 65535.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                        @JsonProperty("port") int port) {
        return builder()
                .name(name)
                .description(description)
                .protocol(protocol)
                .syslogHostname(syslogHostname)
                .host(host)
                .port(port)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateSyslogEventActionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(@NotEmpty String name);

        public abstract Builder description(@NotEmpty String description);

        public abstract Builder protocol(@NotEmpty String protocol);

        public abstract Builder syslogHostname(@NotEmpty String syslogHostname);

        public abstract Builder host(@NotEmpty String host);

        public abstract Builder port(@Min(1) @Max(65535) int port);

        public abstract UpdateSyslogEventActionRequest build();
    }
}
