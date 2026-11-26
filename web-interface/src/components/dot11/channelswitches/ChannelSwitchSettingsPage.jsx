import React from "react";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import ConfigurationSectionTable from "../../configuration/ConfigurationSectionTable";
import Dot11Service from "../../../services/Dot11Service";
import usePageTitle from "../../../util/UsePageTitle";
import {CHANNEL_SWITCH_MENU_ITEMS} from "./ChannelSwitchMenuItems";

const dot11Service = new Dot11Service();

export default function ChannelSwitchSettingsPage() {

  usePageTitle("WiFi Channel Switch Settings");

  const [organizationId, tenantId] = useSelectedTenant();

  const configuration = (section) => {
    return <ConfigurationSectionTable organizationId={organizationId}
                                      tenantId={tenantId}
                                      section={section}
                                      getConfiguration={dot11Service.getChannelSwitchAlertConfiguration}
                                      updateConfiguration={dot11Service.updateChannelSwitchAlertConfiguration} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={CHANNEL_SWITCH_MENU_ITEMS} activeRoute={ApiRoutes.DOT11.CHANNEL_SWITCHES.SETTINGS} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="General"
                                       helpLink="https://go.nzyme.org/channel-switches-settings" />

                <p className="text-muted mb-2">
                  Alerts are raised for monitored networks that have their channel switch monitor turned on.
                </p>

                {configuration("general")}
              </div>
            </div>
          </div>

          <div className="col-xl-12 col-xxl-6 mt-3 mt-xxl-0">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Networks That Are Not Monitored"
                                       helpLink="https://go.nzyme.org/channel-switches-settings-other-networks" />

                <p className="text-muted mb-2">
                  These settings apply to access points that are in no monitored network.
                </p>

                {configuration("other_networks")}
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}
