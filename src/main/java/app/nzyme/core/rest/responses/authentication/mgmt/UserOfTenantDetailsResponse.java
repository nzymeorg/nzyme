package app.nzyme.core.rest.responses.authentication.mgmt;

import app.nzyme.core.rest.responses.authentication.apikeys.ApiKeyDetailsResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class UserOfTenantDetailsResponse {

    @JsonProperty("user")
    public abstract UserDetailsResponse user();

    @JsonProperty("is_deletable")
    public abstract boolean isDeletable();

    @JsonProperty("api_keys")
    public abstract List<ApiKeyDetailsResponse> apiKeys();

    public static UserOfTenantDetailsResponse create(UserDetailsResponse user, boolean isDeletable, List<ApiKeyDetailsResponse> apiKeys) {
        return builder()
                .user(user)
                .isDeletable(isDeletable)
                .apiKeys(apiKeys)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UserOfTenantDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder user(UserDetailsResponse user);

        public abstract Builder isDeletable(boolean isDeletable);

        public abstract Builder apiKeys(List<ApiKeyDetailsResponse> apiKeys);

        public abstract UserOfTenantDetailsResponse build();
    }
}
