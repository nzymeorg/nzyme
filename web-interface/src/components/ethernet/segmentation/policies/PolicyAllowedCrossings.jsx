import React, {useEffect, useState} from "react";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import Filters from "../../../shared/filtering/Filters";
import useSegmentationClientTable from "../useSegmentationClientTable";
import {
  emptyRule,
  EVERYTHING_IN_SCOPE_NOTE,
  everythingInScope,
  isEverythingInScope,
  namesNothing
} from "./PolicyDefinition";
import PolicyRuleText from "./PolicyRuleText";
import PolicyRuleEditor from "./PolicyRuleEditor";
import {
  allowedCrossingRows,
  POLICY_ALLOWED_CROSSINGS_FILTER_FIELDS,
  POLICY_ALLOWED_CROSSINGS_SORT_COLUMNS,
  POLICY_ALLOWED_CROSSINGS_WITH_USE_FILTER_FIELDS
} from "./PolicyAllowedCrossingsFilterFields";

/*
 * The crossings are filtered, sorted and paged here, for looking at them only: a crossing is always edited and
 * removed by its position in the definition, and the definition keeps its order.
 */
export default function PolicyAllowedCrossings({allowed, onChange = undefined, options, organizationId, tenantId,
                                                 unused = undefined, onUnfinished = undefined}) {

  const [draft, setDraft] = useState(null);
  const [added, setAdded] = useState(null);

  const [editing, setEditing] = useState(null);
  const [editingRow, setEditingRow] = useState(null);

  const editable = !!onChange;
  const fields = unused === undefined
    ? POLICY_ALLOWED_CROSSINGS_FILTER_FIELDS : POLICY_ALLOWED_CROSSINGS_WITH_USE_FILTER_FIELDS;

  // A crossing that is edited stays where it is in the list, whatever is changed in it, until editing is done.
  const rows = allowedCrossingRows(allowed, options, unused).map((row) => {
    return row.index === editing && editingRow ? {...editingRow, index: row.index, rule: row.rule} : row;
  });

  const table = useSegmentationClientTable(
    rows, fields, POLICY_ALLOWED_CROSSINGS_SORT_COLUMNS, "index", "ASC", (row) => row.index === editing
  );

  useEffect(() => {
    if (editingRow) {
      table.showRow((row) => row.index === editing);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editingRow]);

  const edit = (index, list) => {
    setEditing(index);
    setEditingRow(index === null ? null : allowedCrossingRows(list, options, unused)[index]);
  }

  const toggle = (e, index) => {
    e.preventDefault();
    edit(editing === index ? null : index, allowed);
  }

  useEffect(() => {
    if (added !== null) {
      table.showRow((row) => row.index === added);
      setAdded(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [added]);

  const unfinished = draft !== null && !namesNothing(draft);

  const everything = allowed.some(isEverythingInScope);

  useEffect(() => {
    if (onUnfinished) {
      onUnfinished(unfinished);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [unfinished]);

  const add = (e) => {
    e.preventDefault();

    edit(null, allowed);
    setDraft(emptyRule());
  }

  const addDraft = (e) => {
    e.preventDefault();

    if (namesNothing(draft)) {
      return;
    }

    onChange([...allowed, draft]);
    setAdded(allowed.length);
    setDraft(null);
  }

  // Other allowed crossings are kept. They count again once this one is removed.
  const allowEverything = (e) => {
    e.preventDefault();

    edit(null, allowed);
    onChange([...allowed, everythingInScope()]);
    setAdded(allowed.length);
  }

  const cancelDraft = (e) => {
    e.preventDefault();
    setDraft(null);
  }

  const remove = (e, index) => {
    e.preventDefault();

    if (editing === index) {
      edit(null, allowed);
    } else if (editing !== null && editing > index) {
      setEditing(editing - 1);
    }

    onChange(allowed.filter((rule, i) => i !== index));
  }

  const update = (index, rule) => {
    onChange(allowed.map((r, i) => i === index ? rule : r));
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={table.orderColumn}
                          setOrderColumn={table.setOrderColumn}
                          orderDirection={table.orderDirection}
                          setOrderDirection={table.setOrderDirection} />
  }

  const hints = (rule, index) => {
    return (
      <React.Fragment>
        {namesNothing(rule) ?
          <div className="small text-danger">Names no source, destination or service.</div> : null}
        {unused && unused.includes(index) ?
          <div className="small text-muted"
               title="No recorded traffic in the selected time range matched this allowed crossing.">
            Not used in the selected time range
          </div> : null}
      </React.Fragment>
    )
  }

  const crossings = () => {
    if (table.total === 0) {
      return <div className="alert alert-info mb-0">No allowed crossings match the current filters.</div>
    }

    const paged = table.total > table.perPage;

    return (
      <React.Fragment>
        <table className={"table table-sm table-hover table-striped" + (paged ? "" : " mb-0")}>
          <thead>
          <tr>
            <th style={{width: 60}} className="text-nowrap" title="The position in the definition">
              # {columnSorting("index")}
            </th>
            <th>
              Source {columnSorting("source")} <span className="text-muted fw-normal ms-1">to</span>{' '}
              Destination {columnSorting("destination")} <span className="text-muted fw-normal ms-1">on</span>{' '}
              Service {columnSorting("service")}
            </th>
            {editable ? <th>&nbsp;</th> : null}
          </tr>
          </thead>
          <tbody>
          {table.rows.map((row) => {
            const i = row.index;
            const rule = row.rule;

            return (
              <React.Fragment key={i}>
                <tr>
                  <td className="text-muted">{i + 1}</td>
                  <td>
                    <PolicyRuleText rule={rule} options={options} />
                    {hints(rule, i)}
                  </td>
                  {editable ?
                    <td className="text-end text-nowrap">
                      {isEverythingInScope(rule) ? null :
                        <a href="#" onClick={(e) => toggle(e, i)}>{editing === i ? "Done" : "Edit"}</a>}{' '}
                      <a href="#" className="ms-2" onClick={(e) => remove(e, i)}>Remove</a>
                    </td> : null}
                </tr>
                {editable && editing === i ?
                  <tr>
                    <td>&nbsp;</td>
                    <td colSpan={2}>
                      <PolicyRuleEditor rule={rule}
                                        onChange={(changed) => update(i, changed)}
                                        options={options}
                                        organizationId={organizationId}
                                        tenantId={tenantId}
                                        idPrefix={"policy-allowed-" + i} />
                    </td>
                  </tr> : null}
              </React.Fragment>
            )
          })}
          </tbody>
        </table>

        {paged ?
          <Paginator itemCount={table.total} perPage={table.perPage} setPage={table.setPage} page={table.page} />
          : null}
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      {allowed.length === 0 ?
        <div className="alert alert-info mb-0">
          Nothing is allowed. Every attempted or successful connection in scope will be reported as a finding.
        </div>
        : <React.Fragment>
          {everything ? <div className="alert alert-info">{EVERYTHING_IN_SCOPE_NOTE}</div> : null}

          <div className="mb-4">
            <Filters filters={table.filters}
                     setFilters={table.setFilters}
                     fields={fields}
                     embedded={true}
                     onMonitorsReady={() => {}} />
          </div>

          {crossings()}
        </React.Fragment>}

      {editable && draft ?
        <div className="mt-4">
          <h6 className="mb-3">New Allowed Crossing</h6>

          <PolicyRuleEditor rule={draft}
                            onChange={setDraft}
                            options={options}
                            organizationId={organizationId}
                            tenantId={tenantId}
                            idPrefix="policy-allowed-new" />

          <div className="alert alert-info">
            <strong>Allows:</strong>{' '}
            {namesNothing(draft) ? "Nothing yet. Add at least one source, destination or service."
              : <PolicyRuleText rule={draft} options={options} />}
          </div>

          <button className="btn btn-sm btn-primary" type="button" disabled={namesNothing(draft)}
                  onClick={addDraft}>
            Add Allowed Crossing
          </button>{' '}
          <button className="btn btn-sm btn-outline-secondary" type="button" onClick={cancelDraft}>
            Cancel
          </button>
        </div> : null}

      {editable && !draft ?
        <p className="mt-3 mb-0">
          <button className="btn btn-sm btn-outline-secondary" type="button" onClick={add}>
            Add Allowed Crossing
          </button>{' '}
          {everything ? null :
            <button className="btn btn-sm btn-outline-secondary" type="button" onClick={allowEverything}
                    title="Allows every connection in scope. The policy then reports no findings.">
              Allow Everything in Scope
            </button>}
        </p> : null}
    </React.Fragment>
  )

}
