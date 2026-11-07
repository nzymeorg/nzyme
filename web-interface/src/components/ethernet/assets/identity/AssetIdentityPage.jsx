import React, {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import Filters from "../../../shared/filtering/Filters";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeUrl";
import {Presets} from "../../../shared/timerange/TimeRange";
import AssetsService from "../../../../services/ethernet/AssetsService";
import {ASSETS_MENU_ITEMS} from "../AssetsMenuItems";
import {DUPLICATE_MAC_FILTER_FIELDS} from "./DuplicateMacFilterFields";
import {IDENTITY_CHANGE_FILTER_FIELDS} from "./IdentityChangeFilterFields";
import {ASSET_LINK_FILTER_FIELDS} from "./AssetLinkFilterFields";
import DuplicateMacFindingsTable from "./DuplicateMacFindingsTable";
import IdentityChangeFindingsTable from "./IdentityChangeFindingsTable";
import AssetLinksTable from "./AssetLinksTable";

const assetsService = new AssetsService();

const PAGES = {
  duplicates: { title: "Duplicate MAC Addresses", fields: DUPLICATE_MAC_FILTER_FIELDS, defaultOrder: "last_seen" },
  changes: { title: "Identity Changes", fields: IDENTITY_CHANGE_FILTER_FIELDS, defaultOrder: "last_seen" },
  links: { title: "MAC Address Changes", fields: ASSET_LINK_FILTER_FIELDS, defaultOrder: "detected_at" }
};

const PER_PAGE = 25;

export default function AssetIdentityPage({tab}) {

  const current = PAGES[tab];

  usePageTitle("Ethernet Asset " + current.title);

  const [organizationId, tenantId] = useSelectedTenant();
  const fields = current.fields;

  const [filters, setFilters] = useUrlFilters(fields);
  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24, "identity");
  const [orderColumn, setOrderColumn] = useState(current.defaultOrder);
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [data, setData] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

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

  useEffect(() => {
    setData(null);
    const offset = (page - 1) * PER_PAGE;

    // A response is tagged with its page so that a late response never feeds a table the wrong shape.
    const receive = (response) => setData({tab: tab, response: response});

    if (tab === "duplicates") {
      assetsService.findAllDuplicateMacFindings(organizationId, tenantId, filters, timeRange, orderColumn,
        orderDirection, PER_PAGE, offset, receive);
    } else if (tab === "changes") {
      assetsService.findAllIdentityChangeFindings(organizationId, tenantId, filters, timeRange, orderColumn,
        orderDirection, PER_PAGE, offset, receive);
    } else {
      assetsService.findAllAssetLinks(organizationId, tenantId, filters, timeRange, orderColumn,
        orderDirection, PER_PAGE, offset, receive);
    }
  }, [tab, page, filters, timeRange, orderColumn, orderDirection, organizationId, tenantId, revision]);

  const description = () => {
    switch (tab) {
      case "duplicates":
        return (
          <p className="text-muted">
            Two devices used the same MAC address <strong>at the same time</strong>. Both were active in the same
            detection window, so this is not an asset that changed over time but two that overlap. Nzyme tells
            devices apart by how their TCP stacks open connections, and only counts stacks that sent their packets
            directly, not through a router, NAT box, hotspot or VM host that forwards traffic for other devices.
            A wireless bridge that hides its clients behind one address is the common legitimate cause. Add an
            exclusion for it. An asset whose old identity disappeared and was replaced by a new one is an{' '}
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.CHANGES}>identity change</Link> instead. The detection
            runs every minute, so a finding appears about a minute after the taps reported the sessions.
          </p>
        );
      case "changes":
        return (
          <p className="text-muted">
            An established asset <strong>became a different device over time</strong>: the identity it had is gone
            and a new one took its place under the same MAC address. Signals are a TCP stack that replaced the old
            one, a new hostname, a different DHCP client or new server ports. Each change is scored and a finding
            is raised at the threshold. Reinstalled machines, dual-boot systems and a network card moved to
            another machine look like this too, as does an attacker who cloned the address of a device that is now
            offline. Two identities that are active at the same time are a{' '}
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATES}>duplicate MAC address</Link> instead. The
            detection runs every minute.
          </p>
        );
      default:
        return (
          <p className="text-muted">
            A new asset looks like a device that recently went silent under another MAC address. The evidence is
            shared hostnames, addresses, DHCP identity, TCP stacks, TLS clients and inbound ports, with rare values
            counting more than ones a whole fleet shares. Confirm a link when it is right, dismiss it when it is not.
            Devices whose old address comes back later are shown as alternating between both. The detection runs
            every minute, but a new asset is first evaluated once it has existed for the quiet period, and again at
            6 hours, 24 hours and the end of the lookback as it collects more identity.
          </p>
        );
    }
  }

  const table = () => {
    const props = {
      page: page, setPage: setPage, perPage: PER_PAGE, orderColumn: orderColumn, setOrderColumn: applyOrderColumn,
      orderDirection: orderDirection, setOrderDirection: applyOrderDirection, filterFields: fields, setFilters: applyFilters
    };

    const response = data && data.tab === tab ? data.response : null;

    switch (tab) {
      case "duplicates": return <DuplicateMacFindingsTable findings={response} {...props} />;
      case "changes": return <IdentityChangeFindingsTable findings={response} {...props} />;
      default: return <AssetLinksTable links={response} {...props} />;
    }
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={ASSETS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.ASSETS.IDENTITY.TAB(tab)}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-assets-identity" className="btn btn-secondary float-end">Help</a>
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

              <Filters key={tab}
                       filters={filters}
                       setFilters={applyFilters}
                       fields={fields}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title={current.title}
                                     refreshAction={() => setRevision(new Date())} />

              {description()}

              {table()}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
