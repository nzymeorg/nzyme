package app.nzyme.core.rest.authentication;

import app.nzyme.core.MockNzyme;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.security.authentication.db.UserEntry;
import app.nzyme.core.security.sessions.SessionId;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.*;

public class PreMFAAuthenticationFilterTest extends RESTAuthenticationFilterTestBase {

    @Test
    public void testFilterLetsValidSessionPass() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        PreMFAAuthenticationFilter f = new PreMFAAuthenticationFilter(nzyme);

        createUser("test@example.org", "123123123123");
        UserEntry user = createUser("lennart@example.org", "456456456456");
        String sessionId = createSession(user.uuid(), false);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest(
                "Bearer " + sessionId
        );

        f.filter(ctx);
        assertFalse(ctx.aborted);
    }

    @Test
    public void testFilterLetsSessionWithPassedMFAPass() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        PreMFAAuthenticationFilter f = new PreMFAAuthenticationFilter(nzyme);

        createUser("test@example.org", "123123123123");
        UserEntry user = createUser("lennart@example.org", "456456456456");
        String sessionId = createSession(user.uuid(), false);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest(
                "Bearer " + sessionId
        );

        f.filter(ctx);
        assertFalse(ctx.aborted);
    }

    @Test
    public void testFilterRejectsNotExistingSession() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        PreMFAAuthenticationFilter f = new PreMFAAuthenticationFilter(nzyme);

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
        PreMFAAuthenticationFilter f = new PreMFAAuthenticationFilter(nzyme);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer ");

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsEmptyAuthHeader() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        PreMFAAuthenticationFilter f = new PreMFAAuthenticationFilter(nzyme);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("");

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsUnknownAuthScheme() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        PreMFAAuthenticationFilter f = new PreMFAAuthenticationFilter(nzyme);

        createUser("test@example.org", "123123123123");
        UserEntry user = createUser("lennart@example.org", "456456456456");
        String sessionId = createSession(user.uuid(), false);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest(
                "Wtf " + sessionId
        );

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

    @Test
    public void testFilterRejectsApiKey() throws IOException {
        NzymeNode nzyme = new MockNzyme();
        PreMFAAuthenticationFilter f = new PreMFAAuthenticationFilter(nzyme);

        UserEntry user = createUser("lennart@example.org", "456456456456");
        String key = createApiKey(user.uuid(), null);

        MockHeaderContainerRequest ctx = new MockHeaderContainerRequest("Bearer " + key);

        f.filter(ctx);
        assertTrue(ctx.aborted);
    }

}
