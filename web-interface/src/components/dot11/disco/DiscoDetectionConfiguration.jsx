import React from "react";
import ConfigurationSectionTable from "../../configuration/ConfigurationSectionTable";
import Dot11Service from "../../../services/Dot11Service";

const dot11Service = new Dot11Service();

export default function DiscoDetectionConfiguration({organizationId, tenantId, section}) {

  return <ConfigurationSectionTable organizationId={organizationId}
                                    tenantId={tenantId}
                                    section={section}
                                    getConfiguration={dot11Service.getDiscoAnomalyDetectionConfiguration}
                                    updateConfiguration={dot11Service.updateDiscoAnomalyDetectionConfiguration} />

}
