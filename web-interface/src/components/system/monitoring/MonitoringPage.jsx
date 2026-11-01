import React, { useEffect, useState } from 'react'
import MonitoringService from '../../../services/MonitoringService'
import ExportersTable from './ExportersTable'
import usePageTitle from "../../../util/UsePageTitle";

const monitoringService = new MonitoringService()

function MonitoringPage () {

  usePageTitle("Monitoring & Metrics");

  const [summary, setSummary] = useState(null)

  useEffect(() => {
    monitoringService.getSummary(setSummary)
  }, [])

  return (
        <div>
            <div className="row">
                <div className="col-12">
                    <h1>Monitoring &amp; Metrics</h1>
                </div>
            </div>

            <div className="row mt-2">
                <div className="col-xl-12 col-xxl-6">
                    <div className="card">
                        <div className="card-body">
                            <h3>Metrics Exporters</h3>

                            <p>
                                The Nzyme system can make internal metrics available to other monitoring systems. Learn
                                more about metrics exporters in
                                the <a href="https://go.nzyme.org/metrics-exporters" target="_blank" rel="noreferrer">Nzyme documentation</a>.
                            </p>

                            <ExportersTable summary={summary} />
                        </div>
                    </div>
                </div>
            </div>

        </div>
  )
}

export default MonitoringPage
