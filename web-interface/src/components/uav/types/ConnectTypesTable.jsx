import React from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import UavModelType from "../util/UavModelType";
import UavClassification from "../util/UavClassification";
import UavSerialNumberMatch from "../util/UavSerialNumberMatch";
import ApiRoutes from "../../../util/ApiRoutes";

export default function ConnectTypesTable(props) {

  const types = props.types;

  const page = props.page;
  const perPage = props.perPage;
  const setPage = props.setPage;

  if (types == null) {
    return <LoadingSpinner />
  }

  if (types.count === 0) {
    return <div className="alert alert-warning mb-0">
      No built-in types found. Set up <a href="https://connect.nzyme.org/">Nzyme Connect</a> if you want to use this
      feature.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Model</th>
          <th>Type</th>
          <th>Serial</th>
        </tr>
        </thead>
        <tbody>
        {types.types.map((t, i) => {
          return (
              <tr key={i}>
                <td>{t.model}</td>
                <td><UavModelType type={t.type} /></td>
                <td><span className="serial-number-match">{t.serial}</span></td>
              </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={types.count} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}