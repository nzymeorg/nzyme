import {Link} from "react-router-dom";
import React from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import UavClassification from "../util/UavClassification";
import UavModelType from "../util/UavModelType";
import UavSerialNumberMatch from "../util/UavSerialNumberMatch";
import ApiRoutes from "../../../util/ApiRoutes";
import UavService from "../../../services/UavService";
import WithPermission from "../../misc/WithPermission";

const uavService = new UavService();

export default function CustomTypesTable(props) {

  const types = props.types;
  const deleteType = props.onDeleteType;

  const page = props.page;
  const perPage = props.perPage;
  const setPage = props.setPage;


  if (types == null) {
    return <LoadingSpinner />
  }

  if (types.count === 0) {
    return <div className="alert alert-info mb-0">No custom UAV types defined.</div>
  }

  return (
    <React.Fragment>

      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Name</th>
          <th>Model</th>
          <th>Type</th>
          <th>Default Classification</th>
          <th>Serial Number</th>

          <WithPermission permission="uav_monitoring_manage">
            <th>&nbsp;</th>
            <th>&nbsp;</th>
          </WithPermission>
        </tr>
        </thead>
        <tbody>
        {types.types.map((t, i) => {
          return (
            <tr key={i}>
              <td>{t.name}</td>
              <td>{t.model ? t.model : <span className="text-muted">n/a</span>}</td>
              <td><UavModelType type={t.type} /></td>
              <td><UavClassification classification={t.default_classification} /></td>
              <td><UavSerialNumberMatch matchType={t.match_type} matchValue={t.match_value} /></td>

              <WithPermission permission="uav_monitoring_manage">
                <td><Link to={ApiRoutes.UAV.TYPES.EDIT(t.uuid, t.organization_id, t.tenant_id)}>Edit</Link></td>
                <td><a href="#" onClick={(e) => deleteType(e, t.uuid)}>Delete</a></td>
              </WithPermission>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={types.count} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}