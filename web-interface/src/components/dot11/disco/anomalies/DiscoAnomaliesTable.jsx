import React from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import AutomaticDot11MacAddressLink from "../../../shared/context/macs/AutomaticDot11MacAddressLink";
import ApiRoutes from "../../../../util/ApiRoutes";
import {DISCO_ANOMALY_FILTER_FIELDS} from "./DiscoAnomalyFilterFields";
import {discoAnomalySummary, discoAnomalyTypeToName} from "./DiscoAnomalyTypes";
import DiscoAnomalyClient from "./DiscoAnomalyClient";
import PivotLink from "../../../shared/PivotLink";
import {discoAnomalySignalLink} from "../signal/DiscoSignalLinks";

export default function DiscoAnomaliesTable(props) {

  const anomalies = props.anomalies;
  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;
  const setFilters = props.setFilters;

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={props.orderColumn}
                          setOrderColumn={props.setOrderColumn}
                          orderDirection={props.orderDirection}
                          setOrderDirection={props.setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    if (!setFilters) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters} fields={DISCO_ANOMALY_FILTER_FIELDS} field={field} value={value} />
  }

  if (!anomalies) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (anomalies.total === 0) {
    return <div className="alert alert-info mb-0">No anomalies recorded.</div>
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(anomalies.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th>Anomaly {columnSorting("anomaly_type")}</th>
            <th>Access Point {columnSorting("bssid")}</th>
            <th>Client {columnSorting("client")}</th>
            <th>Observed</th>
            <th>Tap</th>
            <th style={{width: 210}}>Last Seen {columnSorting("last_seen")}</th>
          </tr>
          </thead>
          <tbody>
          {anomalies.anomalies.map((anomaly) => {
            return (
                <tr key={anomaly.uuid}>
                  <td>
                    <Link to={ApiRoutes.DOT11.DISCO.ANOMALIES.DETAILS(anomaly.uuid)}>
                      {discoAnomalyTypeToName(anomaly.anomaly_type, anomaly.title)}
                    </Link>
                    {filterIcon("anomaly_type", anomaly.anomaly_type)}
                  </td>
                  <td>
                    <AutomaticDot11MacAddressLink mac={anomaly.bssid.mac.address}
                                                  type={anomaly.bssid.type}
                                                  addressWithContext={anomaly.bssid.mac}
                                                  filterElement={filterIcon("bssid", anomaly.bssid.mac.address)} />
                  </td>
                  <td>
                    <DiscoAnomalyClient anomaly={anomaly}
                                        filterElement={anomaly.client ? filterIcon("client", anomaly.client.mac.address) : null} />
                  </td>
                  <td>
                    {discoAnomalySummary(anomaly)}
                    {discoAnomalySignalLink(anomaly) ?
                        <PivotLink to={discoAnomalySignalLink(anomaly)}
                                   title="Signal strength investigation of these frames"
                                   icon={true}
                                   className="ms-2 small" /> : null}
                  </td>
                  <td>{anomaly.tap_name ? anomaly.tap_name : <span className="text-muted">Unknown</span>}</td>
                  <td title={"First seen " + moment(anomaly.first_seen).format()}>
                    {anomaly.is_ongoing ? "Ongoing" : moment(anomaly.last_seen).format()}
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={anomalies.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}
