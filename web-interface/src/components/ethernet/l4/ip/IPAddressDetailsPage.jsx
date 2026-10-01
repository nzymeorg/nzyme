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

  const [organizationId, tenantId] = useSelectedTenant();

  const [pageTitle, setPageTile] = useState("IP Details");
  const [address, setAddress] = useState();

  usePageTitle(pageTitle);

  useEffect(() => {
    setAddress(null);

    ipAddressesService.findAddress(addressParam, organizationId, tenantId, setAddress);
  }, [addressParam, organizationId, tenantId])

  useEffect(() => {
    if (address) {
      setPageTile("IP " + address.address + " Details");
    }
  }, [address])

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
                <li className="breadcrumb-item active" aria-current="page">{address.address}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <h1>
              IP Address <span className="machine-data">{address.address}</span>
            </h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )

}