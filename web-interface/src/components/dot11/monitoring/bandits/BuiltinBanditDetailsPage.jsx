import React, {useEffect, useState} from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import {Link, useParams} from "react-router-dom";
import Dot11Service from "../../../../services/Dot11Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import BanditFingerprints from "./BanditFingerprints";
import usePageTitle from "../../../../util/UsePageTitle";

const dot11Service = new Dot11Service();

function BuiltinBanditDetailsPage() {

  const {id} = useParams();

  const [bandit, setBandit] = useState();

  usePageTitle(bandit ? `Bandit: ${bandit.name}` : "Bandit Details");

  useEffect(() => {
    dot11Service.findBuiltinBandit(id, setBandit);
  }, [id]);

  if (!bandit) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-7">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.OVERVIEW}>WiFi</Link></li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.MONITORING.INDEX}>Monitoring</Link></li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.MONITORING.BANDITS.INDEX}>Bandits</Link></li>
                <li className="breadcrumb-item">Built-In</li>
                <li className="breadcrumb-item active" aria-current="page">{bandit.name}</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-5">
            <span className="float-end">
              <Link className="btn btn-primary" to={ApiRoutes.DOT11.MONITORING.BANDITS.INDEX}>Back</Link>
            </span>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <h1>
              Built-In Bandit &quot;{bandit.name}&quot;
            </h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Description</h3>

                <p className="mb-0">
                  {bandit.description}
                </p>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Fingerprints</h3>

                <p>
                  You can learn more about fingerprints in
                  the <a href="https://go.nzyme.org/wifi-fingerprinting">documentation</a>
                </p>

                <BanditFingerprints fingerprints={bandit.fingerprints} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default BuiltinBanditDetailsPage;