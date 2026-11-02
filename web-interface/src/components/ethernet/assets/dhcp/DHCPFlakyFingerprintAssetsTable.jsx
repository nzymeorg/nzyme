import React, {useContext, useState} from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../../misc/Paginator";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import {DHCP_FILTER_FIELDS} from "./DHCPFilterFields";
import ApiRoutes from "../../../../util/ApiRoutes";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";

export default function DHCPFlakyFingerprintAssetsTable(props) {

  const data = props.data;
  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;
  const setFilters = props.setFilters;

  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_assets_manage");

  const [expandedRow, setExpandedRow] = useState(null);

  const exclusionState = (a) => {
    if (a.exclusions && a.exclusions.length > 0) {
      return (
          <span className="text-muted">
            Excluded by {a.exclusions.map((ex, j) => (
              <React.Fragment key={ex.uuid}>
                {j > 0 ? ", " : null}
                <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.DETAILS(ex.uuid)}>{ex.name}</Link>
              </React.Fragment>
          ))}
          </span>
      )
    }

    if (!canEdit) {
      return <span className="text-muted">Alerting</span>;
    }

    return (
        <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.CREATE + "?mac=" + encodeURIComponent(a.mac.address)}>
          Exclude
        </Link>
    )
  }

  const toggleRow = (e, i) => {
    e.preventDefault();
    setExpandedRow(expandedRow === i ? null : i);
  }

  const fingerprintsRow = (i, a) => {
    if (expandedRow !== i) {
      return null;
    }

    return (
        <tr>
          <td colSpan={8}>
            <strong>All {numeral(a.fingerprint_count).format("0,0")} fingerprints:</strong>
            <ul className="mb-0 mt-1">
              {a.fingerprints.map((fp) => (
                  <li key={fp}>
                    <span className="machine-data">{fp}</span>{' '}
                    <FilterValueIcon setFilters={setFilters}
                                     fields={DHCP_FILTER_FIELDS}
                                     field="fingerprint"
                                     value={fp} />
                  </li>
              ))}
            </ul>
          </td>
        </tr>
    )
  }

  if (!data) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (data.assets.length === 0) {
    return (
        <div className="mb-0 alert alert-info">
          No assets with flaky DHCP fingerprints were observed during selected time range.
        </div>
    )
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(data.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th>Client MAC</th>
            <th>Transaction Types</th>
            <th>Fingerprints</th>
            <th>Changes</th>
            <th className="hide-narrow">Transactions</th>
            <th className="hide-narrow">First Seen</th>
            <th>Last Seen</th>
            <th>Exclusion</th>
          </tr>
          </thead>
          <tbody>
          {data.assets.map((a, i) => {
            return (
                <React.Fragment key={i}>
                <tr>
                  <td>
                    <EthernetMacAddress addressWithContext={a.mac}
                                        filterElement={<FilterValueIcon setFilters={setFilters}
                                                                        fields={DHCP_FILTER_FIELDS}
                                                                        field="client_mac"
                                                                        value={a.mac.address} />}
                                        withAssetName withAssetLink />
                  </td>
                  <td>{a.transaction_types.join(", ")}</td>
                  <td>
                    {numeral(a.fingerprint_count).format("0,0")}{' '}
                    <a href="#" onClick={(e) => toggleRow(e, i)}>
                      {expandedRow === i ? "Hide" : "Show"}
                    </a>
                  </td>
                  <td>{numeral(a.change_count).format("0,0")}</td>
                  <td className="hide-narrow">{numeral(a.transaction_count).format("0,0")}</td>
                  <td className="hide-narrow">{moment(a.first_seen).format()}</td>
                  <td>{moment(a.last_seen).format()}</td>
                  <td>{exclusionState(a)}</td>
                </tr>
                {fingerprintsRow(i, a)}
                </React.Fragment>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={data.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  );

}
