package app.nzyme.core.rest;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.ContextService;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.context.db.MacAddressTransparentContextEntry;
import app.nzyme.core.context.db.NetworkContextEntry;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.tcp.TcpSessionState;
import app.nzyme.core.rest.misc.CategorizedTransparentContextData;
import app.nzyme.core.rest.responses.context.MacAddressTransparentHostnameResponse;
import app.nzyme.core.rest.responses.context.MacAddressTransparentIpAddressResponse;
import app.nzyme.core.rest.responses.context.NetworkContextDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.*;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RestHelpers {

    private static final Logger LOG = LogManager.getLogger(RestHelpers.class);

    public static L4AddressResponse L4AddressDataToResponse(NzymeNode nzyme,
                                                            UUID organizationId,
                                                            UUID tenantId,
                                                            L4Type type,
                                                            L4AddressData data) {
        if (organizationId == null || tenantId == null) {
            throw new IllegalArgumentException("Organization and Tenant ID must be set.");
        }


        L4AddressTypeResponse typeResponse;
        switch(type) {
            case TCP:
                typeResponse = L4AddressTypeResponse.TCP;
                break;
            case UDP:
                typeResponse = L4AddressTypeResponse.UDP;
                break;
            case NONE:
                typeResponse = L4AddressTypeResponse.NONE;
                break;
            default:
                throw new RuntimeException("Unknown L4 type [" + type + "].");
        }

        L4AddressGeoResponse geo;
        if (data.geo() != null) {
            geo = L4AddressGeoResponse.create(
                    data.geo().asnNumber(),
                    data.geo().asnName(),
                    data.geo().asnDomain(),
                    data.geo().city(),
                    data.geo().countryCode(),
                    data.geo().latitude(),
                    data.geo().longitude()
            );
        } else {
            geo = null;
        }

        L4AddressAttributesResponse attributes;
        if (data.attributes() != null) {
            attributes = L4AddressAttributesResponse.create(
                    data.attributes().isSiteLocal(),
                    data.attributes().isLoopback(),
                    data.attributes().isMulticast()
            );
        } else {
            attributes = null;
        }

        // IP address context.
        List<L4AddressNetworkContextResponse> networkContext = Lists.newArrayList();
        for (NetworkContextEntry ctx : nzyme.getContextService()
                .findNetworkContext(InetAddress.ofLiteral(data.address()), organizationId, tenantId)) {
            networkContext.add(L4AddressNetworkContextResponse.create(
                    ctx.name(),
                    ctx.description()
            ));
        }

        Optional<MacAddressContextEntry> context = nzyme.getContextService()
                .findMacAddressContext(data.mac(), organizationId, tenantId);

        // Get asset info if we have it.
        UUID assetId;
        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAssetByMac(data.mac(), organizationId, tenantId);
        if (asset.isPresent() && data.attributes() != null && data.attributes().isSiteLocal()) {
            assetId = asset.get().uuid();
        } else {
            assetId = null;
        }

        EthernetMacAddressResponse macResponse;
        if (data.mac() != null) {
            macResponse = EthernetMacAddressResponse.create(
                    data.mac(),
                    nzyme.getOuiService().lookup(data.mac()).orElse(null),
                    assetId,
                    asset.map(AssetEntry::isActive).orElse(null),
                    macContextEntryToResponse(context)
            );
        } else {
            // Try to pull asset from IP address when we don't have a MAC.

            macResponse = null;
        }

        return L4AddressResponse.create(
                typeResponse,
                macResponse,
                data.address(),
                data.port(),
                geo,
                attributes,
                L4AddressContextResponse.create(networkContext)
        );
    }

    public static String tcpSessionStateToGeneric(TcpSessionState state) {
        switch (state) {
            case SYNSENT:
            case SYNRECEIVED:
            case ESTABLISHED:
            case FINWAIT1:
            case FINWAIT2:
                return "Active";
            case CLOSEDFIN:
            case CLOSEDRST:
            case REFUSED:
            case CLOSEDTIMEOUT:
            case CLOSEDTIMEOUTNODE:
                return "Inactive";
        }

        return "Invalid";
    }

    public static CategorizedTransparentContextData transparentContextDataToResponses(List<MacAddressTransparentContextEntry> data) {
        List<MacAddressTransparentIpAddressResponse> transparentIps = Lists.newArrayList();
        List<MacAddressTransparentHostnameResponse> transparentHostnames = Lists.newArrayList();

        for (MacAddressTransparentContextEntry t : data) {
            ContextService.TransparentDataType dataType;

            try {
                dataType = ContextService.TransparentDataType.valueOf(t.type());
            } catch (IllegalArgumentException e) {
                LOG.error("Invalid transparent context data type [{}].", t.type());
                continue;
            }

            switch (dataType) {
                case IP_ADDRESS:
                    transparentIps.add(MacAddressTransparentIpAddressResponse.create(
                            t.ipAddress().toString().substring(1),
                            t.source(),
                            t.lastSeen(),
                            t.createdAt()
                    ));
                    break;
                case HOSTNAME:
                    transparentHostnames.add(MacAddressTransparentHostnameResponse.create(
                            t.hostname(),
                            t.source(),
                            t.lastSeen(),
                            t.createdAt()
                    ));
                    break;
            }
        }

        return CategorizedTransparentContextData.create(transparentIps, transparentHostnames);
    }

    public static InternalAddressResponse internalAddressDataToResponse(NzymeNode nzyme,
                                                                        @Nullable String mac,
                                                                        String address,
                                                                        UUID organizationId,
                                                                        UUID tenantId) {
        if (address == null) {
            return null;
        }

        List<L4AddressNetworkContextResponse> networkContext = Lists.newArrayList();
        for (NetworkContextEntry ctx : nzyme.getContextService()
                .findNetworkContext(InetAddress.ofLiteral(address), organizationId, tenantId)) {
            networkContext.add(L4AddressNetworkContextResponse.create(
                    ctx.name(),
                    ctx.description()
            ));
        }

        Optional<AssetEntry> asset;
        EthernetMacAddressResponse macResponse;
        if (mac != null) {
            asset = nzyme.getAssetsManager().findAssetByMac(mac, organizationId, tenantId);

            Optional<MacAddressContextEntry> context = nzyme.getContextService().findMacAddressContext(mac, organizationId, tenantId);
            macResponse = EthernetMacAddressResponse.create(
                    mac,
                    nzyme.getOuiService().lookup(mac).orElse(null),
                    asset.map(AssetEntry::uuid).orElse(null),
                    asset.map(AssetEntry::isActive).orElse(null),
                    macContextEntryToResponse(context)
            );
        } else {
            asset = null;
            macResponse = null;
        }

        return InternalAddressResponse.create(
                L4AddressTypeResponse.NONE,
                macResponse,
                address,
                null,
                L4AddressContextResponse.create(networkContext)
        );
    }

    public static EthernetMacAddressContextResponse macContextEntryToResponse(Optional<MacAddressContextEntry> context) {
        return context.map(ctx ->
                EthernetMacAddressContextResponse.create(
                        ctx.uuid(),
                        ctx.name(),
                        ctx.description(),
                        ctx.notes()
                )
        ).orElse(null);
    }

}
