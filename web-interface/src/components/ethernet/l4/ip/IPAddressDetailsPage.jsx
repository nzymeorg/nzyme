import React, {useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import IPAddressesService from "../../../../services/ethernet/IPAddressesService";
import LoadingSpinner from "../../../misc/LoadingSpinner";

const ipAddressesService = new IPAddressesService();

export default function IPAddressDetailsPage() {

  const { addressParam } = useParams()

  // TODO replace with actual IP after we loaded it
  usePageTitle("IP Address Details");

  const [organizationId, tenantId] = useSelectedTenant();

  const [address, setAddress] = useState();

  useEffect(() => {
    setAddress(null);

    ipAddressesService.findAddress(addressParam, organizationId, tenantId, setAddress);
  }, [addressParam, organizationId, tenantId])

  if (!address) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><a href={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</a></li>
                <li className="breadcrumb-item">IP</li>
                <li className="breadcrumb-item">Addresses</li>
                <li className="breadcrumb-item active" aria-current="page">{addressParam} TODO TODO TODO TODO TODO TODO TODO TODO</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <h1>
              IP Address &quot;{addressParam}&quot; TODO TODO TODO TODO TODO TODO
            </h1>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                This page will show all details about the IP address, including an overview of where it connected
                to, using which services and protocols.
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )

}