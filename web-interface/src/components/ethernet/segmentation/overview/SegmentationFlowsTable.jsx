import React from "react";
import moment from "moment";
import Flag from "../../../misc/Flag";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import L4Address from "../../shared/L4Address";
import PivotLink from "../../../shared/PivotLink";
import {filtersApplied} from "../../../../util/Tools";
import AssetTags from "../../assets/AssetTags";
import {formatCount} from "../../assets/details/AssetProfileHelpers";
import {flowServiceLabel, FOLDED_PORT, OUTCOMES} from "../SegmentationFormat";
import {flowSessionsLink} from "../SegmentationPivots";
import {LONG_LIVED_DESCRIPTION, outcomeColor, outcomeTitle} from "./SegmentationOverviewTheme";

const flowKey = (flow) => [flow.source.key, flow.destination.key, flow.l4_type, flow.port, flow.protocol, flow.outcome,
  flow.tap_uuid].join("|");

function FlowSide({side, filterIcon, foldedNote}) {
  const tags = side.tags && side.tags.length > 0
      ? <span className="ms-2"><AssetTags tags={side.tags.map((name) => ({name: name}))} /></span>
      : null;

  if (side.address) {
    return (
        <React.Fragment>
          <L4Address address={side.address} hidePort={true} withAssetName hideSegment
                     filterElement={filterIcon("address", side.address.address)} />
          {tags}
        </React.Fragment>
    );
  }

  if (side.broadcast) {
    return (
        <span title={foldedNote}>Many broadcast or multicast addresses</span>
    );
  }

  if (side.internal) {
    return (
        <span title={foldedNote}>Many addresses in {side.segment_name ? side.segment_name : "Unassigned"}</span>
    );
  }

  const network = side.asn !== null && side.asn !== undefined ? "AS" + side.asn : null;
  const name = side.segment_name ? side.segment_name : (network ? network : (side.country_code ? side.country_code : "Internet"));

  return (
      <span>
        {side.country_code ? <React.Fragment><Flag code={side.country_code} />{' '}</React.Fragment> : null}
        {name}
        {side.segment_name && (network || side.country_code)
            ? <span className="text-muted"> ({[network, side.country_code].filter((x) => x).join(", ")})</span>
            : (network && side.country_code ? <span className="text-muted"> ({side.country_code})</span> : null)}
        {network ? filterIcon("asn", side.asn) : filterIcon("country_code", side.country_code)}
        {tags}
      </span>
  );
}

export default function SegmentationFlowsTable({flows, page, setPage, perPage, orderColumn, setOrderColumn,
                                                 orderDirection, setOrderDirection, filterFields, filters, setFilters,
                                                 colors, timeRange}) {

  const sessions = (flow) => {
    const link = flowSessionsLink(flow, timeRange);
    return link ? <PivotLink to={link} title="Sessions of this flow" icon={true} /> : null;
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} filters={filters} fields={filterFields} field={field} value={value} />
  }

  if (!flows) {
    return <GenericWidgetLoadingSpinner height={120} />
  }

  if (flows.flows.length === 0) {
    return (
        <div className="alert alert-info mb-0">
          {filtersApplied(filters)
              ? "No flows of this crossing match the current filters."
              : "No flows of this crossing are stored for the selected time range."}
        </div>
    )
  }

  const destinationsFolded = "More than " + flows.destinations_folded_above + " unanswered destinations of one "
      + "source per evaluation are stored as one flow.";
  const portsFolded = "More than " + flows.ports_folded_above + " unanswered ports of one destination per "
      + "evaluation are stored as one flow.";

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Source {columnSorting("source")}</th>
            <th>Destination {columnSorting("destination")}</th>
            <th>Service {columnSorting("port")}</th>
            <th>Outcome {columnSorting("outcome")}</th>
            <th>Sessions {columnSorting("sessions")}</th>
            <th>First Seen {columnSorting("first_seen")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
            <th></th>
          </tr>
          </thead>
          <tbody>
          {flows.flows.map((flow) => (
              <tr key={flowKey(flow)}>
                <td><FlowSide side={flow.source} filterIcon={(part, value) => filterIcon("source_" + part, value)} /></td>
                <td>
                  <FlowSide side={flow.destination}
                            foldedNote={destinationsFolded}
                            filterIcon={(part, value) => filterIcon("destination_" + part, value)} />
                </td>
                <td title={flow.port === FOLDED_PORT ? portsFolded : undefined}>
                  <span className="machine-data">{flowServiceLabel(flow)}</span>
                  {/* A flow that stands for many ports names none to filter by. */}
                  {filterIcon("port", flow.port === FOLDED_PORT ? null : flow.port)}
                </td>
                <td title={OUTCOMES[flow.outcome] ? OUTCOMES[flow.outcome].description : null}>
                  <span style={{display: "inline-block", width: "8px", height: "8px",
                    marginRight: "6px", background: outcomeColor(colors, flow.outcome)}} />
                  {outcomeTitle(flow.outcome)}
                  {filterIcon("outcome", flow.outcome)}
                </td>
                <td>
                  {/* The node counts sessions that were already open apart from new ones. */}
                  {formatCount(flow.sessions + flow.continued)}
                  {flow.continued > 0
                      ? <span className="text-muted" title={LONG_LIVED_DESCRIPTION}>
                          {' '}({formatCount(flow.continued)} long-lived)
                        </span>
                      : null}
                </td>
                <td title={moment(flow.first_seen).format()}>{moment(flow.first_seen).fromNow()}</td>
                <td title={moment(flow.last_seen).format()}>{moment(flow.last_seen).fromNow()}</td>
                <td className="text-end">{sessions(flow)}</td>
              </tr>
          ))}
          </tbody>
        </table>

        <Paginator itemCount={flows.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}
