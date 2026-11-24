package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.ethernet.GeoData;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.beacons.BeaconDetector;
import app.nzyme.core.ethernet.l4.db.L4AddressAttributes;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressMacRoleResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;

public class ReconAddressResponses {

    private static final Logger LOG = LogManager.getLogger(ReconAddressResponses.class);

    private final NzymeNode nzyme;

    public ReconAddressResponses(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Nullable
    public EthernetMacAddressResponse macResponse(@Nullable String mac, UUID organizationId, UUID tenantId) {
        return macWithRole(mac, null, organizationId, tenantId);
    }

    // Only for the MAC address that traffic of the address was recorded with.
    @Nullable
    public EthernetMacAddressResponse macResponse(@Nullable String mac,
                                                  @Nullable String address,
                                                  UUID organizationId,
                                                  UUID tenantId) {
        if (mac == null || address == null || !InetAddresses.isInetAddress(address)) {
            return macResponse(mac, organizationId, tenantId);
        }

        return macWithRole(mac, RestHelpers.macRole(nzyme, mac, InetAddresses.forString(address), organizationId,
                tenantId), organizationId, tenantId);
    }

    @Nullable
    private EthernetMacAddressResponse macWithRole(@Nullable String mac,
                                                   @Nullable L4AddressMacRoleResponse role,
                                                   UUID organizationId,
                                                   UUID tenantId) {
        if (mac == null) {
            return null;
        }

        if (role != null && role.withoutAsset()) {
            return RestHelpers.recordedMacResponse(nzyme, mac, role);
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAssetByMac(mac, organizationId, tenantId);
        Optional<MacAddressContextEntry> context = nzyme.getContextService()
                .findMacAddressContext(mac, organizationId, tenantId);

        return EthernetMacAddressResponse.builder()
                .address(mac)
                .oui(nzyme.getOuiService().lookup(mac).orElse(null))
                .assetId(asset.map(AssetEntry::uuid).orElse(null))
                .assetIsActive(asset.map(AssetEntry::isActive).orElse(null))
                .context(RestHelpers.macContextEntryToResponse(context))
                .role(role)
                .build();
    }

    @Nullable
    public L4AddressResponse addressResponse(@Nullable String address,
                                             @Nullable Integer port,
                                             @Nullable String mac,
                                             L4Type l4Type,
                                             UUID organizationId,
                                             UUID tenantId) {
        if (address == null || !BeaconDetector.isValidAddress(address)) {
            return null;
        }

        InetAddress inet;
        try {
            inet = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            return null;
        }

        GeoData geo = null;
        Optional<GeoIpLookupResult> lookup = nzyme.getGeoIpService().lookup(inet);
        if (lookup.isPresent() && (lookup.get().asn() != null || lookup.get().geo() != null)) {
            GeoIpLookupResult r = lookup.get();
            geo = GeoData.create(
                    r.asn() == null ? null : r.asn().number(),
                    r.asn() == null ? null : r.asn().name(),
                    r.asn() == null ? null : r.asn().domain(),
                    r.geo() == null ? null : r.geo().city(),
                    r.geo() == null ? null : r.geo().countryCode(),
                    r.geo() == null ? null : r.geo().latitude(),
                    r.geo() == null ? null : r.geo().longitude()
            );
        }

        L4AddressAttributes attributes = L4AddressAttributes.create(
                nzyme.getEthernet().internalAddresses().of(organizationId, tenantId).isSiteLocal(inet),
                inet.isLoopbackAddress(),
                inet.isMulticastAddress()
        );

        try {
            return RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, l4Type,
                    L4AddressData.create(mac, address, port, geo, attributes));
        } catch (Exception e) {
            LOG.warn("Could not build address data for recon address [{}].", address, e);
            return null;
        }
    }

}
