package app.nzyme.core.rest.authentication;

import app.nzyme.core.MockNzyme;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.dashboards.DisplaySettings;
import app.nzyme.core.dashboards.DisplayTokens;
import app.nzyme.core.security.authentication.ApiKeys;
import app.nzyme.core.security.authentication.db.OrganizationEntry;
import app.nzyme.core.security.authentication.db.TenantEntry;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class DisplayAuthenticationFilterTest {

    @BeforeMethod
    public void clean() {
        NzymeNode nzyme = new MockNzyme();

        nzyme.getDatabase().useHandle(handle -> handle.createUpdate("DELETE FROM dashboard_displays").execute());
        nzyme.getDatabase().useHandle(handle -> handle.createUpdate("DELETE FROM auth_users").execute());
        nzyme.getDatabase().useHandle(handle -> handle.createUpdate("DELETE FROM auth_tenants").execute());
        nzyme.getDatabase().useHandle(handle -> handle.createUpdate("DELETE FROM auth_organizations").execute());
    }

    private String buildDisplay(NzymeNode nzyme, List<String> allowedNetworks, @Nullable DateTime expiresAt) {
        OrganizationEntry org = nzyme.getAuthenticationService()
                .createOrganization("test org", "test org");

        TenantEntry tenant = nzyme.getAuthenticationService()
                .createTenant(org.uuid(), "test tenant", "test tenant", 720, 15, 5);

        DisplaySettings settings = DisplaySettings.validated("test display", null, null, null, null, null, null,
                null, null, null, allowedNetworks, null, null, expiresAt);

        String token = DisplayTokens.generate();
        nzyme.getDashboards().createDisplay(org.uuid(), tenant.uuid(), settings, DisplayTokens.hash(token), List.of());

        return token;
    }

    private boolean aborted(NzymeNode nzyme, String authorizationHeader) throws IOException {
        DisplayAuthenticationFilter f = new DisplayAuthenticationFilter(nzyme);
        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest(authorizationHeader);
        f.filter(ctx);

        return ctx.aborted;
    }

    @Test
    public void testFilterLetsValidTokenPass() throws IOException {
        NzymeNode nzyme = new MockNzyme();

        buildDisplay(nzyme, List.of(), null);
        String token = buildDisplay(nzyme, List.of(), DateTime.now().plusDays(1));

        assertFalse(aborted(nzyme, "Bearer " + token));
    }

    @Test
    public void testFilterRejectsUnknownToken() throws IOException {
        NzymeNode nzyme = new MockNzyme();

        buildDisplay(nzyme, List.of(), null);

        assertTrue(aborted(nzyme, "Bearer " + DisplayTokens.generate()));
    }

    @Test
    public void testFilterRejectsTokenHash() throws IOException {
        NzymeNode nzyme = new MockNzyme();

        String token = buildDisplay(nzyme, List.of(), null);

        assertTrue(aborted(nzyme, "Bearer " + DisplayTokens.hash(token)));
    }

    @Test
    public void testFilterRejectsOtherCredentialTypes() throws IOException {
        NzymeNode nzyme = new MockNzyme();

        buildDisplay(nzyme, List.of(), null);

        assertTrue(aborted(nzyme, "Bearer " + ApiKeys.generate()));
        assertTrue(aborted(nzyme, "Bearer "));
        assertTrue(aborted(nzyme, "Basic Zm9vOmJhcg=="));
        assertTrue(aborted(nzyme, null));
    }

    @Test
    public void testFilterRejectsExpiredToken() throws IOException {
        NzymeNode nzyme = new MockNzyme();

        String token = buildDisplay(nzyme, List.of(), DateTime.now().minusMinutes(1));

        assertTrue(aborted(nzyme, "Bearer " + token));
    }

    @Test
    public void testFilterRejectsReplacedToken() throws IOException {
        NzymeNode nzyme = new MockNzyme();

        String token = buildDisplay(nzyme, List.of(), null);
        long displayId = nzyme.getDashboards().findValidDisplayByTokenHash(DisplayTokens.hash(token)).orElseThrow().id();

        String replacement = DisplayTokens.generate();
        nzyme.getDashboards().replaceDisplayToken(displayId, DisplayTokens.hash(replacement));

        assertTrue(aborted(nzyme, "Bearer " + token));
        assertFalse(aborted(nzyme, "Bearer " + replacement));
    }

    @Test
    public void testFilterRejectsUnknownAddressWithAllowedNetworks() throws IOException {
        // No request is injected in tests, so the remote address is unknown.
        NzymeNode nzyme = new MockNzyme();

        String token = buildDisplay(nzyme, List.of("10.0.0.0/8"), null);

        assertTrue(aborted(nzyme, "Bearer " + token));
    }

    @Test
    public void testUserFilterRejectsDisplayToken() throws IOException {
        NzymeNode nzyme = new MockNzyme();

        String token = buildDisplay(nzyme, List.of(), null);

        RESTAuthenticationFilter f = new RESTAuthenticationFilter(nzyme);
        f.resourceInfo = MockResourceInfo.anyUser();
        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + token);
        f.filter(ctx);

        assertTrue(ctx.aborted);
    }

}
