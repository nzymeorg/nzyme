import React, {useEffect, useRef, useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "react-toastify";
import DetectionAlertsService from "../../../services/DetectionAlertsService";
import AlertActionDefinitions from "./AlertActionDefinitions";
import VizMenu from "../../shared/viz/VizMenu";
import CardTitleWithControls from "../../shared/CardTitleWithControls";

const alertsService = new DetectionAlertsService();

const BUTTON = "btn btn-sm btn-outline-secondary";

export default function AlertActions({alert, onActionCompleted = undefined}) {

  const [actions, setActions] = useState(null);
  const [revision, setRevision] = useState(0);
  const [running, setRunning] = useState(null);
  const isRunning = useRef(false);

  useEffect(() => {
    let active = true;

    alertsService.findAlertActions(alert.id, (found) => {
      if (active) {
        setActions(found);
        setRunning(null);
        isRunning.current = false;
      }
    }, (error) => {
      // Without the current state, nothing can be offered safely. A node from before this endpoint answers 404.
      if (active) {
        if (error.response.status !== 404) {
          toast.error("Could not load alert actions. (HTTP " + error.response.status + ")");
        }
        setActions(null);
        setRunning(null);
        isRunning.current = false;
      }
    });

    return () => {
      active = false;
    }
  }, [alert.id, revision]);

  const reload = () => setRevision((previous) => previous + 1);

  const completed = () => {
    reload();
    if (onActionCompleted) {
      onActionCompleted();
    }
  }

  const execute = (action, definition, call) => {
    // The ref also stops a second click that arrives before the state has rendered.
    if (isRunning.current) {
      return;
    }

    isRunning.current = true;
    setRunning(action.type);

    // Released here and not by the reload, which may never get an answer.
    const finished = () => {
      isRunning.current = false;
      setRunning(null);
    }

    call((result) => {
      finished();

      // Someone else may have decided in the meantime. The reloaded actions show the state.
      if (definition.changedNothing && definition.changedNothing(result)) {
        toast.info("Nothing was changed.");
        reload();
        return;
      }

      toast.success(definition.success);
      completed();
    }, (error) => {
      finished();
      // Someone else may have made the same change. The reloaded actions show if that is the case.
      toast.error(error.response
        ? "Action failed. (HTTP " + error.response.status + ")" : "Action failed. No response. Is Nzyme running?");
      reload();
    });
  }

  const run = (action, definition) => {
    if (isRunning.current || !confirm(definition.confirmation(action.target))) {
      return;
    }

    execute(action, definition,
      (onSuccess, onError) => definition.run(action.target, alert, onSuccess, onError));
  }

  const choose = (action, definition, value) => {
    execute(action, definition,
      (onSuccess, onError) => definition.run(action.target, alert, value, onSuccess, onError));
  }

  const title = (action, definition) => {
    return running === action.type
      ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> &nbsp;{definition.title}</span>
      : definition.title;
  }

  const control = (action, definition) => {
    if (action.done) {
      return <button type="button" className={BUTTON} disabled={true}><i className="fa-solid fa-check"></i> Done</button>
    }

    if (definition.kind === "link") {
      return <Link className={BUTTON} to={definition.to(action.target)}>{definition.title}</Link>
    }

    if (definition.kind === "choice") {
      return <VizMenu label={title(action, definition)}
                      disabled={running !== null}
                      items={definition.choices.map((choice) => ({
                        label: choice.title,
                        disabled: choice.value === definition.current(action.target),
                        onClick: () => choose(action, definition, choice.value)
                      }))} />
    }

    return (
      <button type="button" className={BUTTON} disabled={running !== null} onClick={() => run(action, definition)}>
        {title(action, definition)}
      </button>
    )
  }

  if (!actions) {
    return null;
  }

  const rows = actions
    .map((action) => ({action: action, definition: AlertActionDefinitions[action.type]}))
    .filter((row) => row.definition);

  if (rows.length === 0) {
    return null;
  }

  return (
    <div className="row mt-3">
      <div className="col-md-12">
        <div className="card">
          <div className="card-body">
            <CardTitleWithControls title="Quick Actions" helpLink="https://go.nzyme.org/detection-alerts-quick-actions" />

            <table className="table table-sm table-hover table-striped mb-0">
              <tbody>
              {rows.map((row) => (
                <tr key={row.action.type}>
                  <td className="align-middle">
                    {row.action.done && row.definition.doneDescription
                      ? row.definition.doneDescription(row.action.target)
                      : row.definition.description(row.action.target)}
                  </td>
                  <td className="align-middle text-end text-nowrap">{control(row.action, row.definition)}</td>
                </tr>
              ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  )

}
