import React, {useEffect, useState} from "react";
import ClusterService from "../../../../../services/ClusterService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import SimpleLineChart from "../../../../widgets/charts/SimpleLineChart";

const clusterService = new ClusterService()

function fetchData(nodeId, metricName, setHistogram) {
  clusterService.findGaugeMetricHistogramOfNode(nodeId, metricName, setHistogram)
}

function formatData(data) {
  const result = {}

  Object.keys(data).sort().forEach(function (key) {
    result[key] = data[key].maximum
  })

  return result
}

function DatabasePoolChart(props) {

  const nodeId = props.nodeId;
  const metricName = props.metricName;
  const [histogram, setHistogram] = useState(null)

  useEffect(() => {
    setHistogram(null)
    fetchData(nodeId, metricName, setHistogram)
    const id = setInterval(() => fetchData(nodeId, metricName, setHistogram), 30000)
    return () => clearInterval(id)
  }, [nodeId, metricName, setHistogram])

  if (!histogram) {
    return <LoadingSpinner />
  }

  return <SimpleLineChart
      height={200}
      data={formatData(histogram.values)}
      customMarginLeft={85}
      customMarginRight={25}
      tickformat={'.0f'}
  />

}

export default DatabasePoolChart;
