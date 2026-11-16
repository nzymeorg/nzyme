import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import {
  FINDING_CLASSES,
  OUTCOMES,
  policyFindingsLink,
  portServiceLabel
} from "../../../ethernet/segmentation/SegmentationFormat";

export default function SegmentationPolicyAlertDetails({alert}) {

  const a = alert.attributes;

  const crossed = a.finding_class === "CROSSED";
  const findingClass = FINDING_CLASSES[a.finding_class];
  const outcome = OUTCOMES[a.example_outcome];

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                <dt>Policy</dt>
                <dd>
                  <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(a.policy_uuid)}>{a.policy_name}</Link>
                </dd>
                <dt>Class</dt>
                <dd>
                  {findingClass ? findingClass.title : a.finding_class}
                  {findingClass ? <span className="text-muted"> {findingClass.description}</span> : null}
                </dd>
                <dt>Example</dt>
                <dd>
                  <span className="machine-data">{a.example_source}</span>
                  {a.example_destination ?
                    <React.Fragment>
                      {' '}to <span className="machine-data">{a.example_destination}</span>
                      {a.example_service ? <span> on {portServiceLabel(a.example_service)}</span> : null}
                      {a.example_outcome ?
                        <span className="text-muted" title={outcome ? outcome.description : undefined}>
                          {' '}({outcome ? outcome.title : a.example_outcome})
                        </span> : null}
                    </React.Fragment> : null}
                </dd>
                <dt>Findings</dt>
                <dd>
                  <Link to={policyFindingsLink(a.policy_name)}>All findings of this policy</Link>
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Possible false-positives</h3>

              <p>
                These circumstances can lead to false-positive alerts of this type:
              </p>

              <ul className="mb-0">
                <li>Traffic that is intended but not listed as an allowed crossing of the policy yet. Allow it
                  from the finding.</li>
                <li>Segments or tags that are out of date, so that an address counts as part of the wrong
                  group.</li>
                <li>Systems that are expected to reach everywhere, like vulnerability scanners, monitoring or
                  backup. Add an allowed crossing for them.</li>
                {crossed ? null :
                  <li>Misconfigured or stray clients that try addresses they have no use for, like stale DNS or
                    discovery traffic.</li>}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
