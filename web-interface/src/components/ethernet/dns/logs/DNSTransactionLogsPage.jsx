import React, {useContext, useEffect, useState} from 'react';
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import {Presets} from "../../../shared/timerange/TimeRange";
import DNSTransactionsTable from "./DNSTransactionsTable";
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import Filters from "../../../shared/filtering/Filters";
import DNSTransactionCountChart from "./widgets/DNSTransactionCountChart";
import {DNS_FILTER_FIELDS} from "../DNSFilterFields";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import {DNS_MENU_ITEMS} from "../DNSMenuItems";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import usePageTitle from "../../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import onSaveFiltersAsMonitor from "../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../misc/RenderConditionally";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";


export default function DNSTransactionLogsPage() {

  usePageTitle("DNS Transaction Logs");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)
  const [filters, setFilters] = useUrlFilters(DNS_FILTER_FIELDS);
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
          <div className="row mb-3">
            <div className="col-md-12">
              <SectionMenuBar items={DNS_MENU_ITEMS}
                              activeRoute={ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS}/>
            </div>
          </div>

          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={DNS_FILTER_FIELDS}
                         monitorType="ETHERNET_DNS_TRANSACTION"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_DNS_TRANSACTION", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Transaction Count"
                                         refreshAction={() => { setRevision(new Date()) }} />

                  <DNSTransactionCountChart timeRange={timeRange}
                                            filters={filters}
                                            setFilters={setFilters}
                                            setTimeRange={setTimeRange}
                                            revision={revision} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Transactions"
                                         refreshAction={() => { setRevision(new Date()) }} />

                  <DNSTransactionsTable timeRange={timeRange}
                                        filters={filters}
                                        setFilters={setFilters}
                                        revision={revision}/>
                </div>
              </div>
            </div>
          </div>
        </RenderConditionally>
      </React.Fragment>
  )

}
