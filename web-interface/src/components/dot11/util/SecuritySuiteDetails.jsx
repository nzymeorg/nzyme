import React from "react";

const protection = (enabled) => {
  if (enabled === null || enabled === undefined) {
    return "n/a";
  }

  return enabled ? "Yes" : "No";
}

function SecuritySuiteDetails(props) {
  const suite = props.suite;

  return (
      <React.Fragment>
        {suite.identifier}{' '}
        <span className="text-muted">
          (Group: {suite.group_cipher ? suite.group_cipher : "None"},{' '}
          Pairwise: {suite.pairwise_ciphers ? suite.pairwise_ciphers : "None"},{' '}
          Key Management: {suite.key_management_modes ? suite.key_management_modes : "None"}, {' '}
          PMF: {suite.pmf_mode ? suite.pmf_mode : "n/a"},{' '}
          Beacon Protection: {protection(suite.beacon_protection)},{' '}
          Operating Channel Validation: {protection(suite.operating_channel_validation)})
        </span>
      </React.Fragment>
  )

}

export default SecuritySuiteDetails;