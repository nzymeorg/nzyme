package app.nzyme.core.rest.authentication;

import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.security.Principal;
import java.util.UUID;

public class AuthenticatedUser implements Principal {

    private final UUID userId;
    private final String email;

    // Set when authenticated via interactive session.
    @Nullable
    private final String sessionId;
    @Nullable
    private final DateTime sessionCreatedAt;

    // Set when authenticated via API key.
    @Nullable
    private final UUID apiKeyId;

    @Nullable
    private final UUID organizationId;

    @Nullable
    private final UUID tenantId;

    private final boolean isOrganizationAdministrator;
    private final boolean isSuperAdministrator;

    public boolean accessAllTenantTaps;

    public AuthenticatedUser(UUID userId,
                             String sessionId,
                             String email,
                             DateTime sessionCreatedAt,
                             @Nullable UUID organizationId,
                             @Nullable UUID tenantId,
                             final boolean isOrganizationAdministrator,
                             boolean isSuperAdministrator,
                             boolean accessAllTenantTaps) {
        this(userId, sessionId, sessionCreatedAt, null, email, organizationId, tenantId,
                isOrganizationAdministrator, isSuperAdministrator, accessAllTenantTaps);
    }

    public AuthenticatedUser(UUID userId,
                             @Nullable String sessionId,
                             @Nullable DateTime sessionCreatedAt,
                             @Nullable UUID apiKeyId,
                             String email,
                             @Nullable UUID organizationId,
                             @Nullable UUID tenantId,
                             final boolean isOrganizationAdministrator,
                             boolean isSuperAdministrator,
                             boolean accessAllTenantTaps) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.sessionCreatedAt = sessionCreatedAt;
        this.apiKeyId = apiKeyId;
        this.email = email;
        this.organizationId = organizationId;
        this.tenantId = tenantId;
        this.isOrganizationAdministrator = isOrganizationAdministrator;
        this.isSuperAdministrator = isSuperAdministrator;
        this.accessAllTenantTaps = accessAllTenantTaps;
    }

    public UUID getUserId() {
        return userId;
    }

    @Nullable
    public String getSessionId() {
        return sessionId;
    }

    @Nullable
    public UUID getApiKeyId() {
        return apiKeyId;
    }

    public boolean isApiKeyAuthenticated() {
        return apiKeyId != null;
    }

    public String getEmail() {
        return email;
    }

    @Nullable
    public DateTime getSessionCreatedAt() {
        return sessionCreatedAt;
    }

    @Nullable
    public UUID getOrganizationId() {
        return organizationId;
    }

    @Nullable
    public UUID getTenantId() {
        return tenantId;
    }

    public boolean isOrganizationAdministrator() {
        return isOrganizationAdministrator;
    }

    public boolean isSuperAdministrator() {
        return isSuperAdministrator;
    }

    public boolean isAccessAllTenantTaps() {
        return accessAllTenantTaps;
    }

    @Override
    public String getName() {
        return getEmail();
    }

}
