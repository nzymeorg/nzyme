import React, {useEffect, useState} from "react";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetsService from "../../../../services/ethernet/AssetsService";
import AssetIdentityConfigurationTable from "./AssetIdentityConfigurationTable";

const assetsService = new AssetsService();

const DESCRIPTIONS = {
  duplicate: (
    <p className="text-muted">
      Finds MAC addresses that two or more TCP stacks use at the same time.
    </p>
  ),
  change: (
    <p className="text-muted">
      Scores identity attributes that were replaced under the same MAC address and raises a finding at the
      threshold.
    </p>
  ),
  successor: (
    <p className="text-muted">
      Links a new asset to an asset that went silent when it appeared. Links are notices on the asset pages, not
      alerts, unless alerting is switched on.
    </p>
  ),
  retention: (
    <p className="text-muted">
      Findings and links not seen for longer than the retention time are deleted. Confirmed links are kept.
    </p>
  )
};

const HELP_LINKS = {
  duplicate: "https://go.nzyme.org/ethernet-assets-identity-duplicates",
  change: "https://go.nzyme.org/ethernet-assets-identity-changes",
  successor: "https://go.nzyme.org/ethernet-assets-identity-mac-changes",
  retention: "https://go.nzyme.org/ethernet-assets-identity-config"
};

export default function AssetIdentitySettings({organizationId, tenantId}) {

  const [config, setConfig] = useState(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    setConfig(null);
    assetsService.getAssetIdentityConfiguration(organizationId, tenantId, setConfig);
  }, [organizationId, tenantId, revision]);

  if (!config) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      {config.sections.map((section) => {
        return (
          <div className="card mb-3" key={section.key}>
            <div className="card-body">
              <CardTitleWithControls title={section.title} helpLink={HELP_LINKS[section.key]} />

              {DESCRIPTIONS[section.key]}

              {section.detection.length > 0 ?
                <React.Fragment>
                  {section.alerting.length > 0 ? <h4 className="mt-3">Detection</h4> : null}
                  <AssetIdentityConfigurationTable organizationId={organizationId}
                                                   tenantId={tenantId}
                                                   entries={section.detection}
                                                   setConfig={setConfig}
                                                   setRevision={setRevision} />
                </React.Fragment> : null}

              {section.alerting.length > 0 ?
                <React.Fragment>
                  <h4 className="mt-4">Alerting</h4>
                  <p className="text-muted mb-0">
                    An alert is raised once per finding when it is first detected, not again while it continues.
                  </p>
                  <AssetIdentityConfigurationTable organizationId={organizationId}
                                                   tenantId={tenantId}
                                                   entries={section.alerting}
                                                   setConfig={setConfig}
                                                   setRevision={setRevision} />
                </React.Fragment> : null}
            </div>
          </div>
        )
      })}
    </React.Fragment>
  )

}
