package app.nzyme.core.rest.authentication;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.*;
import java.util.function.Predicate;

public class MockHeaderContainerRequest implements ContainerRequestContext {

    private final String value;
    public boolean aborted = false;

    public MockHeaderContainerRequest(String value) {
        this.value = value;
    }

    @Override
    public Object getProperty(String name) {
        return null;
    }

    @Override
    public Collection<String> getPropertyNames() {
        return null;
    }

    @Override
    public void setProperty(String name, Object object) {

    }

    @Override
    public void removeProperty(String name) {

    }

    @Override
    public UriInfo getUriInfo() {
        // Minimal UriInfo: only getPath() is used by the filters.
        return (UriInfo) Proxy.newProxyInstance(
                UriInfo.class.getClassLoader(),
                new Class[]{UriInfo.class},
                (proxy, method, args) -> method.getName().equals("getPath") ? "api/test" : null
        );
    }

    @Override
    public void setRequestUri(URI requestUri) {

    }

    @Override
    public void setRequestUri(URI baseUri, URI requestUri) {

    }

    @Override
    public Request getRequest() {
        return null;
    }

    @Override
    public String getMethod() {
        return null;
    }

    @Override
    public void setMethod(String method) {

    }

    @Override
    public MultivaluedMap<String, String> getHeaders() {
        return null;
    }

    @Override
    public String getHeaderString(String name) {
        return value;
    }

    @Override
    public boolean containsHeaderString(String s, String s1, Predicate<String> predicate) {
        return false;
    }

    @Override
    public Date getDate() {
        return null;
    }

    @Override
    public Locale getLanguage() {
        return null;
    }

    @Override
    public int getLength() {
        return 0;
    }

    @Override
    public MediaType getMediaType() {
        return null;
    }

    @Override
    public List<MediaType> getAcceptableMediaTypes() {
        return null;
    }

    @Override
    public List<Locale> getAcceptableLanguages() {
        return null;
    }

    @Override
    public Map<String, Cookie> getCookies() {
        return null;
    }

    @Override
    public boolean hasEntity() {
        return false;
    }

    @Override
    public InputStream getEntityStream() {
        return null;
    }

    @Override
    public void setEntityStream(InputStream input) {

    }

    @Override
    public SecurityContext getSecurityContext() {
        return null;
    }

    @Override
    public void setSecurityContext(SecurityContext context) {

    }

    @Override
    public void abortWith(Response response) {
        aborted = true;
    }
}
