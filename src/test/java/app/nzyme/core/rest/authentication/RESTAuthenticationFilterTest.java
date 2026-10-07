package app.nzyme.core.rest.authentication;

import app.nzyme.core.MockNzyme;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.security.authentication.ApiKeys;
import app.nzyme.core.security.authentication.PasswordHasher;
import app.nzyme.core.security.authentication.db.ApiKeyEntry;
import app.nzyme.core.security.authentication.db.OrganizationEntry;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.core.security.authentication.db.UserEntry;
import app.nzyme.core.security.sessions.SessionId;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.joda.time.DateTime;

import java.io.IOException;

import static org.testng.Assert.*;

public class RESTAuthenticationFilterTest extends RESTAuthenticationFilterTestBase {

    @Test
    public void testFilterLetsValidSessionPass() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        createUser("test@example.org", "123123123123");
        UserEntry user = createUser("lennart@example.org", "456456456456");
        String sessionId = createSession(user.uuid(), true);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest(
                "Bearer " + sessionId
        );

        f.filter(ctx);
        assertFalse(ctx.aborted);
    }

    @Test
    public void testFilterRejectsSessionWithoutPassedMFA() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        createUser("test@example.org", "123123123123");
        UserEntry user = createUser("lennart@example.org", "456456456456");
        String sessionId = createSession(user.uuid(), false);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest(
                "Bearer " + sessionId
        );

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsNotExistingSession() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        createUser("test@example.org", "123123123123");
        createUser("lennart@example.org", "456456456456");

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest(
                "Bearer " + SessionId.createSessionId()
        );

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsEmptySessionId() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer ");

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsEmptyAuthHeader() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("");

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsUnknownAuthScheme() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        createUser("test@example.org", "123123123123");
        UserEntry user = createUser("lennart@example.org", "456456456456");
        String sessionId = createSession(user.uuid(), true);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest(
                "Wtf " + sessionId
        );

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterLetsValidApiKeyPass() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        UserEntry user = createUser("lennart@example.org", "456456456456");
        String key = createApiKey(user.uuid(), null);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + key);

        f.filter(ctx);
        assertFalse(ctx.aborted);

        AuthenticatedUser principal = (AuthenticatedUser) ctx.getSecurityContext().getUserPrincipal();
        assertEquals(principal.getUserId(), user.uuid());
        assertTrue(principal.isApiKeyAuthenticated());
        assertNotNull(principal.getApiKeyId());
        assertNull(principal.getSessionId());
    }

    @Test
    public void testFilterLetsValidApiKeyPassWithoutMfaSession() throws IOException {
        // API keys are not subject to the MFA check, even though the user has MFA enabled.
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        UserEntry user = createUser("lennart@example.org", "456456456456");
        createSession(user.uuid(), false);
        String key = createApiKey(user.uuid(), DateTime.now().plusDays(30));

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + key);

        f.filter(ctx);
        assertFalse(ctx.aborted);
    }

    @Test
    public void testFilterRejectsExpiredApiKey() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        UserEntry user = createUser("lennart@example.org", "456456456456");
        String key = createApiKey(user.uuid(), DateTime.now().minusMinutes(1));

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + key);

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsUnknownApiKey() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        UserEntry user = createUser("lennart@example.org", "456456456456");
        createApiKey(user.uuid(), null);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + ApiKeys.generate());

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsDeletedApiKey() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        UserEntry user = createUser("lennart@example.org", "456456456456");
        String key = createApiKey(user.uuid(), null);

        ApiKeyEntry entry = nzyme.getAuthenticationService().findAllApiKeys(user.uuid()).get(0);
        nzyme.getAuthenticationService().deleteApiKey(user.uuid(), entry.uuid());

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + key);

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsApiKeyOnSessionOnlyResource() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.sessionOnly();

        UserEntry user = createUser("lennart@example.org", "456456456456");
        String key = createApiKey(user.uuid(), null);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + key);

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterLetsSessionPassOnSessionOnlyResource() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.sessionOnly();

        UserEntry user = createUser("lennart@example.org", "456456456456");
        String sessionId = createSession(user.uuid(), true);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + sessionId);

        f.filter(ctx);
        assertFalse(ctx.aborted);

        AuthenticatedUser principal = (AuthenticatedUser) ctx.getSecurityContext().getUserPrincipal();
        assertFalse(principal.isApiKeyAuthenticated());
        assertEquals(principal.getSessionId(), sessionId);
    }

    @Test
    public void testFilterAppliesPermissionLevelToApiKey() throws IOException {
        // A key inherits the user's permissions. A regular user cannot reach a super admin resource with it.
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.superAdminOnly();

        UserEntry user = createUser("lennart@example.org", "456456456456");
        String key = createApiKey(user.uuid(), null);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + key);

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterUpdatesApiKeyLastActivityButNotUserActivity() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();

        UserEntry user = createUser("lennart@example.org", "456456456456");
        String key = createApiKey(user.uuid(), null);

        assertNull(nzyme.getAuthenticationService().findAllApiKeys(user.uuid()).get(0).lastActivity());

        f.filter(new MockHeaderContainerRequest("Bearer " + key));

        assertNotNull(nzyme.getAuthenticationService().findAllApiKeys(user.uuid()).get(0).lastActivity());
        assertNull(nzyme.getAuthenticationService().findUserById(user.uuid()).get().lastActivity());
    }

}
