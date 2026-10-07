package app.nzyme.core.rest.authentication;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.security.authentication.ApiKeys;
import app.nzyme.core.security.authentication.db.UserEntry;
import app.nzyme.core.security.sessions.db.SessionEntry;
import com.google.common.net.HttpHeaders;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.Request;

import java.io.IOException;
import java.net.InetAddress;
import java.security.Principal;
import java.util.Optional;

@PreMFASecured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class PreMFAAuthenticationFilter implements ContainerRequestFilter {

    private static final Logger LOG = LogManager.getLogger(PreMFAAuthenticationFilter.class);

    private final NzymeNode nzyme;

    @Context
    private jakarta.inject.Provider<Request> requestProvider;

    public static final String AUTHENTICATION_SCHEME = "Bearer";

    public PreMFAAuthenticationFilter(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String remoteIp = "";
        if (requestProvider != null) { // For tests.
            final Request request = requestProvider.get();
            remoteIp = request.getHeader("X-Forwarded-For") == null
                    ? request.getRemoteAddr() : request.getHeader("X-Forwarded-For").split(",")[0];

            InetAddressValidator inetValidator = new InetAddressValidator();
            if (!inetValidator.isValid(remoteIp)) {
                LOG.warn("Invalid remote IP or X-Forwarded-For header in session request: [{}]. Aborting.", remoteIp);
                abortWithUnauthorized(requestContext);
            }
        }

        try {
            String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

            if (!isTokenBasedAuthentication(authorizationHeader)) {
                abortWithUnauthorized(requestContext);
                return;
            }

            String sessionId = authorizationHeader.substring(AUTHENTICATION_SCHEME.length()).trim();

            // API keys can never be used for pre-MFA (session setup) resources.
            if (ApiKeys.isApiKey(sessionId)) {
                abortWithUnauthorized(requestContext);
                return;
            }

            // Check if session exists.
            Optional<SessionEntry> session = nzyme.getAuthenticationService().findSessionWithOrWithoutPassedMFABySessionId(sessionId);
            if (session.isEmpty()) {
                abortWithUnauthorized(requestContext);
                return;
            }

            Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(session.get().userId());

            if (user.isEmpty()) {
                LOG.error("Session referenced user that doesn't exist. Aborting.");
                abortWithUnauthorized(requestContext);
                return;
            }

            // Authenticated. Set last activity information.
            nzyme.getAuthenticationService().updateLastUserActivity(
                    user.get().uuid(),
                    remoteIp,
                    nzyme.getGeoIpService().lookup(InetAddress.getByName(remoteIp))
                            .orElse(null)
            );

            // Set new security context for later use in resources.
            final SecurityContext currentSecurityContext = requestContext.getSecurityContext();
            requestContext.setSecurityContext(new SecurityContext() {

                @Override
                public Principal getUserPrincipal() {
                    UserEntry u = user.get();
                    return new AuthenticatedUser(
                            u.uuid(),
                            session.get().sessionId(),
                            u.email(),
                            session.get().createdAt(),
                            u.organizationId(),
                            u.tenantId(),
                            u.isOrganizationAdmin(),
                            u.isSuperAdmin(),
                            u.accessAllTenantTaps()
                    );
                }

                @Override
                public boolean isUserInRole(String role) {
                    return true;
                }

                @Override
                public boolean isSecure() {
                    return currentSecurityContext.isSecure();
                }

                @Override
                public String getAuthenticationScheme() {
                    return AUTHENTICATION_SCHEME;
                }

            });
        } catch (Exception e) {
            LOG.warn("Session ID validation failed.", e);
            abortWithUnauthorized(requestContext);
            return;
        }
    }

    public static boolean isTokenBasedAuthentication(String authorizationHeader) {
        return authorizationHeader != null && authorizationHeader.toLowerCase().startsWith(AUTHENTICATION_SCHEME.toLowerCase() + " ");
    }

    private void abortWithUnauthorized(ContainerRequestContext requestContext) {
        requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }

}
