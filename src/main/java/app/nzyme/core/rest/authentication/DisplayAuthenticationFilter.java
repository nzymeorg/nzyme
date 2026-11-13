package app.nzyme.core.rest.authentication;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dashboards.DisplayTokens;
import app.nzyme.core.dashboards.db.DisplayEntry;
import com.google.common.net.HttpHeaders;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.Request;

import java.io.IOException;
import java.security.Principal;
import java.util.Optional;

@DisplaySecured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class DisplayAuthenticationFilter implements ContainerRequestFilter {

    private static final Logger LOG = LogManager.getLogger(DisplayAuthenticationFilter.class);

    private static final String AUTHENTICATION_SCHEME = "Bearer";

    private final NzymeNode nzyme;

    @Context
    private jakarta.inject.Provider<Request> requestProvider;

    public DisplayAuthenticationFilter(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        try {
            String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

            if (!RESTAuthenticationFilter.isTokenBasedAuthentication(authorizationHeader)) {
                abortWithUnauthorized(requestContext);
                return;
            }

            String token = authorizationHeader.substring(AUTHENTICATION_SCHEME.length()).trim();

            if (!DisplayTokens.isDisplayToken(token)) {
                abortWithUnauthorized(requestContext);
                return;
            }

            Optional<DisplayEntry> display = nzyme.getDashboards()
                    .findValidDisplayByTokenHash(DisplayTokens.hash(token));

            if (display.isEmpty()
                    || !nzyme.getDashboards().isEnabled(display.get().organizationId(), display.get().tenantId())) {
                abortWithUnauthorized(requestContext);
                return;
            }

            String remoteAddress = remoteAddress();

            if (!display.get().settings().allowsAddress(remoteAddress)) {
                LOG.debug("Blocking display [{}] from address [{}] outside of its allowed networks.",
                        display.get().uuid(), remoteAddress);
                abortWithUnauthorized(requestContext);
                return;
            }

            final SecurityContext currentSecurityContext = requestContext.getSecurityContext();
            requestContext.setSecurityContext(new SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    return new AuthenticatedDisplay(display.get(), remoteAddress);
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
            // Not a 401: the display keeps its content on screen during an outage and only clears it when rejected.
            LOG.warn("Display authentication failed.", e);
            requestContext.abortWith(Response.status(Response.Status.SERVICE_UNAVAILABLE).build());
        }
    }

    @Nullable
    private String remoteAddress() {
        if (requestProvider == null) { // For tests.
            return null;
        }

        // Only the address of the connection. A forwarding header is set by the client and would pass any allowed network.
        String address = requestProvider.get().getRemoteAddr();

        return address != null && InetAddresses.isInetAddress(address) ? address : null;
    }

    private void abortWithUnauthorized(ContainerRequestContext requestContext) {
        requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }

}
