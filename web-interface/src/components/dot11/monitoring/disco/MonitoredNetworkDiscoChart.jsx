import React, {useEffect, useState} from "react";
import Dot11Service from "../../../../services/Dot11Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import SimpleBarChart from "../../../widgets/charts/SimpleBarChart";
const dot11Service = new Dot11Service();

function MonitoredNetworkDiscoChart(props) {

  const tapUuid = props.selectedTapUuid;
  const monitoredNetwork = props.monitoredNetwork;
  const timeRange = props.timeRange;

  const [histogram, setHistogram] = useState(null);
  const [anomalies, setAnomalies] = useState(null);
  const [configuration, setConfiguration] = useState(null);

  useEffect(() => {
    dot11Service.getDiscoDetectionConfiguration(monitoredNetwork.uuid, setConfiguration);
  }, [monitoredNetwork]);

  useEffect(() => {
    if (configuration && tapUuid) {
      setHistogram(null);
      setAnomalies(null);

      dot11Service.getDiscoHistogram(
          "disconnection",
          timeRange,
          [tapUuid],
          null,
          monitoredNetwork.uuid,
          setHistogram
      );

      dot11Service.simulateDiscoDetectionConfiguration(
          configuration.method_type,
          configuration.configuration,
          monitoredNetwork.uuid,
          tapUuid,
          setAnomalies
      )
    }
  }, [tapUuid, configuration, monitoredNetwork]);

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
            timeRange={timeRange}
            data={formatData(histogram)}
            shapes={formatShapes(anomalies.anomalies)}
        />
      </React.Fragment>
  )

}

export default MonitoredNetworkDiscoChart;