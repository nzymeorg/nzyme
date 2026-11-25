import React, {useContext, useEffect, useState} from "react";
import numeral from "numeral";
import moment from "moment";
import {toast} from "react-toastify";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import Filters from "../../shared/filtering/Filters";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import SpoofingService from "../../../services/ethernet/SpoofingService";
import {SPOOFING_MENU_ITEMS} from "./SpoofingMenuItems";
import {DHCP_SPOOFING_FILTER_FIELDS} from "./DhcpSpoofingFilterFields";
import DhcpSpoofingTable from "./DhcpSpoofingTable";
import DhcpSpoofingServersTable from "./DhcpSpoofingServersTable";
import ActiveSpoofingFindingsHistogram from "./ActiveSpoofingFindingsHistogram";
import {Presets} from "../../shared/timerange/TimeRange";
import {UserContext} from "../../../App";
import {userHasPermission} from "../../../util/Tools";
import {dhcpSpoofingProtocolToName, dhcpSpoofingServerNoun} from "./DhcpSpoofingProtocol";

const spoofingService = new SpoofingService();


export default function DhcpSpoofingPage() {

  usePageTitle("Ethernet DHCP and Router Spoofing");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_monitoring_manage");

  const [filters, setFilters] = useUrlFilters(DHCP_SPOOFING_FILTER_FIELDS);

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [findings, setFindings] = useState(null);
  const [histogram, setHistogram] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const [servers, setServers] = useState(null);
  const [serversPage, setServersPage] = useState(1);
  const [serversOrderColumn, setServersOrderColumn] = useState("last_seen");
  const [serversOrderDirection, setServersOrderDirection] = useState("DESC");
  const [serversRevision, setServersRevision] = useState(new Date());

  const perPage = 25;

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
  }

  const applyTimeRange = (newTimeRange) => {
    setPage(1);
    setTimeRange(newTimeRange);
  }

  const applyOrderColumn = (column) => {
    setPage(1);
    setOrderColumn(column);
  }

  const applyOrderDirection = (direction) => {
    setPage(1);
    setOrderDirection(direction);
  }

  const applyServersOrderColumn = (column) => {
    setServersPage(1);
    setServersOrderColumn(column);
  }

  const applyServersOrderDirection = (direction) => {
    setServersPage(1);
    setServersOrderDirection(direction);
  }

  useEffect(() => {
    setFindings(null);
    spoofingService.findAllDhcpFindings(
      organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setFindings
    );
  }, [page, filters, timeRange, orderColumn, orderDirection, organizationId, tenantId, revision]);

  useEffect(() => {
    setHistogram(null);
    spoofingService.findActiveDhcpFindingsHistogram(organizationId, tenantId, filters, timeRange, setHistogram);
  }, [organizationId, tenantId, filters, timeRange, revision]);

  useEffect(() => {
    setServers(null);
    spoofingService.findAllDhcpServers(
      organizationId, tenantId, {}, serversOrderColumn, serversOrderDirection, perPage, (serversPage-1)*perPage, setServers
    );
  }, [serversPage, serversOrderColumn, serversOrderDirection, organizationId, tenantId, serversRevision]);

  // A trust change affects findings too.
  const onServersChanged = () => {
    setServersRevision(new Date());
    setRevision(new Date());
  }

  const learningPeriodStatus = () => {
    if (!servers.learning_period_start) {
      return <span className="text-muted">Not started.</span>
    }

    if (servers.learning_period_active) {
      return <span title={moment(servers.learning_period_end).format()}>
        Active for {servers.learning_protocols.map(dhcpSpoofingProtocolToName).join(", ")}, ends{' '}
        {moment(servers.learning_period_end).fromNow()}. What is seen of these now is trusted.
      </span>
    }

    return <span title={moment(servers.learning_period_end).format()}>
      Ended {servers.learning_period_ended_by_user ? "by a user " : ""}{moment(servers.learning_period_end).fromNow()}.
      New servers and routers are not trusted.
    </span>
  }

  const onEndLearningPeriod = (e) => {
    e.preventDefault();

    if (!confirm("End the learning period now? Servers and routers that show up from now on are not trusted " +
        "automatically. Do this once every server and router you expect is listed.")) {
      return;
    }

    spoofingService.endDhcpLearningPeriod(organizationId, tenantId, () => {
      toast.success("Learning period ended.");
      onServersChanged();
    });
  }

  const onRestartLearningPeriod = (e) => {
    e.preventDefault();

    if (!confirm("Restart the learning period? Every server and router seen during the learning period from " +
        "now on is trusted automatically, including the ones that are currently untrusted. Only do this if you are " +
        "sure no rogue server or router is active right now. Trusted ones and existing findings are kept.")) {
      return;
    }

    spoofingService.restartDhcpLearningPeriod(organizationId, tenantId, () => {
      toast.success("Learning period restarted.");
      onServersChanged();
    });
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={SPOOFING_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SPOOFING.DHCP.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-spoofing-dhcp" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true} />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={DHCP_SPOOFING_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Active DHCP and Router Spoofing"
                                     refreshAction={() => setRevision(new Date())} />

              <ActiveSpoofingFindingsHistogram histogram={histogram}
                                               timeRange={timeRange}
                                               setTimeRange={applyTimeRange}
                                               emptyMessage="No DHCP or router spoofing in the selected time range." />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="DHCP and Router Spoofing"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                A <em>rogue server</em> finding is a DHCP server, DHCPv6 server or IPv6 router that answered
                clients or advertised itself while it is not trusted. A <em>configuration changed</em> finding is a
                trusted one that handed out a gateway, DNS server, domain, subnet mask or prefix different from what
                it handed out before. Victims are the clients that accepted a lease from the server or chose its
                offer. A router advertisement goes to every host of the network, so it has no list of victims. The
                list covers findings that were active at any point in the selected time range.
              </p>

              {findings ? <p className="mb-3"><strong>Total:</strong> {numeral(findings.total).format("0,0")}</p> : null}

              <DhcpSpoofingTable findings={findings}
                                 page={page}
                                 setPage={setPage}
                                 perPage={perPage}
                                 orderColumn={orderColumn}
                                 setOrderColumn={applyOrderColumn}
                                 orderDirection={orderDirection}
                                 setOrderDirection={applyOrderDirection}
                                 filterFields={DHCP_SPOOFING_FILTER_FIELDS}
                                 setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="DHCP Servers and IPv6 Routers"
                                     refreshAction={() => setServersRevision(new Date())} />

              <p className="text-muted">
                Every DHCP server and DHCPv6 server that answered clients and every IPv6 router that sent router
                advertisements, regardless of time range. DHCP, DHCPv6 and router advertisements each have a
                learning period. For DHCP it starts when the first server is seen. For DHCPv6 and router
                advertisements it starts when Nzyme first looks for them, so a server or router that appears later
                on a network without IPv6 is not trusted. Servers and routers seen during their learning period are
                trusted automatically. Later ones are not until you trust them. A trusted one is held to the
                configuration it handed out when it was trusted, and values that differ from it are highlighted.
                Accept the current configuration after an intended change to stop configuration changed findings.
                The prefixes that trusted routers advertise are treated as internal networks.
                {servers ? " At most " + numeral(servers.untrusted_limit).format("0,0") + " untrusted DHCP " +
                  "servers, DHCPv6 servers and routers each are kept." : null}
              </p>

              {servers && servers.untrusted_limit_reached_protocols.length > 0 ?
                <div className="alert alert-warning">
                  The limit of {numeral(servers.untrusted_limit).format("0,0")} untrusted entries is reached
                  for: {servers.untrusted_limit_reached_protocols.map(dhcpSpoofingServerNoun).join(", ")}. New
                  untrusted ones are not recorded and raise no finding until existing ones are trusted or deleted.
                  A flood of forged advertisements or replies fills the list like this.
                </div> : null}

              {servers ?
                <p className="mb-3">
                  <strong>Total:</strong> {numeral(servers.total).format("0,0")}
                  {' '}&middot;{' '}
                  <strong>Learning Period:</strong>{' '}
                  {learningPeriodStatus()}
                  {canManage ?
                    <React.Fragment>
                      {servers.learning_period_active ?
                        <React.Fragment>
                          {' '}<a href="#" onClick={onEndLearningPeriod}>End Now</a> &middot;
                        </React.Fragment> : null}
                      {' '}<a href="#" onClick={onRestartLearningPeriod}>Restart</a>
                    </React.Fragment> : null}
                </p> : null}

              <DhcpSpoofingServersTable servers={servers}
                                        page={serversPage}
                                        setPage={setServersPage}
                                        perPage={perPage}
                                        orderColumn={serversOrderColumn}
                                        setOrderColumn={applyServersOrderColumn}
                                        orderDirection={serversOrderDirection}
                                        setOrderDirection={applyServersOrderDirection}
                                        canManage={canManage}
                                        onChange={onServersChanged} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
