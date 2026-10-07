package app.nzyme.core.rest.authentication;

import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import jakarta.ws.rs.container.ResourceInfo;

import java.lang.reflect.Method;

/**
 * Points the authentication filter at a method of a small fake resource class, so tests can exercise the
 * annotation handling without a running Jersey container.
 */
public class MockResourceInfo implements ResourceInfo {

    @RESTSecured(PermissionLevel.ANY)
    public static class TestResource {
        public void anyUser() {}

        @SessionOnly
        public void sessionOnly() {}

        @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
        public void superAdminOnly() {}
    }

    private final Method method;

    private MockResourceInfo(String methodName) {
        try {
            this.method = TestResource.class.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static MockResourceInfo anyUser() {
        return new MockResourceInfo("anyUser");
    }

    public static MockResourceInfo sessionOnly() {
        return new MockResourceInfo("sessionOnly");
    }

    public static MockResourceInfo superAdminOnly() {
        return new MockResourceInfo("superAdminOnly");
    }

    @Override
    public Method getResourceMethod() {
        return method;
    }

    @Override
    public Class<?> getResourceClass() {
        return TestResource.class;
    }

}
