import React, {useEffect, useState} from "react";
import {Link, Navigate, useLocation} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import SpoofingService from "../../../../services/ethernet/SpoofingService";
import SpoofingExclusionForm from "./SpoofingExclusionForm";

const spoofingService = new SpoofingService();

const prefillFromFinding = (details) => {
  const finding = details.finding;

  return {
    name: "Exclude " + finding.address + " claimed by " + finding.spoofer_mac,
    description: "Created from ARP poisoning finding of " + finding.address + " by " + finding.spoofer_mac + ".",
    types: ["ARP"],
    address_cidr: finding.address,
    mac: finding.spoofer_mac
  };
}

const prefillFromDhcpFinding = (details) => {
  const finding = details.finding;

  return {
    name: "Exclude DHCP server " + finding.server_address + " (" + finding.server_mac + ")",
    description: "Created from DHCP spoofing finding of server " + finding.server_address + " with MAC address "
      + finding.server_mac + ".",
    types: ["DHCP"],
    address_cidr: finding.server_address,
    mac: finding.server_mac
  };
}

const prefillFromNameResolutionFinding = (details) => {
  const finding = details.finding;
  const address = finding.responder_addresses.length > 0 ? finding.responder_addresses[0] : null;

  return {
    name: "Exclude name resolution responder " + finding.responder_mac,
    description: "Created from name resolution poisoning finding of " + finding.responder_mac + ".",
    types: ["NAME_RESOLUTION"],
    address_cidr: address,
    mac: finding.responder_mac
  };
}

export default function CreateSpoofingExclusionPage() {

  usePageTitle("Create Spoofing Exclusion");

  const [organizationId, tenantId] = useSelectedTenant();
  const query = new URLSearchParams(useLocation().search);
  const findingUuid = query.get("finding");
  const dhcpFindingUuid = query.get("dhcp_finding");
  const nameresFindingUuid = query.get("nameres_finding");

  const [initialValues, setInitialValues] = useState(findingUuid || dhcpFindingUuid || nameresFindingUuid ? null : {});
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    if (findingUuid) {
      spoofingService.findArpFinding(findingUuid, organizationId, tenantId,
        (details) => setInitialValues(prefillFromFinding(details)),
        () => setInitialValues({}));
      return;
    }

    if (dhcpFindingUuid) {
      spoofingService.findDhcpFinding(dhcpFindingUuid, organizationId, tenantId,
        (details) => setInitialValues(prefillFromDhcpFinding(details)),
        () => setInitialValues({}));
      return;
    }

    if (nameresFindingUuid) {
      spoofingService.findNameResolutionFinding(nameresFindingUuid, organizationId, tenantId,
        (details) => setInitialValues(prefillFromNameResolutionFinding(details)),
        () => setInitialValues({}));
    }
  }, [findingUuid, dhcpFindingUuid, nameresFindingUuid, organizationId, tenantId]);

  const create = (payload, onError) => {
    spoofingService.createExclusion(organizationId, tenantId, payload, () => {
      toast.success("Exclusion created.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.INDEX} />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX}>Spoofing</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.INDEX}>Exclusions</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Create Exclusion</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.INDEX} className="btn btn-primary">Back to Exclusions</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              {initialValues ?
                <SpoofingExclusionForm organizationId={organizationId}
                                       tenantId={tenantId}
                                       initialValues={initialValues}
                                       submitText="Create Exclusion"
                                       onSubmit={create}
                                       isCreate={true} />
                : <LoadingSpinner />}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
