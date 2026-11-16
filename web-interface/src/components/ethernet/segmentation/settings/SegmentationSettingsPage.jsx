import React from "react";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../util/UsePageTitle";
import {SEGMENTATION_MENU_ITEMS} from "../SegmentationMenuItems";
import SegmentationConfiguration from "./SegmentationConfiguration";

export default function SegmentationSettingsPage() {

  usePageTitle("Ethernet Segmentation Settings");

  const [organizationId, tenantId] = useSelectedTenant();

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={SEGMENTATION_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SEGMENTATION.SETTINGS} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Segmentation Settings" />

              <SegmentationConfiguration organizationId={organizationId} tenantId={tenantId} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
