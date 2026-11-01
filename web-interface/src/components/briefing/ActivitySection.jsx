import React from "react";
import BriefingChart from "./BriefingChart";
import BriefingCard from "./BriefingCard";
import {windowPhrase} from "./briefingFormat";

export default function ActivitySection({activity, window, type, tapsTotal}) {

  if (!activity || tapsTotal === 0) {
    return null;
  }

  const charts = [];

  const chart = (key, title, data, options = {}) => {
    charts.push(
      <div key={key} className="briefing-chart-panel">
        <h4 className="briefing-chart-title">{title}</h4>
        <BriefingChart data={data} window={window} unit={options.unit} kind={options.kind} />
      </div>
    );
  }

  const dot11 = (a, prefix) => {
    chart(prefix + "bssids", prefix ? "WiFi: Access points seen" : "Access points seen", a.bssids);
    chart(prefix + "ssids", prefix ? "WiFi: Networks seen" : "Networks seen", a.ssids);
    chart(prefix + "disco", prefix ? "WiFi: Disconnection frames" : "Disconnection frames", a.disco);
  }

  const ethernet = (a, prefix) => {
    chart(prefix + "assets", prefix ? "Ethernet: Active assets" : "Active assets", a.assets);
  }

  const bluetooth = (a, prefix) => {
    chart(prefix + "devices", prefix ? "Bluetooth: Devices seen" : "Devices seen", a.devices);
  }

  if (type === "DOT11") {
    dot11(activity, "");
  } else if (type === "ETHERNET") {
    ethernet(activity, "");
  } else if (type === "BLUETOOTH") {
    bluetooth(activity, "");
  } else {
    chart("throughput", "Total tap throughput", activity.throughput, {unit: "bitrate"});
    if (activity.ethernet) {
      ethernet(activity.ethernet, "ethernet_");
    }
    if (activity.dot11) {
      dot11(activity.dot11, "dot11_");
    }
    if (activity.bluetooth) {
      bluetooth(activity.bluetooth, "bluetooth_");
    }
  }

  if (charts.length === 0) {
    return null;
  }

  return (
    <div className="row mt-3 briefing-activity">
      <div className="col-12">
        <BriefingCard title="Activity"
                      hint={"The shaded part of each chart is everything " + windowPhrase(window) + ". Left of it, for comparison, the equally long window before."}>
          <div className="briefing-charts">{charts}</div>
        </BriefingCard>
      </div>
    </div>
  );

}
