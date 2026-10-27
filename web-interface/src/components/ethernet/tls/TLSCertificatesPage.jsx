import React, {useContext, useEffect, useState} from "react";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {Presets} from "../../shared/timerange/TimeRange";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import {TapContext} from "../../../App";
import Filters from "../../shared/filtering/Filters";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import ApiRoutes from "../../../util/ApiRoutes";
import SectionMenuBar from "../../shared/SectionMenuBar";
import usePageTitle from "../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import {TLS_MENU_ITEMS} from "./TLSMenuItems";
import {TLS_CERTIFICATE_FILTER_FIELDS} from "./TLSCertificateFilterFields";
import TLSCertificatesTable from "./TLSCertificatesTable";
import TLSNewCertificatesHistogram from "./TLSNewCertificatesHistogram";
import TLSCertificateTopHistogram from "./TLSCertificateTopHistogram";
import onSaveFiltersAsMonitor from "../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../misc/RenderConditionally";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";


export default function TLSCertificatesPage() {

  usePageTitle("TLS Certificates");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)
  const [filters, setFilters] = useUrlFilters(TLS_CERTIFICATE_FILTER_FIELDS);
  const [monitorsReady, setMonitorsReady] = useState(false);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={TLS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.TLS.CERTIFICATES.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Filters"
                                       helpLink="https://go.nzyme.org/ethernet-tls"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={TLS_CERTIFICATE_FILTER_FIELDS}
                         monitorType="ETHERNET_TLS_CERTIFICATE"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_TLS_CERTIFICATE", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="New Certificates"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSNewCertificatesHistogram timeRange={timeRange}
                                               setTimeRange={setTimeRange}
                                               filters={filters}
                                               revision={revision} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Issuers"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSCertificateTopHistogram type="issuers"
                                              timeRange={timeRange}
                                              filters={filters}
                                              setFilters={setFilters}
                                              revision={revision} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Key Types"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSCertificateTopHistogram type="key_types"
                                              timeRange={timeRange}
                                              filters={filters}
                                              setFilters={setFilters}
                                              revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Signature Algorithms"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSCertificateTopHistogram type="signature_algorithms"
                                              timeRange={timeRange}
                                              filters={filters}
                                              setFilters={setFilters}
                                              revision={revision} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Expired and Expiring Within 30 Days"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSCertificatesTable timeRange={timeRange}
                                        filters={filters}
                                        setFilters={setFilters}
                                        revision={revision}
                                        expiringDays={30}
                                        perPage={10} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="All Certificates"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <p className="text-muted">
                    Certificates observed in TLS sessions and by portal integrity probes. In passive TLS sessions,
                    certificates are only visible in handshakes using TLS 1.2 or older. TLS 1.3 encrypts them.
                  </p>

                  <TLSCertificatesTable timeRange={timeRange}
                                        filters={filters}
                                        setFilters={setFilters}
                                        revision={revision} />
                </div>
              </div>
            </div>
          </div>
        </RenderConditionally>
      </React.Fragment>
  )

}
