package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressContextResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import com.google.common.base.Splitter;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class Dot11BssidScopedResource extends TapDataHandlingResource {

    @Inject
    protected NzymeNode nzyme;

    protected record BssidSelection(@Nullable List<String> bssids, @Nullable Response error) { }

    protected BssidSelection resolveBssids(AuthenticatedUser authenticatedUser,
                                           @Nullable String bssidsParam,
                                           @Nullable UUID monitoredNetworkId) {
        List<String> bssids = null;

        if (bssidsParam != null && !bssidsParam.isEmpty()) {
            bssids = Splitter.on(",").omitEmptyStrings().trimResults().splitToList(bssidsParam)
                    .stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());
        }

        if (monitoredNetworkId != null) {
            Optional<MonitoredSSID> monitoredNetwork = nzyme.getDot11().findMonitoredSSID(monitoredNetworkId);
            if (monitoredNetwork.isEmpty()) {
                return new BssidSelection(null, Response.status(Response.Status.NOT_FOUND).build());
            }

            if (!entityAccessible(authenticatedUser, monitoredNetwork.get())) {
                return new BssidSelection(null, Response.status(Response.Status.UNAUTHORIZED).build());
            }

            bssids = nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(monitoredNetwork.get().id())
                    .stream()
                    .map(MonitoredBSSID::bssid)
                    .collect(Collectors.toList());
        }

        return new BssidSelection(bssids, null);
    }

    protected Dot11MacLinkMetadataResponse buildMacResponse(String mac,
                                                            List<UUID> tapUuids,
                                                            UUID organizationId,
                                                            UUID tenantId) {
        Optional<MacAddressContextEntry> macContext = nzyme.getContextService()
                .findMacAddressContext(mac, organizationId, tenantId);

        return Dot11MacLinkMetadataResponse.create(
                nzyme.getDot11().getMacAddressMetadata(mac, tapUuids).type(),
                Dot11MacAddressResponse.create(
                        mac,
                        nzyme.getOuiService().lookup(mac).orElse(null),
                        null,
                        macContext.map(c -> Dot11MacAddressContextResponse.create(c.name(), c.description(), c.notes()))
                                .orElse(null)
                )
        );
    }

}
