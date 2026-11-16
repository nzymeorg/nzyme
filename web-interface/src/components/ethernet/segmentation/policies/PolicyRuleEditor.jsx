import React from "react";
import PolicySelectorsEditor from "./PolicySelectorsEditor";
import PolicyServicesEditor from "./PolicyServicesEditor";

/*
 * Each of the three editors renders four parts and nothing else: a label, the inputs, a help text and the list
 * of what was added. The boxes of the sides share their rows, and for that the count has to stay at four.
 */
export default function PolicyRuleEditor({rule, onChange, options, organizationId, tenantId, idPrefix, scope = false}) {

  return (
    <div className="policy-rule-container">
      <div className="policy-rule">
        <div className="policy-rule-side">
          <PolicySelectorsEditor title="Sources"
                                 anyLabel="Any source"
                                 selectors={rule.sources}
                                 onChange={(sources) => onChange({...rule, sources: sources})}
                                 options={options}
                                 organizationId={organizationId}
                                 tenantId={tenantId}
                                 idPrefix={idPrefix + "-sources"} />
        </div>

        <div className="policy-rule-side">
          <PolicySelectorsEditor title="Destinations"
                                 anyLabel={scope ? "Any host, but not broadcast or multicast" : "Any destination"}
                                 anyTitle={scope ? "Any host. Broadcast and multicast only if listed." : undefined}
                                 destinations={true}
                                 selectors={rule.destinations}
                                 onChange={(destinations) => onChange({...rule, destinations: destinations})}
                                 options={options}
                                 organizationId={organizationId}
                                 tenantId={tenantId}
                                 idPrefix={idPrefix + "-destinations"} />
        </div>

        <div className="policy-rule-side">
          <PolicyServicesEditor services={rule.services}
                                onChange={(services) => onChange({...rule, services: services})}
                                options={options}
                                idPrefix={idPrefix + "-services"} />
        </div>
      </div>
    </div>
  )

}
