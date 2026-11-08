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
  duplicates: {
    title: "Duplicate MAC Addresses", fields: DUPLICATE_MAC_FILTER_FIELDS, defaultOrder: "last_seen",
    help: "https://go.nzyme.org/ethernet-assets-identity-duplicates"
  },
  changes: {
    title: "Identity Changes", fields: IDENTITY_CHANGE_FILTER_FIELDS, defaultOrder: "last_seen",
    help: "https://go.nzyme.org/ethernet-assets-identity-changes"
  },
  links: {
    title: "MAC Address Changes", fields: ASSET_LINK_FILTER_FIELDS, defaultOrder: "detected_at",
    help: "https://go.nzyme.org/ethernet-assets-identity-mac-changes"
  }
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
            Two or more TCP stacks used the same MAC address <strong>at the same time</strong>, none of them behind
            a router or NAT box. An identity that was replaced over time is an{' '}
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.CHANGES}>identity change</Link> instead.
          </p>
        );
      case "changes":
        return (
          <p className="text-muted">
            An established asset <strong>became a different device over time</strong> under the same MAC address.
            Two identities that are active at the same time are a{' '}
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATES}>duplicate MAC address</Link> instead.
          </p>
        );
      default:
        return (
          <p className="text-muted">
            A new asset looks like a device that recently went silent under another MAC address.
          </p>
        );
    }
  }

  const table = () => {
    const props = {
      page: page, setPage: setPage, perPage: PER_PAGE, orderColumn: orderColumn, setOrderColumn: applyOrderColumn,
      orderDirection: orderDirection, setOrderDirection: applyOrderDirection, filterFields: fields, setFilters: applyFilters,
      bulk: {
        organizationId: organizationId, tenantId: tenantId, filters: filters, timeRange: timeRange,
        onChanged: () => setRevision(new Date())
      }
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
        <div className="col-md-12">
          <SectionMenuBar items={ASSETS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.ASSETS.IDENTITY.TAB(tab)}/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true}
                                     helpLink={current.help} />

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
