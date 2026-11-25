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

package app.nzyme.core.rest;

import app.nzyme.core.rest.authentication.DisplaySecured;
import app.nzyme.core.rest.authentication.PreMFASecured;
import app.nzyme.core.rest.authentication.PrometheusBasicAuthSecured;
import app.nzyme.core.rest.authentication.TapSecured;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.testng.annotations.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.fail;

public class ResourcesRequireAuthenticationTest {

    private static final List<Class<? extends Annotation>> HTTP_METHODS = ImmutableList.of(
            GET.class, POST.class, PUT.class, DELETE.class, PATCH.class
    );

    private static final List<Class<? extends Annotation>> SECURED = ImmutableList.of(
            RESTSecured.class, PreMFASecured.class, TapSecured.class, DisplaySecured.class,
            PrometheusBasicAuthSecured.class
    );

    private static final List<String> NOT_AUTHENTICATED = ImmutableList.of(
            "WebInterfaceAssetsResource.getIndex",
            "WebInterfaceAssetsResource.get",
            "PingResource.ping",
            "ApiDocsResource.spec",
            "InitialUserResource.createInitialUser",
            "AuthenticationResource.createSession"
    );

    private static final List<String> PREMFA_AUTHENTICATED = ImmutableList.of(
            "AuthenticationResource.getSessionInformation",
            "AuthenticationResource.touchSession",
            "AuthenticationResource.initializeMfaSetup",
            "AuthenticationResource.verifyMfaSetup",
            "AuthenticationResource.completeMfaSetup",
            "AuthenticationResource.verifyMfa",
            "AuthenticationResource.mfaRecoveryCodeValidation"
    );

    private static final Map<String, Class<? extends Annotation>> NOT_USER_AUTHENTICATED = ImmutableMap.of(
            "StatusResource", TapSecured.class,
            "TablesResource", TapSecured.class,
            "DisplayResource", DisplaySecured.class,
            "PrometheusResource.metrics", PrometheusBasicAuthSecured.class
    );

    @Test
    public void testAllResources() {
        Reflections r = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("app.nzyme.core.rest.resources"))
                .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner())
        );

        Set<String> seen = Sets.newHashSet();
        for (Class<?> clazz : r.getTypesAnnotatedWith(Path.class)) {
            for (Method method : clazz.getMethods()) {
                if (HTTP_METHODS.stream().noneMatch(method::isAnnotationPresent)) {
                    continue;
                }

                String resource = method.getDeclaringClass().getSimpleName();
                String id = resource + "." + method.getName();
                seen.add(id);
                seen.add(resource);

                if (NOT_AUTHENTICATED.contains(id)) {
                    for (Class<? extends Annotation> secured : SECURED) {
                        if (has(method, clazz, secured)) {
                            fail("REST resource " + id + " is listed as not authenticated but is annotated with @"
                                    + secured.getSimpleName() + ".");
                        }
                    }

                    continue;
                }

                Class<? extends Annotation> expected = PREMFA_AUTHENTICATED.contains(id)
                        ? PreMFASecured.class
                        : NOT_USER_AUTHENTICATED.getOrDefault(id,
                                NOT_USER_AUTHENTICATED.getOrDefault(resource, RESTSecured.class));

                if (!has(method, clazz, expected)) {
                    fail("REST resource " + id + " is not annotated with @" + expected.getSimpleName() + ".");
                }

                // A second annotation would add a second way in.
                for (Class<? extends Annotation> secured : SECURED) {
                    if (secured != expected && has(method, clazz, secured)) {
                        fail("REST resource " + id + " is annotated with @" + secured.getSimpleName()
                                + " but should only be annotated with @" + expected.getSimpleName() + ".");
                    }
                }
            }
        }

        if (seen.isEmpty()) {
            fail("Did not check any resources. Wrong package name?");
        }

        // An entry for an endpoint that is gone would exempt the next one of that name.
        for (String listed : Sets.union(
                Sets.union(Sets.newHashSet(NOT_AUTHENTICATED), Sets.newHashSet(PREMFA_AUTHENTICATED)),
                NOT_USER_AUTHENTICATED.keySet())) {
            if (!seen.contains(listed)) {
                fail("Listed REST resource " + listed + " does not exist.");
            }
        }
    }

    private static boolean has(Method method, Class<?> clazz, Class<? extends Annotation> annotation) {
        return method.isAnnotationPresent(annotation) || clazz.isAnnotationPresent(annotation);
    }

}
