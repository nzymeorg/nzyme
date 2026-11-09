import React, {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import {toast} from "react-toastify";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetsService from "../../../../services/ethernet/AssetsService";
import ApiRoutes from "../../../../util/ApiRoutes";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import TcpFingerprint from "../identity/TcpFingerprint";
import {changeSignalTitle, linkKindTitle, linkStatus} from "../identity/AssetIdentityHelpers";
import {serializeTimeRange} from "../../../shared/timerange/TimeRangeUrl";
import {Relative} from "../../../shared/timerange/TimeRange";

const assetsService = new AssetsService();

export default function AssetIdentityNoticesCard({asset, organizationId, tenantId, revision, onRefresh, canEdit}) {

  const [summary, setSummary] = useState(null);
  const [localRevision, setLocalRevision] = useState(0);

  useEffect(() => {
    setSummary(null);
    assetsService.getAssetIdentitySummary(asset.uuid, organizationId, tenantId, setSummary);
  }, [asset.uuid, organizationId, tenantId, revision, localRevision]);

  const setLinkStatus = (link, status) => (e) => {
    e.preventDefault();

    assetsService.setAssetLinkStatus(link.uuid, organizationId, tenantId, status, () => {
      toast.success("Link " + status.toLowerCase() + ".");
      setLocalRevision(localRevision + 1);
    });
  }

  const title = <CardTitleWithControls title="Identity Notices" refreshAction={onRefresh} />;

  if (!summary) {
    return <React.Fragment>{title}<LoadingSpinner /></React.Fragment>
  }

  // The lists are capped by the backend, so the collapsed counts come from the reported totals.
  const activeDuplicates = summary.duplicates.filter((d) => d.recent);
  const pastDuplicates = summary.duplicates.filter((d) => !d.recent).slice(0, 1);
  const olderDuplicatesCount = summary.duplicates_total - activeDuplicates.length - pastDuplicates.length;

  const recentChanges = summary.changes.filter((c) => c.recent);
  const pastChanges = summary.changes.filter((c) => !c.recent).slice(0, 1);
  const olderChangesCount = summary.changes_total - recentChanges.length - pastChanges.length;

  const findingsListLink = (route) => {
    const filters = {mac: [{field: "mac", operator: "equals", value: asset.mac.address}]};
    const timeRange = serializeTimeRange(Relative(summary.period_days * 24 * 60));

    return route + "?filters=" + encodeURIComponent(JSON.stringify(filters))
      + "&tr_identity=" + encodeURIComponent(JSON.stringify(timeRange));
  }

  const olderFindings = (count, noun, route) => {
    if (count <= 0) {
      return null;
    }

    return (
      <p className="text-muted mb-3">
        {numeral(count).format("0,0")} older {noun} {count === 1 ? "finding was" : "findings were"} recorded
        for this asset in the last {summary.period_days} days.{' '}
        <Link to={findingsListLink(route)}>Show all findings</Link>
      </p>
    )
  }

  if (summary.duplicates.length === 0 && summary.changes.length === 0 && summary.links.length === 0) {
    return (
      <React.Fragment>
        {title}
        <div className="alert alert-success mb-0">
          No identity notices. One stack uses this address, it has not changed its identity recently, and it is not
          linked to another address.
        </div>
      </React.Fragment>
    )
  }

  const otherMac = (link) => link.successor_mac === asset.mac.address ? link.predecessor_mac : link.successor_mac;
  const otherMacData = (link) => link.successor_mac === asset.mac.address ? link.predecessor_mac_data : link.successor_mac_data;
  const linkDescription = (link) => {
    if (link.kind === "ALTERNATE") {
      return "This device alternates between this address and";
    }

    return link.successor_mac === asset.mac.address
      ? "This asset appeared when the following address went silent. It may be the same device as"
      : "This asset went silent and may have continued under";
  }

  return (
    <React.Fragment>
      {title}

      {activeDuplicates.map((d) => {
        return (
          <div className="alert alert-danger" key={d.uuid}>
            <strong>Duplicate MAC address.</strong> {numeral(d.stack_count).format("0,0")} different TCP stacks are
            using this address right now: {d.stacks.map((s, i) => <React.Fragment key={s.fingerprint}>{i > 0 ? ", " : ""}<TcpFingerprint value={s.fingerprint} id={s.fingerprint_id} compact /></React.Fragment>)}.{' '}
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATE_DETAILS(d.uuid)} className="alert-link">Open finding</Link>
          </div>
        )
      })}

      {pastDuplicates.map((d) => {
        return (
          <div className="alert alert-warning" key={d.uuid}>
            <strong>Duplicate MAC address</strong> <span title={moment(d.last_seen).format()}>{moment(d.last_seen).fromNow()}</span>:{' '}
            {numeral(d.stack_count).format("0,0")} different TCP stacks used this address at the same time.{' '}
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATE_DETAILS(d.uuid)} className="alert-link">Open finding</Link>
          </div>
        )
      })}

      {olderFindings(olderDuplicatesCount, "duplicate MAC address", ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATES)}

      {[...recentChanges, ...pastChanges].map((c) => {
        return (
          <div className={"alert " + (c.recent ? "alert-warning" : "alert-secondary")} key={c.uuid}>
            <strong>Identity changed</strong> <span title={moment(c.last_seen).format()}>{moment(c.last_seen).fromNow()}</span>{' '}
            (score {numeral(c.score).format("0,0")}): {c.changes.map((ch) => changeSignalTitle(ch.signal)).join(", ")}.{' '}
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.CHANGE_DETAILS(c.uuid)} className="alert-link">Open finding</Link>
          </div>
        )
      })}

      {olderFindings(olderChangesCount, "identity change", ApiRoutes.ETHERNET.ASSETS.IDENTITY.CHANGES)}

      {summary.links.map((l) => {
        return (
          <div className="alert alert-info" key={l.uuid}>
            <strong>{linkKindTitle(l.kind)}.</strong> {linkDescription(l)}{' '}
            {otherMacData(l) ? <EthernetMacAddress addressWithContext={otherMacData(l)} withAssetLink withAssetName />
              : <span className="machine-data">{otherMac(l)}</span>}{' '}
            (confidence {numeral(l.confidence).format("0,0")}) {linkStatus(l.status)}{' '}
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.LINK_DETAILS(l.uuid)} className="alert-link">Evidence</Link>
            {canEdit && l.status === "OPEN" ?
              <span className="ms-2">
                <a href="#" className="btn btn-sm btn-outline-success me-1" onClick={setLinkStatus(l, "CONFIRMED")}>Confirm</a>
                <a href="#" className="btn btn-sm btn-outline-secondary" onClick={setLinkStatus(l, "DISMISSED")}>Dismiss</a>
              </span> : null}
          </div>
        )
      })}
    </React.Fragment>
  )

}
