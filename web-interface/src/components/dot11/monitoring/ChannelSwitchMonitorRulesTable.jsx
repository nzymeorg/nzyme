import React from "react";
import {toast} from "react-toastify";
import Dot11Service from "../../../services/Dot11Service";
import {
  CHANNEL_SWITCH_MONITOR_RULES,
  channelSwitchMonitorRuleDescription,
  channelSwitchMonitorRuleToName
} from "../channelswitches/ChannelSwitchNames";

const dot11Service = new Dot11Service();

export default function ChannelSwitchMonitorRulesTable({ssid, onChange}) {

  const setStatus = (e, rule, status) => {
    e.preventDefault();

    const name = channelSwitchMonitorRuleToName(rule);
    if (!confirm("Really " + (status ? "enable" : "disable") + " the detection method \"" + name + "\"?")) {
      return;
    }

    dot11Service.setChannelSwitchMonitorRuleStatus(ssid.uuid, rule, status, () => {
      toast.success("Detection method " + (status ? "enabled." : "disabled."));
      onChange();
    })
  }

  return (
      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>Method</th>
          <th>Description</th>
          <th>Status</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {CHANNEL_SWITCH_MONITOR_RULES.map((rule) => {
          const enabled = !ssid.channel_switch_disabled_rules.includes(rule);

          return (
              <tr key={rule}>
                <td className="text-nowrap">{channelSwitchMonitorRuleToName(rule)}</td>
                <td>{channelSwitchMonitorRuleDescription(rule)}</td>
                <td>{enabled ? "Enabled" : <span className="text-muted">Disabled</span>}</td>
                <td>
                  {enabled
                      ? <a href="#" title="Disable this detection method." onClick={(e) => setStatus(e, rule, false)}>Disable</a>
                      : <a href="#" title="Enable this detection method." onClick={(e) => setStatus(e, rule, true)}>Enable</a>}
                </td>
              </tr>
          )
        })}
        </tbody>
      </table>
  )

}
