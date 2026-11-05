import React, {useEffect, useState} from "react";
import moment from "moment";
import PivotLink from "../../../shared/PivotLink";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetsService from "../../../../services/ethernet/AssetsService";
import ApiRoutes from "../../../../util/ApiRoutes";
import {exclusionTitle, formatCount} from "./AssetProfileHelpers";

const assetsService = new AssetsService();

const detailsRoute = (type, uuid) => {
  switch (type) {
    case "DHCP_FINGERPRINT": return ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.DETAILS(uuid);
    case "SPOOFING": return ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.DETAILS(uuid);
    case "BEACON": return ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.DETAILS(uuid);
    case "ICMP": return ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.DETAILS(uuid);
    case "RECON": return ApiRoutes.ETHERNET.RECON.SCANS.EXCLUSIONS.DETAILS(uuid);
    case "DNS_TUNNEL": return ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.DETAILS(uuid);
    default: return null;
  }
}

export default function AssetExclusionsCard({asset, organizationId, tenantId, revision, onRefresh}) {

  const [exclusions, setExclusions] = useState(null);

  useEffect(() => {
    setExclusions(null);
    assetsService.getAssetProfileExclusions(asset.uuid, organizationId, tenantId, setExclusions);
  }, [asset.uuid, organizationId, tenantId, revision]);

  const title = (
    <React.Fragment>
      <CardTitleWithControls title="Exclusions"
                             refreshAction={onRefresh} />
      <p className="text-muted">Detection exclusions that match this asset directly or through one of its IP addresses.</p>
    </React.Fragment>
  );

  if (!exclusions) {
    return <React.Fragment>{title}<LoadingSpinner /></React.Fragment>
  }

  if (exclusions.exclusions.length === 0) {
    return (
      <React.Fragment>
        {title}
        <div className="alert alert-info mb-0">This asset is not covered by any exclusion. All detection methods apply to it.</div>
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      {title}

      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>Detection</th>
          <th>Exclusion</th>
          <th>Matched By</th>
          <th>Matches</th>
          <th>Last Matched</th>
        </tr>
        </thead>
        <tbody>
        {exclusions.exclusions.map((e) => {
          const route = detailsRoute(e.type, e.uuid);
          return (
            <tr key={e.type + "-" + e.uuid}>
              <td>{exclusionTitle(e.type)}</td>
              <td>
                {route ? <PivotLink to={route}>{e.name}</PivotLink> : e.name}{' '}
                {e.enabled ? null : <span className="badge bg-secondary">Disabled</span>}
              </td>
              <td>{e.matched_by}</td>
              <td>{formatCount(e.match_count)}</td>
              <td>{e.last_matched_at ? <span title={moment(e.last_matched_at).format()}>{moment(e.last_matched_at).fromNow()}</span> : <span className="text-muted">Never</span>}</td>
            </tr>
          )
        })}
        </tbody>
      </table>
    </React.Fragment>
  )

}
