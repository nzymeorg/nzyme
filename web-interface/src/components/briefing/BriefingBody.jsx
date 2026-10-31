import React from "react";
import BriefingVerdict from "./BriefingVerdict";
import BriefingCard from "./BriefingCard";
import AttentionList from "./AttentionList";
import MonitoringSection, {ClusterHealth} from "./MonitoringSection";
import {resolveBriefingLink} from "./briefingLinks";
import ChangesSection from "./ChangesSection";
import ActivitySection from "./ActivitySection";
import LocationsSection from "./LocationsSection";
import {formatDuration, windowPhrase} from "./briefingFormat";

export default function BriefingBody({briefing, type, onRefresh, loading}) {

  const attention = briefing.attention || [];

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-12">
          <div className="card briefing-card briefing-card-verdict">
            <div className="card-body">
              <BriefingVerdict briefing={briefing} type={type} onRefresh={onRefresh} loading={loading} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <BriefingCard title="Needs your attention"
                        count={attention.length > 0 ? attention.length : null}
                        hint={"Open problems as of now, and detection alerts that fired " + windowPhrase(briefing.window) + ". Problems that already resolved themselves are not listed."}>
            <AttentionList items={attention} window={briefing.window} showSubsystem={type === "MAIN"} />
          </BriefingCard>
        </div>
      </div>

      {briefing.monitoring.health_indicators ? (
        <div className="row mt-3">
          <div className="col-12">
            <BriefingCard title="Cluster health"
                          internalLink={resolveBriefingLink({target: "HEALTH"})}>
              <ClusterHealth indicators={briefing.monitoring.health_indicators} />
            </BriefingCard>
          </div>
        </div>
      ) : null}

      <div className="row mt-3">
        <div className="col-12">
          <BriefingCard title="Detection Coverage"
                        className="briefing-card-full">
            <MonitoringSection monitoring={briefing.monitoring} />
          </BriefingCard>
        </div>
      </div>

      <LocationsSection locations={briefing.locations} type={type} window={briefing.window} />

      <ActivitySection activity={briefing.activity} window={briefing.window} type={type} tapsTotal={briefing.monitoring.taps_total} />

      <ChangesSection changes={briefing.changes}
                      window={briefing.window}
                      type={type}
                      hint={"Everything " + windowPhrase(briefing.window) + ", compared to the " + formatDuration(briefing.window.duration_ms) + " before that."} />
    </React.Fragment>
  )

}

