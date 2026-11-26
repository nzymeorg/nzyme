import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {Presets} from "../../../shared/timerange/TimeRange";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import Filters from "../../../shared/filtering/Filters";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../../util/UsePageTitle";
import ApiRoutes from "../../../../util/ApiRoutes";
import TwoColumnHistogram from "../../../widgets/histograms/TwoColumnHistogram";
import Dot11Service from "../../../../services/Dot11Service";
import {DISCO_MENU_ITEMS} from "../DiscoMenuItems";
import {DISCO_ANOMALY_FILTER_FIELDS} from "./DiscoAnomalyFilterFields";
import DiscoAnomaliesChart from "./DiscoAnomaliesChart";
import DiscoAnomalies from "./DiscoAnomalies";
import {discoAnomalyTypeToName} from "./DiscoAnomalyTypes";

const dot11Service = new Dot11Service();

export default function DiscoAnomaliesPage() {

  usePageTitle("WiFi Disconnection Anomalies");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const [filters, setFilters] = useUrlFilters(DISCO_ANOMALY_FILTER_FIELDS);

  const [histogram, setHistogram] = useState(null);
  const [types, setTypes] = useState(null);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    let active = true;

    setHistogram(null);
    dot11Service.getDiscoAnomaliesHistogram(timeRange, filters, selectedTaps, (response) => {
      if (active) {
        setHistogram(response);
      }
    });

    return () => {
      active = false;
    }
  }, [organizationId, tenantId, timeRange, filters, selectedTaps, revision]);

  const typeHistogram = () => {
    if (!types) {
      return null;
    }

    const values = types.map((t) => {
      return {
        column_one: {type: "GENERIC", value: discoAnomalyTypeToName(t.type), metadata: {type: t.type}},
        column_two: {type: "INTEGER", value: t.anomalies}
      }
    });

    return {total: values.length, show_bar_chart: true, values: values};
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={DISCO_MENU_ITEMS} activeRoute={ApiRoutes.DOT11.DISCO.ANOMALIES.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Disconnection Anomalies Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       helpLink="https://go.nzyme.org/disco-anomalies" />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={DISCO_ANOMALY_FILTER_FIELDS}
                         onMonitorsReady={() => {}} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="New Anomalies"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())}
                                       helpLink="https://go.nzyme.org/disco-anomalies-detection" />

                <DiscoAnomaliesChart histogram={histogram} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>

          <div className="col-xl-12 col-xxl-6 mt-3 mt-xxl-0">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Anomalies by Type"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())}
                                       helpLink="https://go.nzyme.org/disco-anomalies-types" />

                {types && types.length === 0 ?
                    <div className="alert alert-info mb-0">No anomalies recorded.</div>
                    : <TwoColumnHistogram data={typeHistogram()}
                                          columnTitles={["Anomaly", "Count"]}
                                          columnFilterElements={[
                                            {field: "anomaly_type", metadataField: "type", fields: DISCO_ANOMALY_FILTER_FIELDS, setFilters: setFilters},
                                            null
                                          ]}
                                          customChartMarginLeft={260} />}
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Anomalies"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())}
                                       helpLink="https://go.nzyme.org/disco-anomalies-reports" />

                <p className="text-muted">
                  An anomaly is a pattern that one tap recorded around one access point.
                </p>

                <DiscoAnomalies timeRange={timeRange}
                                filters={filters}
                                setFilters={setFilters}
                                revision={revision}
                                onLoaded={(response) => setTypes(response.types)} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}
