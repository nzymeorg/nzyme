package app.nzyme.core.rest;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.assets.profile.AssetProfileBatch;
import app.nzyme.core.context.ContextService;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.context.db.MacAddressTransparentContextEntry;
import app.nzyme.core.context.db.NetworkContextEntry;
import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.l4.db.L4AddressAttributes;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.tcp.TcpSessionState;
import app.nzyme.core.ethernet.segmentation.MembershipResolver;
import app.nzyme.core.ethernet.segmentation.Segment;
import app.nzyme.core.rest.misc.CategorizedTransparentContextData;
import app.nzyme.core.rest.responses.context.MacAddressTransparentHostnameResponse;
import app.nzyme.core.rest.responses.context.MacAddressTransparentIpAddressResponse;
import app.nzyme.core.rest.responses.context.NetworkContextDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.*;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Inet4Address;
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

        L4AddressMacRoleResponse macRole = macRole(nzyme, data.mac(), InetAddress.ofLiteral(data.address()),
                organizationId, tenantId);

        EthernetMacAddressResponse macResponse;
        if (data.mac() == null) {
            macResponse = null;
        } else if (macRole.withoutAsset()) {
            macResponse = recordedMacResponse(nzyme, data.mac(), macRole);
        } else {
            Optional<MacAddressContextEntry> context = nzyme.getContextService()
                    .findMacAddressContext(data.mac(), organizationId, tenantId);
            Optional<AssetEntry> asset = nzyme.getAssetsManager().findAssetByMac(data.mac(), organizationId, tenantId);

            macResponse = EthernetMacAddressResponse.builder()
                    .address(data.mac())
                    .oui(nzyme.getOuiService().lookup(data.mac()).orElse(null))
                    .assetId(macIsDevice(macRole, data.attributes()) ? asset.map(AssetEntry::uuid).orElse(null) : null)
                    .assetIsActive(asset.map(AssetEntry::isActive).orElse(null))
                    .context(macContextEntryToResponse(context))
                    .role(macRole)
                    .build();
        }

        return L4AddressResponse.create(
                typeResponse,
                macResponse,
                data.address(),
                data.port(),
                geo,
                attributes,
                addressContext(nzyme, networkContext, data.address(),
                        data.geo() == null || data.geo().asnNumber() == null ? null : data.geo().asnNumber().longValue(),
                        organizationId, tenantId)
        );
    }

    // What the MAC address that traffic of an address was recorded with is to the address. Only private IPv4
    // addresses are claimed by ARP and recorded for assets, so only there it is known if it is the device that has
    // the address. If it did not claim the address, it usually is a router that forwarded the traffic. The MAC
    // address of a public address usually is the gateway.
    public static L4AddressMacRoleResponse macRole(NzymeNode nzyme,
                                                   @Nullable String mac,
                                                   InetAddress address,
                                                   UUID organizationId,
                                                   UUID tenantId) {
        if (!AssetProfileBatch.isUsableMac(mac)) {
            return L4AddressMacRoleResponse.UNKNOWN;
        }

        if (address instanceof Inet4Address
                && nzyme.getEthernet().internalAddresses().of(organizationId, tenantId).isSiteLocal(address)) {
            return nzyme.getAssetsManager().claimedIpAddress(mac, address, organizationId, tenantId)
                    ? L4AddressMacRoleResponse.DEVICE : L4AddressMacRoleResponse.UNCLAIMED;
        }

        return isPublic(address) ? L4AddressMacRoleResponse.PUBLIC : L4AddressMacRoleResponse.UNKNOWN;
    }

    private static final List<CIDR> SPECIAL_IPV4 = List.of(
            CIDR.parse("0.0.0.0/8"), CIDR.parse("100.64.0.0/10"), CIDR.parse("192.0.0.0/24"),
            CIDR.parse("192.0.2.0/24"), CIDR.parse("198.18.0.0/15"), CIDR.parse("198.51.100.0/24"),
            CIDR.parse("203.0.113.0/24"), CIDR.parse("240.0.0.0/4")
    );

    private static final CIDR GLOBAL_IPV6 = CIDR.parse("2000::/3");
    private static final CIDR DOCUMENTATION_IPV6 = CIDR.parse("2001:db8::/32");

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }

        if (address instanceof Inet4Address) {
            return SPECIAL_IPV4.stream().noneMatch(range -> range.contains(address));
        }

        return GLOBAL_IPV6.contains(address) && !DOCUMENTATION_IPV6.contains(address);
    }

    public static boolean macWithoutAsset(@Nullable L4AddressResponse address) {
        return address != null && address.mac() != null && address.mac().role() != null
                && address.mac().role().withoutAsset();
    }

    // Where the role is not known, the site-local attribute decides.
    private static boolean macIsDevice(L4AddressMacRoleResponse macRole, @Nullable L4AddressAttributes attributes) {
        return macRole == L4AddressMacRoleResponse.DEVICE
                || (macRole == L4AddressMacRoleResponse.UNKNOWN && attributes != null && attributes.isSiteLocal());
    }

    public static EthernetMacAddressResponse recordedMacResponse(NzymeNode nzyme,
                                                                String mac,
                                                                L4AddressMacRoleResponse role) {
        return EthernetMacAddressResponse.builder()
                .address(mac)
                .oui(nzyme.getOuiService().lookup(mac).orElse(null))
                .role(role)
                .build();
    }

    private static L4AddressContextResponse addressContext(NzymeNode nzyme,
                                                           List<L4AddressNetworkContextResponse> networkContext,
                                                           String address,
                                                           @Nullable Long asn,
                                                           UUID organizationId,
                                                           UUID tenantId) {
        MembershipResolver.AnyTap resolved;
        try {
            resolved = nzyme.getEthernet().segmentation().segmentOfAddress(organizationId, tenantId,
                    InetAddress.ofLiteral(address), asn);
        } catch (Exception e) {
            LOG.debug("Could not look up segment of address [{}].", address, e);
            return L4AddressContextResponse.create(networkContext);
        }

        if (resolved.dependsOnTap()) {
            List<L4AddressSegmentResponse> candidates = Lists.newArrayList();
            for (Segment candidate : resolved.candidates()) {
                candidates.add(segmentResponse(candidate));
            }

            return L4AddressContextResponse.create(networkContext, null,
                    L4AddressSegmentsByTapResponse.create(candidates, resolved.unassignedPossible()));
        }

        return L4AddressContextResponse.create(networkContext,
                resolved.segment() == null ? null : segmentResponse(resolved.segment()), null);
    }

    private static L4AddressSegmentResponse segmentResponse(Segment segment) {
        return L4AddressSegmentResponse.create(segment.uuid(), segment.name(), segment.kind().name(), segment.description(),
                segment.notes() != null && !segment.notes().isBlank());
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
                addressContext(nzyme, networkContext, address, null, organizationId, tenantId)
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
