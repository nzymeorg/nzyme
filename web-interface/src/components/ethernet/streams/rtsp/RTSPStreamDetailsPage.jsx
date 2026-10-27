import React  from "react";
import {Link, useParams} from "react-router-dom";
import usePageTitle from "../../../../util/UsePageTitle";
import ApiRoutes from "../../../../util/ApiRoutes";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import RTSPStreamDetails from "./RTSPStreamDetails";

export default function RTSPStreamDetailsPage() {

  usePageTitle("RTSP Stream Details");

  const { sessionKey } = useParams();


  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-10">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item">Streams</li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX}>RTSP Sessions</Link></li>
              <li className="breadcrumb-item active">{sessionKey}</li>
            </ol>
          </nav>
        </div>
        <div className="col-2">
          <Link to={ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX} className="btn btn-primary float-end">
            Back
          </Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <h1>
            RTSP Stream {<FullCopyShortenedId value={sessionKey} />}
          </h1>
        </div>
      </div>

      <RTSPStreamDetails sessionKey={sessionKey} />
    </React.Fragment>
  )


}