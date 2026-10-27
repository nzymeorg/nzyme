import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import WithPermission from "../../misc/WithPermission";
import ConnectTypesTable from "./ConnectTypesTable";
import ApiRoutes from "../../../util/ApiRoutes";
import CustomTypesTable from "./CustomTypesTable";
import UavService from "../../../services/UavService";
import {toast} from "react-toastify";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";

const uavService = new UavService();

export default function UavTypesPage() {

  usePageTitle("UAV Types");

  const [organizationId, tenantId] = useSelectedTenant();

  const [connectTypes, setConnectTypes] = useState(null);
  const [connectTypesPage, setConnectTypesPage] = useState(1);
  const connectTypesPerPage = 25;

  const [customTypes, setCustomTypes] = useState(null);
  const [customTypesPage, setCustomTypesPage] = useState(1);
  const customTypesPerPage = 25;

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    setCustomTypes(null);

    uavService.findAllCustomTypes(
      setCustomTypes, organizationId, tenantId, customTypesPerPage, (customTypesPage-1)*customTypesPerPage
    );
  }, [organizationId, tenantId, customTypesPage, revision]);

  useEffect(() => {
    setConnectTypes(null);

    uavService.findAllConnectTypes(
        setConnectTypes, organizationId, tenantId, connectTypesPerPage, (connectTypesPage-1)*connectTypesPerPage
    );
  }, [connectTypesPage, revision, organizationId, tenantId]);

  const onDeleteCustomType = (e, uuid) => {
    e.preventDefault();

    if (!confirm("Really delete custom type?")) {
      return;
    }

    uavService.deleteCustomType(uuid, organizationId, tenantId, () => {
      toast.success("Custom UAV type deleted.");
      setRevision(new Date());
    })
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <h1>Unmanned Aerial Vehicle (UAV) Types</h1>
        </div>

        <div className="col-md-2 text-end">
          <a href="https://go.nzyme.org/uav-types" className="btn btn-secondary">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Custom Types" />

              <p className="text-muted mt-0">
                You can use custom types to associate UAVs with additional details, such as name, make, or model. These
                types extend existing built-in types, allowing for more precise identification. A common use case is
                assigning names or extra information to frequently detected UAVs, such as those in your own fleet or
                drones that regularly appear in your area.
              </p>

              <CustomTypesTable types={customTypes}
                                page={customTypesPage}
                                onDeleteType={onDeleteCustomType}
                                setPage={setCustomTypesPage}
                                perPage={customTypesPerPage} />

              <WithPermission permission="uav_monitoring_manage">
                <Link to={ApiRoutes.UAV.TYPES.CREATE} className="btn btn-sm btn-secondary mt-3">
                  Create Custom Type
                </Link>
              </WithPermission>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Built-In Types" />

              <p className="text-muted mt-0">
                Built-in types, provided through nzyme Connect, cover a wide range of known UAV models.
              </p>

              <ConnectTypesTable types={connectTypes}
                                 page={connectTypesPage}
                                 setPage={setConnectTypesPage}
                                 perPage={connectTypesPerPage} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}