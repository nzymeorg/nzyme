import {Link, useParams} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import NTPTransactionDetails from "./NTPTransactionDetails";
import usePageTitle from "../../../../util/UsePageTitle";

export default function NTPTransactionDetailsPage() {

  usePageTitle("NTP Transaction Details");

  const { transactionId } = useParams();

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item">Time</li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.TIME.NTP.INDEX}>NTP Transactions</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{transactionId}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <h1>
            NTP Transaction &quot;{transactionId}&quot;
          </h1>
        </div>
      </div>

      <NTPTransactionDetails transactionId={transactionId} />

    </React.Fragment>
  )

}