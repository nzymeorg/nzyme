import React, {useEffect, useState} from "react";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetsService from "../../../../services/ethernet/AssetsService";
import AssetIdentityConfigurationTable from "./AssetIdentityConfigurationTable";

const assetsService = new AssetsService();

const DESCRIPTIONS = {
  duplicate: (
    <React.Fragment>
      <p className="text-muted mb-2">
        Runs every minute over the TCP sessions of all taps of the tenant. Every session starts with a SYN whose
        fixed properties fingerprint the sending stack. A finding is raised when two or more different stacks each
        opened at least the minimum number of sessions with the same MAC address inside the detection window.
        Only stacks whose SYN arrived with the initial TTL intact count. SYNs that passed a router before the tap
        saw them do not, which keeps NAT boxes, phone hotspots and VM hosts out.
      </p>
      <p className="text-muted">
        By default, stacks that differ only in window scale or MSS are treated as one, because applications on a
        host can change those. Wireless client bridges that masquerade their clients behind one address are the
        common legitimate cause. Add an exclusion for them.
      </p>
    </React.Fragment>
  ),
  change: (
    <React.Fragment>
      <p className="text-muted mb-2">
        Runs every minute over the identity attributes of assets whose recorded identity is at least the minimum
        age old. An asset that Nzyme only started profiling recently has no known identity to change yet, however
        long it has been on the network. Every attribute
        that appeared inside the detection window while the old value stopped being seen is scored: a replaced TCP
        stack is 50, a replaced hostname 20, a replaced DHCP client 15, and so on. A finding is raised at the score
        threshold. The default threshold means a replaced TCP stack alone is enough.
      </p>
      <p className="text-muted">
        Reinstalled machines, dual-boot systems and network cards moved to another machine look like identity
        changes too. Raise the threshold or add an exclusion for devices that change often.
      </p>
    </React.Fragment>
  ),
  successor: (
    <React.Fragment>
      <p className="text-muted mb-2">
        Runs every minute. Every asset that appeared inside the lookback is evaluated once it has existed for the
        quiet period, and again at 6 hours, 24 hours and the end of the lookback as it collects more identity. Each
        evaluation compares it to assets that went silent around the time it appeared and stayed silent for the
        quiet period. Shared hostnames,
        addresses, DHCP identity, TCP stacks, TLS clients and inbound ports add to the confidence, with rare values
        counting more than ones a whole fleet shares. The best match above the minimum confidence is linked.
      </p>
      <p className="text-muted">
        Links are notices on both asset pages, not alerts, unless alerting is switched on. Confirm or dismiss them
        on the Identity page. Dismissed links are not detected again.
      </p>
    </React.Fragment>
  ),
  retention: (
    <p className="text-muted">
      Findings and links that were not seen for longer than the retention time are deleted. Confirmed links are
      kept, because they describe the network rather than an event.
    </p>
  )
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
              <CardTitleWithControls title={section.title} />

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
