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

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionAlertService;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.rest.resources.ethernet.ReconAddressResponses;
import app.nzyme.core.detection.alerts.db.DetectionAlertAttributeEntry;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.rest.responses.alerts.DetectionAlertDetailsResponse;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.MultivaluedMap;
import org.joda.time.DateTime;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RestTools {

    public static final String OVERRIDE_HEADER = "X-Nzyme-URL";

    public static URI buildExternalUri(@NotNull MultivaluedMap<String, String> httpHeaders, @NotNull URI defaultUri) {
        Optional<URI> externalUri = Optional.empty();
        final List<String> headers = httpHeaders.get(OVERRIDE_HEADER);
        if (headers != null && !headers.isEmpty()) {
            externalUri = headers.stream()
                    .filter(s -> {
                        try {
                            if (Strings.isNullOrEmpty(s)) {
                                return false;
                            }
                            final URI uri = new URI(s);
                            if (!uri.isAbsolute()) {
                                return true;
                            }
                            switch (uri.getScheme()) {
                                case "http":
                                case "https":
                                    return true;
                            }
                            return false;
                        } catch (URISyntaxException e) {
                            return false;
                        }
                    })
                    .map(URI::create)
                    .findFirst();
        }

        final URI uri = externalUri.orElse(defaultUri);

        // Make sure we return an URI object with a trailing slash
        if (!uri.toString().endsWith("/")) {
            return URI.create(uri.toString() + "/");
        }
        return uri;
    }

    public static Map<DateTime, Integer> genericHistogramToResponse(List<GenericIntegerHistogramEntry> entries) {
        Map<DateTime, Integer> response = Maps.newHashMap();

        for (GenericIntegerHistogramEntry entry : entries) {
            response.put(entry.bucket(), entry.value());
        }


        return response;
    }

    public static DetectionAlertDetailsResponse buildAlertDetailsResponse(NzymeNode nzyme,
                                                                    DetectionAlertEntry alert,
                                                                    List<DetectionAlertAttributeEntry> attributes) {
        long timelineEntries = nzyme.getDetectionAlertService().countAlertTimelineEntries(alert.id());

        return buildAlertDetailsResponse(nzyme, alert, attributes, Math.max(0, timelineEntries - 1));
    }

    public static DetectionAlertDetailsResponse buildAlertDetailsResponse(NzymeNode nzyme,
                                                                    DetectionAlertEntry alert,
                                                                    List<DetectionAlertAttributeEntry> attributes,
                                                                    long reactivationCount) {
        Map<String, String> responseAttributes = Maps.newTreeMap();
        for (DetectionAlertAttributeEntry attribute : attributes) {
            responseAttributes.put(attribute.key(), attribute.value());
        }

        boolean isActive = !alert.isResolved() &&
                alert.lastSeen().isAfter(DateTime.now().minusMinutes(DetectionAlertService.ACTIVE_THRESHOLD_MINUTES));

        String status;
        if (alert.isResolved()) {
            status = "Resolved";
        } else if (isActive) {
            status = "Active";
        } else {
            status = "Inactive";
        }

        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);
        String affectedMac = DetectionAlertService.affectedMac(attributes);

        return DetectionAlertDetailsResponse.create(
                alert.uuid(),
                alert.dot11MonitoredNetworkId(),
                alert.tapId(),
                alert.detectionType(),
                alert.subsystem(),
                alert.details(),
                responseAttributes,
                alert.createdAt(),
                alert.lastSeen(),
                isActive,
                alert.isResolved(),
                status,
                addresses.addressResponse(DetectionAlertService.affectedAddress(attributes), null, affectedMac,
                        L4Type.TCP, alert.organizationId(), alert.tenantId()),
                addresses.macResponse(affectedMac, alert.organizationId(), alert.tenantId()),
                reactivationCount,
                alert.organizationId(),
                alert.tenantId()
        );
    }

}
