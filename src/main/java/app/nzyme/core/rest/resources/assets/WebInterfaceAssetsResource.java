/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.rest.resources.assets;

import io.swagger.v3.oas.annotations.Hidden;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import app.nzyme.core.rest.web.IndexHtmlGenerator;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.*;

import javax.activation.MimetypesFileTypeMap;
import javax.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.MoreObjects.firstNonNull;

@Hidden // Not part of the user-facing REST API.
@Path("/")
public class WebInterfaceAssetsResource {

    @Inject
    private MimetypesFileTypeMap mimeTypes;

    @Inject
    private IndexHtmlGenerator indexHtmlGenerator;

    private final LoadingCache<URI, FileSystem> fileSystemCache;

    public WebInterfaceAssetsResource() {
        this.fileSystemCache = CacheBuilder.newBuilder()
                .maximumSize(1024)
                .build(new CacheLoader<URI, FileSystem>() {
                    @Override
                    public FileSystem load(@Nonnull URI key) throws Exception {
                        try {
                            return FileSystems.getFileSystem(key);
                        } catch (FileSystemNotFoundException e) {
                            try {
                                return FileSystems.newFileSystem(key, Collections.emptyMap());
                            } catch (FileSystemAlreadyExistsException f) {
                                return FileSystems.getFileSystem(key);
                            }
                        }
                    }
                });
    }

    @GET
    @Path("index.html")
    public Response getIndex(@Context HttpHeaders headers) {
        return getDefaultResponse(headers);
    }

    @GET
    @Path("/{s:.*}")
    public Response getIndex(@Context ContainerRequest request, @Context HttpHeaders headers) {
        return get(request, headers, request.getRequestUri().getPath());
    }

    @Path("/assets/{filename: .*}")
    @GET
    public Response get(@Context Request request, @Context HttpHeaders headers, @PathParam("filename") String filename) {
        if (filename == null || filename.isEmpty() || "/".equals(filename) || "index.html".equals(filename)) {
            return getDefaultResponse(headers);
        }
        try {
            final URL resourceUrl = getResourceUri(filename, this.getClass());
            return getResponse(request, filename, resourceUrl);
        } catch (IOException | URISyntaxException e) {
            return getDefaultResponse(headers);
        }
    }

    private Response getResponse(Request request, String filename, URL resourceUrl) throws IOException, URISyntaxException {
        final URI uri = resourceUrl.toURI();

        final java.nio.file.Path path;
        final byte[] fileContents;
        switch (resourceUrl.getProtocol()) {
            case "file": {
                path = Paths.get(uri);
                fileContents = Files.readAllBytes(path);
                break;
            }
            case "jar": {
                final FileSystem fileSystem = fileSystemCache.getUnchecked(uri);
                path = fileSystem.getPath("web-interface/assets/" + filename);
                fileContents = Resources.toByteArray(resourceUrl);
                break;
            }
            default:
                throw new IllegalArgumentException("Not a JAR or local file: " + resourceUrl);
        }


        final FileTime lastModifiedTime = Files.getLastModifiedTime(path);
        final Date lastModified = Date.from(lastModifiedTime.toInstant());
        final HashCode hashCode = Hashing.sha256().hashBytes(fileContents);
        final EntityTag entityTag = new EntityTag(hashCode.toString());

        final Response.ResponseBuilder response = request.evaluatePreconditions(lastModified, entityTag);
        if (response != null) {
            return response.build();
        }

        final String contentType = firstNonNull(mimeTypes.getContentType(filename), MediaType.APPLICATION_OCTET_STREAM);
        final CacheControl cacheControl = new CacheControl();
        cacheControl.setMaxAge((int) TimeUnit.DAYS.toSeconds(365));
        cacheControl.setNoCache(false);
        cacheControl.setPrivate(false);

        return Response
                .ok(fileContents, contentType)
                .tag(entityTag)
                .cacheControl(cacheControl)
                .lastModified(lastModified)
                .build();
    }

    private URL getResourceUri(String filename, Class<?> aClass) throws FileNotFoundException {
        filename = "/web-interface/assets/" + filename;
        final URL resourceUrl = aClass.getResource(filename);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Resource file " + filename + " not found.");
        }
        return resourceUrl;
    }

    private Response getDefaultResponse(HttpHeaders headers) {
        return Response
                .ok(indexHtmlGenerator.get(headers.getRequestHeaders()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                .header("X-UA-Compatible", "IE=edge")
                .build();
    }

}
