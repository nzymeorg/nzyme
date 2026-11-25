package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

@AutoValue
public abstract class InternalNetworksUpdateRequest {

    @Nullable
    public abstract List<String> networks();

    @JsonCreator
    public static InternalNetworksUpdateRequest create(@Schema(description = "The complete list of internal networks in CIDR notation, IPv4 or IPv6. Replaces the current list. At most 50 networks, none wider than a /8 or, for IPv6, a /16.") @JsonProperty("networks") List<String> networks) {
        return new AutoValue_InternalNetworksUpdateRequest(networks);
    }

}
