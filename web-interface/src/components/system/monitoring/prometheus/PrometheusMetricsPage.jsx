import {Link} from "react-router-dom";
import React, { useEffect, useState } from 'react'
import Routes from '../../../../util/ApiRoutes'
import ConfigurationValue from '../../../configuration/ConfigurationValue'
import MonitoringService from '../../../../services/MonitoringService'
import LoadingSpinner from '../../../misc/LoadingSpinner'
import ConfigurationModal from '../../../configuration/modal/ConfigurationModal'
import EncryptedConfigurationValue from '../../../configuration/EncryptedConfigurationValue'
import IncompleteConfigurationWarning from "../../../misc/IncompleteConfigurationWarning";
import usePageTitle from "../../../../util/UsePageTitle";

const monitoringService = new MonitoringService()

function PrometheusMetricsPage () {

  usePageTitle("Prometheus Exporter");

  const [configuration, setConfiguration] = useState(null)
  const [localRevision, setLocalRevision] = useState(0)

  useEffect(() => {
    monitoringService.getPrometheusExporterConfiguration(setConfiguration)
  }, [localRevision])

  if (!configuration) {
    return <LoadingSpinner />
  }

  return (
        <div className="row">
            <div className="col-md-10">
                <nav aria-label="breadcrumb">
                    <ol className="breadcrumb">
                        <li className="breadcrumb-item"><Link to={Routes.SYSTEM.MONITORING.INDEX}>Monitoring &amp; Metrics</Link></li>
                        <li className="breadcrumb-item">Exporters</li>
                        <li className="breadcrumb-item active" aria-current="page">Prometheus</li>
                    </ol>
                </nav>
            </div>
            <div className="col-md-2">
                <Link className="btn btn-primary float-end" to={Routes.SYSTEM.TAPS.INDEX}>Back</Link>
            </div>

            <div className="row">
                <div className="col-md-12">
                    <h1>Prometheus Exporter</h1>
                </div>
            </div>

            <IncompleteConfigurationWarning show={
                configuration.prometheus_rest_report_enabled.value &&
                (!configuration.prometheus_rest_report_username.value ||
                    !configuration.prometheus_rest_report_password.value_is_set)
            } />

            <div className="row mt-3">
                <div className="col-md-6">
                    <div className="card">
                        <div className="card-body">
                            <h3>Configuration</h3>

                            <table className="table table-sm table-hover table-striped">
                                <thead>
                                <tr>
                                    <th>Configuration</th>
                                    <th>Value</th>
                                    <th>Actions</th>
                                </tr>
                                </thead>
                                <tbody>
                                <tr>
                                    <td>REST Report Enabled</td>
                                    <td>
                                        <ConfigurationValue value={configuration.prometheus_rest_report_enabled.value}
                                                            configKey={configuration.prometheus_rest_report_enabled.key}
                                                            required={true}
                                                            boolean={true} />
                                    </td>
                                    <td>
                                        <ConfigurationModal config={configuration.prometheus_rest_report_enabled}
                                                            setGlobalConfig={setConfiguration}
                                                            setLocalRevision={setLocalRevision}
                                                            dbUpdateCallback={monitoringService.updatePrometheusExporterConfiguration} />
                                    </td>
                                </tr>
                                <tr>
                                    <td>Basic authentication username</td>
                                    <td>
                                        <ConfigurationValue value={configuration.prometheus_rest_report_username.value}
                                                            configKey={configuration.prometheus_rest_report_username.key}
                                                            required={true} />
                                    </td>
                                    <td>
                                        <ConfigurationModal config={configuration.prometheus_rest_report_username}
                                                            setGlobalConfig={setConfiguration}
                                                            setLocalRevision={setLocalRevision}
                                                            dbUpdateCallback={monitoringService.updatePrometheusExporterConfiguration} />
                                    </td>
                                </tr>
                                <tr>
                                    <td>Basic authentication password</td>
                                    <td>
                                        <EncryptedConfigurationValue isSet={configuration.prometheus_rest_report_password.value_is_set}
                                                            configKey={configuration.prometheus_rest_report_password.key}
                                                            required={true} />
                                    </td>
                                    <td>
                                        <ConfigurationModal config={configuration.prometheus_rest_report_password}
                                                            setGlobalConfig={setConfiguration}
                                                            setLocalRevision={setLocalRevision}
                                                            dbUpdateCallback={monitoringService.updatePrometheusExporterConfiguration} />
                                    </td>
                                </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>

        </div>
  )
}

export default PrometheusMetricsPage
