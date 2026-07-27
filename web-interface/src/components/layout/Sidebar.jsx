import React, {useContext} from 'react'
import ApiRoutes from '../../util/ApiRoutes'
import NavigationLink from './NavigationLink'
import SidebarSubmenu from './SidebarSubmenu'
import UserProfileBlock from "./UserProfileBlock";

import {AlertContext, UserContext} from "../../App";
import {userHasPermission, userHasSubsystem} from "../../util/Tools";
import AlertedHealthIndicatorIcon from "../misc/AlertedHealthIndicatorIcon";
import AssetImage from "../misc/AssetImage";

function Sidebar(props) {

  const user = useContext(UserContext);
  const alerts = useContext(AlertContext);
  const branding = props.branding;

  return (
      <div id="nav-side">
        <p className="brand">
          <a href={ApiRoutes.DASHBOARD}>
           {branding.sidebar_title_text === "nzyme" ? <AssetImage filename="navlogo.png" filenameHover="navlogo_hover.png" /> : branding.sidebar_title_text}
          </a></p>
        <p className="brand-subtitle">{branding.sidebar_subtitle_text ? branding.sidebar_subtitle_text : null}</p>

        <div className="mt-4 mb-4">
          <UserProfileBlock user={user}/>
        </div>

        <ul className="nav nav-pills flex-column mb-sm-auto mb-0 align-items-center align-items-sm-start" id="menu">
          <NavigationLink
              href={ApiRoutes.DASHBOARD}
              title="Dashboard"
              icon={<i className="sidebar-icon fa-regular fa-map"/>}/>

          <NavigationLink
            href={ApiRoutes.LOCATIONS.INDEX}
            title="Locations"
            icon={<i className="sidebar-icon fa-solid fa-globe"/>}/>

          <SidebarSubmenu title="Ethernet"
                          subhref="/ethernet"
                          icon={<i className="sidebar-icon fa-solid fa-network-wired"/>}
                          show={userHasSubsystem(user, "ethernet")}>
            <NavigationLink
                href={ApiRoutes.ETHERNET.OVERVIEW}
                title="Overview"
                icon={<i className="sidebar-icon fa-solid fa-ranking-star"/>}/>
            <NavigationLink
                href={ApiRoutes.ETHERNET.ASSETS.INDEX}
                title="Assets"
                icon={<i className="sidebar-icon fa-solid fa-list"/>}/>
            <NavigationLink
                href={ApiRoutes.ETHERNET.L4.OVERVIEW}
                title="TCP/UDP"
                icon={<i className="sidebar-icon fa-solid fa-road"/>}/>
            <NavigationLink
                href={ApiRoutes.ETHERNET.DNS.INDEX}
                title="DNS"
                icon={<i className="sidebar-icon fa-solid fa-signs-post"/>}/>
            <NavigationLink
                href={ApiRoutes.ETHERNET.REMOTE.SSH.INDEX}
                title="Remote Access"
                icon={<i className="sidebar-icon fa-solid fa-plug"/>}/>
            <NavigationLink
                href={ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX}
                title="Tunnels"
                icon={<i className="sidebar-icon fa-solid fa-car-tunnel"/>}/>
            <NavigationLink
              href={ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX}
              title="Streams"
              icon={<i className="sidebar-icon fa-solid fa-car-tunnel"/>}/>
            <NavigationLink
              href={ApiRoutes.ETHERNET.TIME.NTP.INDEX}
              title="Time"
              icon={<i className="sidebar-icon fa-solid fa-clock"/>}/>
            {/*
            <NavigationLink
              href={ApiRoutes.ETHERNET.RECON.INDEX}
              title="Recon"
              icon={<i className="sidebar-icon fa-solid fa-binoculars"/>}/>
            <NavigationLink
                href={ApiRoutes.ETHERNET.BEACONS.INDEX}
                title="Beacons"
                icon={<i className="sidebar-icon fa-solid fa-location-arrow"/>}/> */}
          </SidebarSubmenu>

          <SidebarSubmenu title="WiFi"
                          subhref="/dot11"
                          icon={<i className="sidebar-icon fa-solid fa-wifi"/>}
                          show={userHasSubsystem(user, "dot11")}>
            <NavigationLink
                href={ApiRoutes.DOT11.OVERVIEW}
                title="Overview"
                icon={<i className="sidebar-icon fa-solid fa-ranking-star"/>}/>

            <NavigationLink
                href={ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX}
                title="Access Points"
                icon={<i className="sidebar-icon fa-solid fa-tower-cell"/>}/>

            <NavigationLink
                href={ApiRoutes.DOT11.CLIENTS.CONNECTED}
                title="Clients"
                icon={<i className="sidebar-icon fa-solid fa-timeline"/>}/>

            <NavigationLink
                href={ApiRoutes.DOT11.DISCO.INDEX}
                title="Disconnections"
                icon={<i className="sidebar-icon fa-solid fa-link-slash"/>}/>

            <NavigationLink
                href={ApiRoutes.DOT11.MONITORING.INDEX}
                title="Monitoring"
                icon={<i className="sidebar-icon fa-solid fa-shield-halved"/>}
                show={userHasPermission(user, "dot11_monitoring_manage")}/>
          </SidebarSubmenu>

          <SidebarSubmenu title="Bluetooth"
                          subhref="/bluetooth"
                          icon={<i className="sidebar-icon fa-brands fa-bluetooth"/>}
                          show={userHasSubsystem(user, "bluetooth")}>
            <NavigationLink
                href={ApiRoutes.BLUETOOTH.DEVICES.INDEX}
                title="Devices"
                icon={<i className="sidebar-icon fa-solid fa-mobile-screen-button"/>}/>
          </SidebarSubmenu>

          <SidebarSubmenu title="UAV"
                          subhref="/uavs"
                          icon={<i className="sidebar-icon fa-solid fa-paper-plane"/>}
                          show={userHasSubsystem(user, "uav")}>
            <NavigationLink
              href={ApiRoutes.UAV.INDEX}
              title="UAVs"
              icon={<i className="sidebar-icon fa-solid fa-paper-plane"/>}/>

            <NavigationLink
              href={ApiRoutes.UAV.TYPES.INDEX}
              title="Types"
              icon={<i className="sidebar-icon fa-solid fa-fingerprint"/>}
              show={true} />

            <NavigationLink
              href={ApiRoutes.UAV.MONITORING.INDEX}
              title="Monitoring"
              icon={<i className="sidebar-icon fa-solid fa-shield-halved"/>}
              show={userHasPermission(user, "uav_monitoring_manage")}/>
          </SidebarSubmenu>

          <SidebarSubmenu title="Context"
                          subhref="/context"
                          icon={<i className="sidebar-icon fa-solid fa-circle-info"></i>}
                          show={true}>
            <NavigationLink
                href={ApiRoutes.CONTEXT.MAC_ADDRESSES.INDEX}
                title="MAC Addresses"
                icon={<i className="sidebar-icon fa-regular fa-address-card"/>}/>

          </SidebarSubmenu>

          <SidebarSubmenu title="Alerts"
                          subhref="/alerts"
                          icon={<i className={"sidebar-icon fa-solid fa-bell " + (alerts.has_active_alerts ? " text-danger blink" : null )}></i>}
                          show={userHasPermission(user, "alerts_view")
                              || userHasPermission(user, "alerts_manage")}>
            <NavigationLink
                href={ApiRoutes.ALERTS.INDEX}
                title={"Overview"}
                icon={<i className={"sidebar-icon fa-solid fa-ranking-star"}/>}
                show={userHasPermission(user, "alerts_view")
                    || userHasPermission(user, "alerts_manage")}/>

            <NavigationLink
                href={ApiRoutes.ALERTS.SUBSCRIPTIONS.INDEX}
                title="Subscriptions"
                icon={<i className="sidebar-icon fa-solid fa-bolt"/>}
                show={user.is_orgadmin || user.is_superadmin}/>
          </SidebarSubmenu>

          <SidebarSubmenu title="System"
                          subhref="/system"
                          icon={<AlertedHealthIndicatorIcon icon="fa-solid fa-screwdriver-wrench" healthIndicatorLevel={alerts.health_indicator_level} />}
                          show={user.is_orgadmin || user.is_superadmin}>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.CLUSTER.INDEX}
                title="Cluster &amp; Nodes"
                icon={<i className="sidebar-icon fa-solid fa-circle-nodes"/>}/>
            <NavigationLink
                href={ApiRoutes.SYSTEM.TAPS.INDEX}
                title="Taps"
                icon={<i className="sidebar-icon fa-solid fa-server"/>}/>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.CONNECT}
                title="Nzyme Connect"
                icon={<i className="fa-solid fa-plug-circle-bolt"></i>}/>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.EVENTS.INDEX}
                title="Events &amp; Actions"
                icon={<i className="sidebar-icon fa-solid fa-bolt"/>}/>
            <NavigationLink
                show={user.is_orgadmin}
                href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS_PAGE(user.organization_id)}
                title="Events &amp; Actions"
                icon={<i className="sidebar-icon fa-solid fa-bolt"/>}/>
            <NavigationLink
                href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}
                title={user.is_superadmin ? "Authentication" : "Organization"}
                icon={<i className="sidebar-icon fa-solid fa-users"/>}/>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.DATABASE.INDEX}
                title="Database"
                icon={<i className="sidebar-icon fa-solid fa-database"/>}/>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.INTEGRATIONS.INDEX}
                title="Integrations"
                icon={<i className="sidebar-icon fa-solid fa-puzzle-piece"/>}/>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.HEALTH.INDEX}
                title="Health Console"
                icon={<AlertedHealthIndicatorIcon icon="fa-solid fa-laptop-medical" healthIndicatorLevel={alerts.health_indicator_level} />}/>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.MONITORING.INDEX}
                title="Monitoring &amp; Metrics"
                icon={<i className="sidebar-icon fa-solid fa-heart-pulse"/>}/>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.CRYPTO.INDEX}
                title="Keys &amp; Certificates"
                icon={<i className="sidebar-icon fa-solid fa-key"/>}/>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.LOOKANDFEEL}
                title="Look &amp; Feel"
                icon={<i className="fa-solid fa-paint-roller"></i>}/>
            <NavigationLink
              show={user.is_superadmin}
              href={ApiRoutes.SYSTEM.SUBSYSTEMS}
              title="Subsystems"
              icon={<i className="fa-solid fa-cubes"></i>}/>
            <NavigationLink
                show={user.is_superadmin}
                href={ApiRoutes.SYSTEM.VERSION}
                title="Version"
                icon={<i className="sidebar-icon fa-solid fa-tag "/>}/>
          </SidebarSubmenu>
        </ul>
      </div>
  )
}

export default Sidebar
