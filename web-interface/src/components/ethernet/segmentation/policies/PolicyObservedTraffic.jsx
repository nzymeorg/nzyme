import React, {useEffect, useMemo, useRef, useState} from "react";
import numeral from "numeral";
import moment from "moment";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import InlineHelp from "../../../misc/InlineHelp";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import Filters from "../../../shared/filtering/Filters";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import {Presets} from "../../../shared/timerange/TimeRange";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import PivotLink from "../../../shared/PivotLink";
import {apiErrorMessage} from "../../../../util/Tools";
import {FINDING_CLASSES, FOLDED_PORT, OUTCOMES, VERDICTS, flowServiceLabel} from "../SegmentationFormat";
import {previewRowSessionsLink} from "../SegmentationPivots";
import useSegmentationClientTable from "../useSegmentationClientTable";
import {allowedCrossingOfPreviewRow, scopeIsValid} from "./PolicyDefinition";
import {
  POLICY_OBSERVED_TRAFFIC_FILTER_FIELDS,
  POLICY_OBSERVED_TRAFFIC_SORT_COLUMNS
} from "./PolicyObservedTrafficFilterFields";

const segmentationService = new SegmentationService();

const VERDICT_CLASSES = {ALLOWED: "text-success", VIOLATION: "text-danger", INDETERMINATE: "text-muted"};

const sessions = (count) => {
  return count > 0 ? numeral(count).format("0,0") : <span className="text-muted">0</span>;
}

const hosts = (count, sample, broadcast = false) => {
  if (count === 0) {
    return null;
  }

  const more = count - sample.length;
  const what = broadcast ? (count === 1 ? "address" : "addresses") : (count === 1 ? "host" : "hosts");

  return (
    <div className="small text-muted">
      {numeral(count).format("0,0")} {what}:{' '}
      <span className="machine-data">{sample.join(", ")}</span>
      {more > 0 ? " and " + numeral(more).format("0,0") + " more" : null}
    </div>
  )
}

export default function PolicyObservedTraffic({organizationId, tenantId, scope, allowed, description = null,
                                                onAllow = undefined, onResult = undefined}) {

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_DAYS_7);
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(false);
  const [revision, setRevision] = useState(new Date());

  const rows = useMemo(() => {
    return preview ? preview.rows.map((row, position) => ({...row, position: position})) : [];
  }, [preview]);

  const table = useSegmentationClientTable(
    rows, POLICY_OBSERVED_TRAFFIC_FILTER_FIELDS, POLICY_OBSERVED_TRAFFIC_SORT_COLUMNS, "last_seen", "DESC"
  );

  const latestRequest = useRef(0);

  const valid = scopeIsValid(scope);
  const definitionKey = JSON.stringify({scope: scope, allowed: allowed});

  useEffect(() => {
    latestRequest.current += 1;
    const request = latestRequest.current;

    if (onResult) {
      onResult(null);
    }

    if (!valid) {
      setPreview(null);
      setError(null);
      setPending(false);
      return;
    }

    setPending(true);
    const timer = setTimeout(() => {
      segmentationService.previewPolicy(organizationId, tenantId, timeRange, scope, allowed, (result) => {
        if (request !== latestRequest.current) {
          return;
        }

        setPreview(result);
        setError(null);
        setPending(false);

        if (onResult) {
          onResult(result);
        }
      }, (e) => {
        if (request !== latestRequest.current) {
          return;
        }

        setPreview(null);
        setPending(false);
        setError(apiErrorMessage(e, "Could not evaluate the definition against recorded traffic."));
      });
    }, 400);

    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [definitionKey, timeRange, organizationId, tenantId, revision]);

  const notAllowed = rows.filter((row) => row.verdict === "VIOLATION");
  const notAllowedNotShown = notAllowed.filter((row) => !table.rows.includes(row)).length;

  const allow = (e, row) => {
    e.preventDefault();
    onAllow([allowedCrossingOfPreviewRow(row)]);
  }

  const allowAll = (e) => {
    e.preventDefault();

    if (!confirm("Add " + notAllowed.length + " allowed " + (notAllowed.length === 1 ? "crossing" : "crossings") +
        ", one for each row that is not allowed?" +
        (notAllowedNotShown > 0 ? " " + notAllowedNotShown + " of them " + (notAllowedNotShown === 1 ? "is" : "are") +
          " not on the shown page." : ""))) {
      return;
    }

    onAllow(notAllowed.map(allowedCrossingOfPreviewRow));
  }

  const fact = (label, count, help, className = "", last = false) => (
    <div className={"py-1 " + (last ? "" : "pe-4 me-4 border-end")}>
      <div className="small text-muted text-uppercase">{label} <InlineHelp text={help} /></div>
      <div>
        <span className={"fs-4 " + (count > 0 ? className : "")}>{numeral(count).format("0,0")}</span>{' '}
        <span className="text-muted">{count === 1 ? "session" : "sessions"}</span>
      </div>
    </div>
  )

  const totals = () => {
    return (
      <div className="d-flex flex-wrap align-items-center mb-3">
        {fact("Allowed", preview.allowed_sessions, VERDICTS.ALLOWED.description)}
        {fact("Crossed", preview.crossed_sessions, FINDING_CLASSES.CROSSED.description, "text-danger")}
        {fact("Attempted", preview.attempted_sessions, FINDING_CLASSES.ATTEMPTED.description, "text-warning")}
        {fact("Cannot Be Judged", preview.indeterminate_sessions, VERDICTS.INDETERMINATE.description, "", true)}
      </div>
    )
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={table.orderColumn}
                          setOrderColumn={table.setOrderColumn}
                          orderDirection={table.orderDirection}
                          setOrderDirection={table.setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={table.setFilters}
                            filters={table.filters}
                            fields={POLICY_OBSERVED_TRAFFIC_FILTER_FIELDS}
                            field={field}
                            value={value} />
  }

  const traffic = () => {
    if (rows.length === 0) {
      return (
        <div className="alert alert-info mb-0">
          No traffic recorded in this scope in the selected time range.
        </div>
      )
    }

    const withSent = rows.some((row) => row.sent > 0);
    const paged = table.total > table.perPage;

    return (
      <React.Fragment>
        {preview.truncated ?
          <div className="alert alert-warning">
            Limited to the {numeral(preview.row_limit).format("0,0")} busiest rows of the{' '}
            {numeral(preview.flow_limit).format("0,0")} busiest flows in scope.
          </div> : null}

        {onAllow && notAllowed.length > 0 ?
          <p>
            <button className="btn btn-sm btn-outline-secondary" type="button" onClick={allowAll}>
              Allow All Observed
            </button>{' '}
            <span className="text-muted ms-2">
              Adds an allowed crossing for each of the {numeral(notAllowed.length).format("0,0")}{' '}
              {notAllowed.length === 1 ? "row that is" : "rows that are"} not allowed, on every page.
            </span>
          </p> : null}

        <div className="mb-4">
          <Filters filters={table.filters}
                   setFilters={table.setFilters}
                   fields={POLICY_OBSERVED_TRAFFIC_FILTER_FIELDS}
                   embedded={true}
                   onMonitorsReady={() => {}} />
        </div>

        {table.total === 0 ?
          <div className="alert alert-info mb-0">No recorded traffic matches the current filters.</div>
          : <table className={"table table-sm table-hover table-striped" + (paged ? "" : " mb-0")}>
            <thead>
            <tr>
              <th>Source {columnSorting("source")}</th>
              <th>Destination {columnSorting("destination")}</th>
              <th>Service {columnSorting("port")}</th>
              <th className="text-nowrap" title={OUTCOMES.CONNECTED.description}>
                Connected {columnSorting("connected")}
              </th>
              <th className="text-nowrap" title={OUTCOMES.REJECTED.description}>
                Rejected {columnSorting("rejected")}
              </th>
              <th className="text-nowrap" title={OUTCOMES.NO_REPLY.description}>
                No Reply {columnSorting("no_reply")}
              </th>
              {withSent ?
                <th className="text-nowrap" title={OUTCOMES.SENT.description}>Sent {columnSorting("sent")}</th> : null}
              <th className="text-nowrap">Last Seen {columnSorting("last_seen")}</th>
              <th>Verdict {columnSorting("verdict")}</th>
              <th>&nbsp;</th>
              {onAllow ? <th>&nbsp;</th> : null}
            </tr>
            </thead>
            <tbody>
            {table.rows.map((row) => {
              const verdict = VERDICTS[row.verdict];
              const sessionsLink = previewRowSessionsLink(row, timeRange);

              return (
                <tr key={row.position}>
                  <td>
                    {row.source.label} {filterIcon("source", row.source.label)}
                    {hosts(row.source_hosts, row.source_sample)}
                  </td>
                  <td>
                    {row.destination.label} {filterIcon("destination", row.destination.label)}
                    {hosts(row.destination_hosts, row.destination_sample, row.destination.selector.type === "BROADCAST")}
                  </td>
                  <td className="text-nowrap">
                    {flowServiceLabel(row)} {filterIcon("port", row.port === FOLDED_PORT ? null : row.port)}
                  </td>
                  <td>{sessions(row.connected)}</td>
                  <td>{sessions(row.rejected)}</td>
                  <td>{sessions(row.no_reply)}</td>
                  {withSent ? <td>{sessions(row.sent)}</td> : null}
                  <td title={moment(row.last_seen).format()}>{moment(row.last_seen).fromNow()}</td>
                  <td className="text-nowrap">
                    {verdict ?
                      <span className={VERDICT_CLASSES[row.verdict]} title={verdict.description}>
                        {row.verdict === "VIOLATION" ? <strong>{verdict.title}</strong> : verdict.title}
                      </span>
                      : row.verdict}{' '}
                    {filterIcon("verdict", row.verdict)}
                  </td>
                  <td className="text-end">
                    {sessionsLink ? <PivotLink to={sessionsLink} title="Sessions of this row" icon={true} /> : null}
                  </td>
                  {onAllow ?
                    <td className="text-end">
                      {row.verdict === "VIOLATION" ?
                        <a href="#" title="Adds an allowed crossing for this source, destination and service"
                           onClick={(e) => allow(e, row)}>
                          Allow
                        </a> : null}
                    </td> : null}
                </tr>
              )
            })}
            </tbody>
          </table>}

        {paged ?
          <Paginator itemCount={table.total} perPage={table.perPage} setPage={table.setPage} page={table.page} />
          : null}
      </React.Fragment>
    )
  }

  const body = () => {
    if (!valid) {
      return (
        <div className="alert alert-info mb-0">
          Name a source or a destination in the scope to see what was recorded in it.
        </div>
      )
    }

    if (error) {
      return <div className="alert alert-warning mb-0">{error}</div>
    }

    if (!preview) {
      return (
        <div className="alert alert-info mb-0">
          <i className="fa-solid fa-circle-notch fa-spin"></i> Evaluating recorded traffic ...
        </div>
      )
    }

    return (
      <div style={pending ? {opacity: 0.5} : {}}>
        {totals()}
        {traffic()}
      </div>
    )
  }

  return (
    <React.Fragment>
      <CardTitleWithControls title="Observed Traffic"
                             timeRange={timeRange}
                             setTimeRange={setTimeRange}
                             doNotPersistTimeRange={true}
                             refreshAction={() => setRevision(new Date())} />

      {description ? <p className="text-muted">{description}</p> : null}

      {body()}
    </React.Fragment>
  )

}
