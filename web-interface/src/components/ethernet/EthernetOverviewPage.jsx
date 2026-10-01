import React, {useContext, useEffect, useState} from "react";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../misc/TapSelector";
import {TapContext, UserContext} from "../../App";
import {userHasPermission} from "../../util/Tools";
import DetectionAlertsService from "../../services/DetectionAlertsService";
import WithPermission from "../misc/WithPermission";
import NumberCard from "../widgets/presentation/NumberCard";
import ApiRoutes from "../../util/ApiRoutes";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import ActiveAssetsHistogram from "./assets/ActiveAssetsHistogram";
import {Presets as TimeRange} from "../shared/timerange/TimeRange";
import DNSService from "../../services/ethernet/DNSService";
import DNSStatisticsChart from "./dns/DNSStatisticsChart";
import L4Service from "../../services/ethernet/L4Service";
import L4SessionsTotalBytesChart from "./l4/L4SessionsTotalBytesChart";
import L4SessionsInternalBytesChart from "./l4/L4SessionsInternalBytesChart";
import AssetsService from "../../services/ethernet/AssetsService";
import ARPPacketsChart from "./assets/arp/ARPPacketsChart";
import DHCPTransactionsChart from "./assets/dhcp/DHCPTransactionsChart";
import usePageTitle from "../../util/UsePageTitle";

const alertsService = new DetectionAlertsService();
const dnsService = new DNSService()
const l4Service = new L4Service();
const assetsService = new AssetsService();

function byteConversion (x) {
  return x / 1024
}

export default function EthernetOverviewPage() {

  usePageTitle("Ethernet Overview");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const user = useContext(UserContext);

  const [alerts, setAlerts] = useState(null);
  const [l4Stats, setL4Stats] = useState(null);
  const [arpStats, setArpStats] = useState(null);
  const [dhcpStats, setDhcpStats] = useState(null);
  const [dnsQueryStats, setDnsQueryStats] = useState(null);
  const [dnsResponseStats, setDnsResponseStats] = useState(null);
  const [dnsNxdomainStats, setDnsNxdomainStats] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    if (userHasPermission(user, "alerts_view")) {
      alertsService.findAllAlerts(setAlerts, organizationId, tenantId, 10, 0, "ETHERNET");
    }

    assetsService.getArpStatistics(TimeRange.RELATIVE_HOURS_24, {}, selectedTaps, setArpStats);
    assetsService.getDHCPStatistics(TimeRange.RELATIVE_HOURS_24, {}, selectedTaps, setDhcpStats);

    dnsService.getGlobalChart(TimeRange.RELATIVE_HOURS_24, selectedTaps, "request_bytes", setDnsQueryStats);
    dnsService.getGlobalChart(TimeRange.RELATIVE_HOURS_24, selectedTaps, "response_bytes", setDnsResponseStats);
    dnsService.getGlobalChart(TimeRange.RELATIVE_HOURS_24, selectedTaps, "nxdomain_count", setDnsNxdomainStats);

    l4Service.getSessionsStatistics(TimeRange.RELATIVE_HOURS_24, selectedTaps, setL4Stats);

  }, [organizationId, tenantId, user, selectedTaps]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <h1>Ethernet</h1>
        </div>
      </div>

      <WithPermission permission="alerts_view">
        <div className="row mt-3">
            <div className="col-4">
              <NumberCard title="Active Alerts"
                          internalLink={ApiRoutes.ALERTS.INDEX}
                          value={alerts ? alerts.total_active : 0}
                          numberFormat="0,0"
                          fullHeight={true}
                          className={(alerts ? (alerts.total_active > 0 ? "bg-danger" : null) : null)}/>
            </div>
        </div>
      </WithPermission>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Active Assets"
                                     internalLink={ApiRoutes.ETHERNET.ASSETS.INDEX}
                                     timeRange={TimeRange.RELATIVE_HOURS_24}/>

              <ActiveAssetsHistogram organizationId={organizationId}
                                     tenantId={tenantId}
                                     timeRange={TimeRange.RELATIVE_HOURS_24} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="ARP Packets"
                                     internalLink={ApiRoutes.ETHERNET.ASSETS.ARP.INDEX}
                                     timeRange={TimeRange.RELATIVE_HOURS_24}/>

              <ARPPacketsChart statistics={arpStats} />
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="DHCP Transactions"
                                     internalLink={ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX}
                                     timeRange={TimeRange.RELATIVE_HOURS_24}/>

              <DHCPTransactionsChart statistics={dhcpStats} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="TCP/UDP: All Bytes Transferred"
                                     internalLink={ApiRoutes.ETHERNET.L4.SESSIONS}
                                     timeRange={TimeRange.RELATIVE_HOURS_24} />

              <L4SessionsTotalBytesChart statistics={l4Stats} />
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="TCP/UDP: Internal Bytes Transferred"
                                     internalLink={ApiRoutes.ETHERNET.L4.SESSIONS}
                                     timeRange={TimeRange.RELATIVE_HOURS_24} />

              <L4SessionsInternalBytesChart statistics={l4Stats} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="DNS Query Volume"
                                     internalLink={ApiRoutes.ETHERNET.DNS.INDEX}
                                     timeRange={TimeRange.RELATIVE_HOURS_24} />

              <DNSStatisticsChart data={dnsQueryStats}
                                  conversion={byteConversion}
                                  valueType="KB"/>
            </div>
          </div>
        </div>

        <div className="col-md-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="DNS Response Volume"
                                     internalLink={ApiRoutes.ETHERNET.DNS.INDEX}
                                     timeRange={TimeRange.RELATIVE_HOURS_24} />

              <DNSStatisticsChart data={dnsResponseStats}
                                  conversion={byteConversion}
                                  valueType="KB"/>
            </div>
          </div>
        </div>

        <div className="col-md-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="DNS NXDomain Responses"
                                     internalLink={ApiRoutes.ETHERNET.DNS.INDEX}
                                     timeRange={TimeRange.RELATIVE_HOURS_24} />

              <DNSStatisticsChart data={dnsNxdomainStats}
                                  attribute="nxdomain_count"/>
            </div>
          </div>
        </div>
      </div>

    </React.Fragment>
  )

}