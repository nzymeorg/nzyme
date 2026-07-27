package app.nzyme.core.rest.resources.taps;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.authentication.AuthenticatedTap;
import app.nzyme.core.rest.authentication.TapSecured;
import app.nzyme.core.rest.resources.taps.reports.tables.arp.ArpPacketsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.bluetooth.BluetoothDevicesReport;
import app.nzyme.core.rest.resources.taps.reports.tables.dhcp.DhcpTransactionsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.dns.DnsTablesReport;
import app.nzyme.core.rest.resources.taps.reports.tables.dot11.Dot11TablesReport;
import app.nzyme.core.rest.resources.taps.reports.tables.ntp.NTPTransactionsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.rtsp.RtspSessionsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.socks.SocksTunnelsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.ssh.SshSessionsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.tcp.TcpSessionsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.uav.UavsReport;
import app.nzyme.core.rest.resources.taps.reports.tables.udp.UdpConversationsReport;
import app.nzyme.plugin.Subsystem;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

@Path("/api/taps/tables")
@TapSecured
@Produces(MediaType.APPLICATION_JSON)
public class TablesResource {

    private static final Logger LOG = LogManager.getLogger(StatusResource.class);

    @Inject
    private NzymeNode nzyme;

    /*
     * REMEMBER: The `TapTableSizeInterceptor` will consider all requests that include `api/taps/tables` in
     * their request URI.
     */

    @POST
    @Path("/dot11/summary")
    public Response dot11Summary(@Context SecurityContext sc, Dot11TablesReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.DOT11, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting 802.11 summary report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received 802.11 summary report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().dot11().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/bluetooth/devices")
    public Response bluetoothDevices(@Context SecurityContext sc, BluetoothDevicesReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.BLUETOOTH, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting Bluetooth devices report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received Bluetooth devices report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().bluetooth().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/tcp/sessions")
    public Response tcpSessions(@Context SecurityContext sc, TcpSessionsReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting TCP sessions report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received TCP session table report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().tcp().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/udp/conversations")
    public Response udpConversations(@Context SecurityContext sc, UdpConversationsReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting UDP conversations report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received UDP conversations table report from tap [{}]: {}", tap.getUuid(), report);

        // Store in combined TCP/UDP table.
        nzyme.getTablesService().udp().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/dns/summary")
    public Response dnsSummary(@Context SecurityContext sc, DnsTablesReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting DNS summary report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received DNS summary report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().dns().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/ssh/sessions")
    public Response sshSessions(@Context SecurityContext sc, SshSessionsReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting SSH sessions report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received SSH sessions report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().ssh().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/socks/tunnels")
    public Response socksTunnels(@Context SecurityContext sc, SocksTunnelsReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting SOCKS tunnels report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received SOCKS tunnels report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().socks().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/uav/uavs")
    public Response uavUavs(@Context SecurityContext sc, UavsReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.UAV, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting UAVs report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received UAVs report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().uav().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/dhcp/transactions")
    public Response dhcpTransactions(@Context SecurityContext sc, DhcpTransactionsReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting DHCP transactions report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received DHCP transactions report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().dhcp().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/arp/packets")
    public Response arpPackets(@Context SecurityContext sc, ArpPacketsReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting ARP packets report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received ARP packets report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().arp().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/ntp/transactions")
    public Response ntpTransactions(@Context SecurityContext sc, NTPTransactionsReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting NTP transactions report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received NTP transactions report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().ntp().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/rtsp/sessions")
    public Response rtspSessions(@Context SecurityContext sc, RtspSessionsReport report) {
        AuthenticatedTap tap = ((AuthenticatedTap) sc.getUserPrincipal());

        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tap.getOrganizationId(), tap.getTenantId())) {
            LOG.debug("Rejecting RTSP transactions report from tap [{}]: Subsystem is disabled.", tap.getUuid());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        LOG.debug("Received RTSP transactions report from tap [{}]: {}", tap.getUuid(), report);
        nzyme.getTablesService().rtsp().handleReport(tap.getUuid(), DateTime.now(), report);

        return Response.status(Response.Status.CREATED).build();
    }

}
