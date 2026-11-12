import React, {useState} from "react";
import moment from "moment";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import ColumnSorting from "../../shared/ColumnSorting";
import PivotLink from "../../shared/PivotLink";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import Paginator from "../../misc/Paginator";
import Flag from "../../misc/Flag";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import TopologyService from "../../../services/ethernet/TopologyService";
import useTopologyRequest from "./useTopologyRequest";
import {formatBytes, formatCount} from "../assets/details/AssetProfileHelpers";
import {
  nodeDetail,
  nodeFilter,
  nodeLabel,
  nodeName,
  nodeSessionFilters,
  serviceLabel,
  serviceName,
  serviceSessionFilters,
  sessionsLink
} from "./TopologyData";

const topologyService = new TopologyService();

const PER_PAGE = 15;

export default function TopologyRankingCard({title, help = null, levels, presets = null, defaultOrder = "BYTES",
                                              defaultDirection = "DESC", request, revision, timeRange, onRefresh,
                                              pageFilters, onLocate, setFilters, filterFields}) {

  const [dimension, setDimension] = useState(levels[0][0]);
  const [orderColumn, setOrderColumn] = useState(presets ? presets[0][2] : defaultOrder);
  const [orderDirection, setOrderDirection] = useState(presets ? presets[0][3] : defaultDirection);
  const service = dimension.startsWith("SERVICE");
  const internal = dimension.startsWith("INTERNAL");

  // The page belongs to one selection and order. Any other one starts at the first page again.
  const selectionKey = JSON.stringify([request, dimension, orderColumn, orderDirection]);
  const [paging, setPaging] = useState({key: selectionKey, page: 1});
  const page = paging.key === selectionKey ? paging.page : 1;
  const setPage = (p) => setPaging({key: selectionKey, page: p});

  const data = useTopologyRequest(
      JSON.stringify([selectionKey, page, revision]),
      true,
      (setData) => topologyService.getRanking(request, dimension, orderColumn, orderDirection, PER_PAGE,
          (page - 1) * PER_PAGE, setData)
  );

  const preset = presets
      ? presets.find((p) => p[2] === orderColumn && p[3] === orderDirection) : null;

  const applyPreset = (key) => {
    const selected = presets.find((p) => p[0] === key);
    if (selected) {
      setOrderColumn(selected[2]);
      setOrderDirection(selected[3]);
    }
  }

  const header = (column, label, hint = null) => (
      <th className="text-nowrap" title={hint}>
        {label}{' '}
        <ColumnSorting thisColumn={column}
                       orderColumn={orderColumn}
                       setOrderColumn={setOrderColumn}
                       orderDirection={orderDirection}
                       setOrderDirection={setOrderDirection} />
      </th>
  )

  const time = (timestamp) => <span title={moment(timestamp).format()}>{moment(timestamp).fromNow()}</span>;

  const pivot = (filters) => sessionsLink(pageFilters, filters, timeRange, null);

  const nodeCell = (node) => {
    const filters = nodeSessionFilters(node, internal ? "source" : "destination");
    const name = nodeName(node);
    const detail = nodeDetail(node);
    const filter = nodeFilter(node);

    return (
        <div className="d-flex align-items-baseline">
          <button type="button" className="btn btn-link btn-sm p-0 me-2 align-baseline"
                  title="Show in visualization"
                  onClick={(e) => { e.preventDefault(); onLocate(node); }}>
            <i className="fa-solid fa-crosshairs" />
          </button>
          {node.type === "EXTERNAL" ? <span className="me-1"><Flag code={node.country_code ? node.country_code : "NONE"} /></span> : null}
          <span className="text-truncate" title={nodeLabel(node) + (name ? " " + name : "") + (detail ? " " + detail : "")}>
            <span className={node.level === "HOST" || node.level === "SUBNET" ? "machine-data" : ""}>{nodeLabel(node)}</span>
            {name ? <span className="context-name context-name-asset ms-1">{name}</span> : null}
            {detail ? <span className="text-muted ms-1">{detail}</span> : null}
          </span>
          {filter
              ? <span className="ms-2">
                  <FilterValueIcon setFilters={setFilters} fields={filterFields} field={filter.field}
                                   value={filter.value} operator={filter.operator} />
                </span>
              : null}
          {filters
              ? <PivotLink to={pivot(filters)} title={internal ? "Sessions from node" : "Sessions to node"} className="ms-2 small" icon={true} />
              : null}
        </div>
    );
  }

  // A session of a protocol carries all of its tags. One filter selects one tag, so every tag gets its own.
  const serviceParts = (s) => {
    if (s.l4_type) {
      return [{text: s.l4_type, field: "l4_type", value: s.l4_type},
        {text: String(s.port), field: "destination_port", value: String(s.port)}];
    }
    return s.protocol
        ? s.protocol.split("/").map((tag) => ({text: tag, field: "tags", value: tag}))
        : [{text: serviceLabel(s), field: null, value: null}];
  }

  const serviceCell = (s) => {
    const filters = serviceSessionFilters(s);
    const name = serviceName(s);

    return (
        <div className="d-flex align-items-baseline">
          <span className="text-truncate" title={serviceLabel(s) + (name ? " " + name : "")}>
            {serviceParts(s).map((part, i) => (
                <React.Fragment key={part.text}>
                  {i > 0 ? <span className="text-muted mx-1">/</span> : null}
                  <span>{part.text}</span>
                  {part.field
                      ? <FilterValueIcon setFilters={setFilters} fields={filterFields} field={part.field} value={part.value} />
                      : null}
                </React.Fragment>
            ))}
            {name ? <span className="text-muted ms-2">{name}</span> : null}
          </span>
          {filters
              ? <PivotLink to={pivot(filters)} title="Sessions of service" className="ms-2 small" icon={true} />
              : null}
        </div>
    );
  }

  const table = () => {
    if (data === null) {
      return <GenericWidgetLoadingSpinner height={440} />;
    }

    if (data.total === 0) {
      return (
          <div className="alert alert-info mb-0 mt-2">
            No sessions matching filters recorded.
          </div>
      );
    }

    return (
        <React.Fragment>
          <table className="table table-sm table-hover table-striped mb-3">
            <thead>
            <tr>
              <th>{service ? "Service" : "Node"}</th>
              {header("BYTES", "Traffic")}
              {header("SESSIONS", "Sessions")}
              {service
                  ? header("PEERS", "Clients", "Distinct source addresses")
                  : header("PEERS", "Peers", "Distinct addresses on the other end")}
              {service ? <th title="Distinct destination addresses">Servers</th> : null}
              {header("FIRST_SEEN", "First Seen")}
              {header("LAST_SEEN", "Last Seen")}
            </tr>
            </thead>
            <tbody>
            {data.rows.map((row) => (
                <tr key={service ? row.service.id : row.node.id}>
                  {/* The narrow maximum lets the column shrink below its content, which is then cut off. */}
                  <td style={{maxWidth: "1px", width: "42%"}}>{service ? serviceCell(row.service) : nodeCell(row.node)}</td>
                  <td title={service
                      ? formatBytes(row.bytes_sent) + " from clients, " + formatBytes(row.bytes_received) + " from servers"
                      : formatBytes(row.bytes_sent) + " sent, " + formatBytes(row.bytes_received) + " received"}>
                    {formatBytes(row.bytes_sent + row.bytes_received)}
                  </td>
                  <td title={service ? null : formatCount(row.sessions_initiated) + " initiated"}>{formatCount(row.sessions)}</td>
                  <td>{formatCount(service ? row.clients : row.peers)}</td>
                  {service ? <td>{formatCount(row.servers)}</td> : null}
                  <td className="text-nowrap">{time(row.first_seen)}</td>
                  <td className="text-nowrap">{time(row.last_seen)}</td>
                </tr>
            ))}
            </tbody>
          </table>

          <Paginator itemCount={data.total} perPage={PER_PAGE} setPage={setPage} page={page} />
        </React.Fragment>
    );
  }

  const id = "topology-ranking-" + title.toLowerCase().replace(/[^a-z]+/g, "-");

  return (
      <div className="card">
        <div className="card-body">
          <CardTitleWithControls title={title}
                                 timeRange={timeRange}
                                 helpLink="https://go.nzyme.org/ethernet-topology-rankings"
                                 refreshAction={onRefresh} />

          {help ? <p className="help-text">{help}</p> : null}

          <div className="d-flex flex-wrap align-items-center">
            {levels.length > 1 ?
                <div className="d-flex align-items-center me-3 mb-2">
                  <label htmlFor={id + "-level"} className="me-2 small text-muted mb-0">Rank</label>
                  <select id={id + "-level"} className="form-select form-select-sm w-auto" value={dimension}
                          onChange={(e) => setDimension(e.target.value)}>
                    {levels.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                  </select>
                </div> : null}
            {presets ?
                <div className="d-flex align-items-center me-3 mb-2">
                  <label htmlFor={id + "-preset"} className="me-2 small text-muted mb-0">Show first</label>
                  <select id={id + "-preset"} className="form-select form-select-sm w-auto" value={preset ? preset[0] : ""}
                          onChange={(e) => applyPreset(e.target.value)}>
                    {preset ? null : <option value="">Custom order</option>}
                    {presets.map(([key, label]) => <option key={key} value={key}>{label}</option>)}
                  </select>
                </div> : null}
          </div>

          {table()}
        </div>
      </div>
  )

}
