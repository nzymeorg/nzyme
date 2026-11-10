import React, {useEffect, useState} from "react";
import Dot11Service from "../../../../../../services/Dot11Service";
import LoadingSpinner from "../../../../../misc/LoadingSpinner";
import SimpleBarChart from "../../../../../widgets/charts/SimpleBarChart";
import AnomaliesTable from "./AnomaliesTable";
import {Presets} from "../../../../../shared/timerange/TimeRange";

const dot11Service = new Dot11Service();

function SimulatorChart(props) {

  const tapUuid = props.selectedTapUuid;
  const methodType = props.methodType;
  const configuration = props.configuration;
  const monitoredNetworkId = props.monitoredNetworkId;

  const [histogram, setHistogram] = useState(null);
  const [anomalies, setAnomalies] = useState(null);

  useEffect(() => {
    if (tapUuid) {
      setHistogram(null);
      setAnomalies(null);

      dot11Service.getDiscoHistogram(
          Presets.RELATIVE_HOURS_24,
          null,
          [tapUuid],
          null,
          monitoredNetworkId,
          setHistogram
      );

      dot11Service.simulateDiscoDetectionConfiguration(
          methodType,
          configuration,
          monitoredNetworkId,
          tapUuid,
          setAnomalies
      )
    }
  }, [tapUuid, methodType, configuration, monitoredNetworkId]);

  const formatData = (data) => {
    const result = {}

    Object.keys(data).sort().forEach(function(key) {
      result[key] = data[key]["frame_count"];
    })

    return result
  }

  const formatShapes = (anomalies) => {
    let shapes = []

    Object.keys(anomalies).sort().forEach(function(key) {
      var timestamp = new Date(anomalies[key]["timestamp"]);

      shapes.push({
            type: 'rect',
            xref: 'x',
            yref: 'paper',
            x0: timestamp.setMinutes(timestamp.getMinutes()-5),
            y0: 0,
            x1: timestamp.setMinutes(timestamp.getMinutes()+10),
            y1: 1,
            fillcolor: '#EF233C',
            opacity: 0.3,
            line: {
              width: 0
            }
          }
      );
    })

    return shapes;
  }

  if (!histogram || anomalies === null || !tapUuid) {
    return (
        <div className="mt-3">
          <LoadingSpinner />
        </div>
    )
  }

  return (
      <React.Fragment>
        <SimpleBarChart
            height={200}
            lineWidth={1}
            customMarginBottom={35}
            data={formatData(histogram)}
            shapes={formatShapes(anomalies.anomalies)}
        />

        <div className="mt-4">
          <h4>Anomalies</h4>

          <AnomaliesTable anomalies={anomalies} />
        </div>
      </React.Fragment>
  )

}

export default SimulatorChart;