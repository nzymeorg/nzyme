import React from "react";
import numeral from "numeral";
import SimpleBarChart from "../../widgets/charts/SimpleBarChart";
import Store from "../../../util/Store";

function ActiveChannelsChart(props) {

  const histogram = props.histogram;

  const declared = histogram.channels.filter(c => c.frequency);
  const tapFrequencies = histogram.tap_frequencies.filter(c => c.frequency);

  const labels = new Map();
  [...declared, ...tapFrequencies]
      .sort((a, b) => a.frequency - b.frequency)
      .forEach(c => labels.set(c.frequency, c.channel + ": " + numeral(c.frequency).format("0,0") + " MHz"));

  const frames = (channels) => [...labels.keys()].map((frequency) => {
    const channel = channels.find(c => c.frequency === frequency);
    return channel ? channel.frames : 0;
  });

  if (labels.size === 0) {
    return <div className="alert alert-info mb-0">No channels recorded in selected time range.</div>
  }

  const darkMode = Store.get('dark_mode');

  return (
      <SimpleBarChart
          height={225}
          customMarginTop={35}
          customMarginLeft={45}
          customMarginBottom={30}
          barmode="group"
          showLegend={true}
          finalData={[
            {
              name: "Declared Channel",
              x: [...labels.values()],
              y: frames(declared),
              type: 'bar',
              marker: { color: darkMode ? '#e6e6e6' : '#1d30d7' }
            },
            {
              name: "Tap Frequency",
              x: [...labels.values()],
              y: frames(tapFrequencies),
              type: 'bar',
              marker: { color: darkMode ? '#62636f' : '#a9aab8' }
            }
          ]} />
  )

}

export default ActiveChannelsChart;
