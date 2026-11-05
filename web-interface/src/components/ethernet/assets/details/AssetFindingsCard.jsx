import React, {useEffect, useState} from "react";
import moment from "moment";
import PivotLink from "../../../shared/PivotLink";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetsService from "../../../../services/ethernet/AssetsService";
import ApiRoutes from "../../../../util/ApiRoutes";
import ColumnSorting from "../../../shared/ColumnSorting";
import {filteredLink, findingTitle, formatCount, macFilter, alertRoleTitle, sortRows} from "./AssetProfileHelpers";

const assetsService = new AssetsService();

const findingLink = (type, mac) => {
  switch (type) {
    case "RECON_SCAN": return filteredLink(ApiRoutes.ETHERNET.RECON.SCANS.INDEX, macFilter("source_mac", mac));
    case "DNS_TUNNEL": return filteredLink(ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX, macFilter("client_mac", mac));
    case "ICMP_TUNNEL": return filteredLink(ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX, macFilter("source_mac", mac));
    case "ICMP_TRACEROUTE": return filteredLink(ApiRoutes.ETHERNET.ICMP.TRACEROUTES.INDEX, macFilter("source_mac", mac));
    case "ICMP_REDIRECT": return ApiRoutes.ETHERNET.ICMP.REDIRECTS.INDEX;
    case "SPOOFING_ARP": return ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX;
    case "SPOOFING_DHCP": return ApiRoutes.ETHERNET.SPOOFING.DHCP.INDEX;
    case "SPOOFING_DNS": return ApiRoutes.ETHERNET.SPOOFING.DNS.INDEX;
    case "SPOOFING_NAME_RESOLUTION": return ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.INDEX;
    default:
      if (type.startsWith("BEACON_")) {
        return ApiRoutes.ETHERNET.BEACONS.INDEX;
      }
      return null;
  }
}

export default function AssetFindingsCard({asset, organizationId, tenantId, revision, onRefresh}) {

  const [findings, setFindings] = useState(null);
  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setFindings(null);
    assetsService.getAssetProfileFindings(asset.uuid, organizationId, tenantId, setFindings);
  }, [asset.uuid, organizationId, tenantId, revision]);

  const mac = asset.mac.address;

  const title = (
    <React.Fragment>
      <CardTitleWithControls title="Findings" refreshAction={onRefresh} />
      <p className="text-muted">Actor: this asset caused the finding. Victim: it was the target. Subject: the finding is about the asset itself.</p>
    </React.Fragment>
  );

  if (!findings) {
    return <React.Fragment>{title}<LoadingSpinner /></React.Fragment>
  }

  const sorting = (column) => (
    <ColumnSorting thisColumn={column}
                   orderColumn={orderColumn}
                   setOrderColumn={setOrderColumn}
                   orderDirection={orderDirection}
                   setOrderDirection={setOrderDirection} />
  );

  const rows = sortRows(findings.findings, orderColumn, orderDirection, {
    finding: (f) => findingTitle(f.type),
    role: (f) => alertRoleTitle(f.role),
    count: (f) => f.count,
    last_seen: (f) => f.last_seen
  });

  return (
    <React.Fragment>
      {title}

      {findings.findings.length === 0
        ? <div className="alert alert-info mb-0">No scans, beacons, tunnels or spoofing findings involve this asset.</div>
        : <table className="table table-sm table-hover table-striped mb-0">
            <thead>
            <tr>
              <th>Finding {sorting("finding")}</th>
              <th>Role {sorting("role")}</th>
              <th>Count {sorting("count")}</th>
              <th>Last Seen {sorting("last_seen")}</th>
            </tr>
            </thead>
            <tbody>
            {rows.map((f) => {
              const link = findingLink(f.type, mac);
              return (
                <tr key={f.type + "-" + f.role}>
                  <td>{link ? <PivotLink to={link}>{findingTitle(f.type)}</PivotLink> : findingTitle(f.type)}</td>
                  <td>{alertRoleTitle(f.role)}</td>
                  <td>{formatCount(f.count)}</td>
                  <td>{f.last_seen ? <span title={moment(f.last_seen).format()}>{moment(f.last_seen).fromNow()}</span> : <span className="text-muted">n/a</span>}</td>
                </tr>
              )
            })}
            </tbody>
          </table>}
    </React.Fragment>
  )

}
