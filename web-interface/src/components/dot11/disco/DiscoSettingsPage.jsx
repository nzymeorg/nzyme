import React from "react";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import {DISCO_MENU_ITEMS} from "./DiscoMenuItems";
import DiscoDetectionConfiguration from "./DiscoDetectionConfiguration";

export default function DiscoSettingsPage() {

  usePageTitle("WiFi Disconnection Settings");

  const [organizationId, tenantId] = useSelectedTenant();

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={DISCO_MENU_ITEMS} activeRoute={ApiRoutes.DOT11.DISCO.SETTINGS} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Disconnection Frames"
                                       helpLink="https://go.nzyme.org/disco-anomalies-settings-frames" />

                <p className="text-muted mb-2">
                  These detections look at the deauthentication and disassociation frames the taps record.
                </p>

                <DiscoDetectionConfiguration organizationId={organizationId} tenantId={tenantId} section="frames" />
              </div>
            </div>

            <div className="card mt-3">
              <div className="card-body">
                <CardTitleWithControls title="General"
                                       helpLink="https://go.nzyme.org/disco-anomalies-settings-general" />

                <p className="text-muted mb-2">
                  Alerts are raised for monitored networks that have their disconnection monitor turned on.
                </p>

                <DiscoDetectionConfiguration organizationId={organizationId} tenantId={tenantId} section="general" />
              </div>
            </div>
          </div>

          <div className="col-xl-12 col-xxl-6 mt-3 mt-xxl-0">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Connection Frames"
                                       helpLink="https://go.nzyme.org/disco-anomalies-settings-connections" />

                <p className="text-muted mb-2">
                  These detections look at the authentication and EAPOL frames of clients that connect.
                </p>

                <DiscoDetectionConfiguration organizationId={organizationId} tenantId={tenantId} section="connections" />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}
