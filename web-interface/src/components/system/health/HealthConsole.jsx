import React from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Indicator from "./Indicator";

function HealthConsole(props) {

  const indicators = props.indicators

  if (!indicators) {
    return <LoadingSpinner />
  }

  return (
        <div className="health-console">
          <div className="hc-row">
            <Indicator indicator={indicators.crypto_sync} name="Crypto Sync" />
            <Indicator indicator={indicators.db_clock} name="DB Clock" />
            <Indicator indicator={indicators.node_clock} name="Node Clock" />
            <Indicator indicator={indicators.tap_clock} name="Tap Clock" />
            <Indicator indicator={indicators.node_offline} name="Node Offline" />
            <Indicator indicator={indicators.tls_exp} name="TLS Expiration" />
          </div>

          <div className="hc-row">
            <Indicator indicator={indicators.tap_offline} name="Tap Offline" />
            <Indicator indicator={indicators.tap_tpx} name="Tap TPX" />
            <Indicator indicator={indicators.tap_drop} name="Tap Drop" />
            <Indicator indicator={indicators.tap_buffer} name="Tap Buffer" />
            <Indicator indicator={indicators.tap_error} name="Tap Error" />
            <Indicator indicator={indicators.tasks_queue_task_stuck} name="Task Stuck" />
          </div>

          <div className="hc-row">
            <Indicator indicator={indicators.tasks_queue_task_failure} name="Task Failure" />
            <Indicator indicator={indicators.message_bus_message_failure} name="Message Failure" />
            <Indicator indicator={indicators.message_bus_message_stuck} name="Message Stuck" />
            <Indicator indicator={indicators.tap_file_permissions} name="Tap File Perms" />
            <Indicator indicator={indicators.node_file_permissions} name="Node File Perms" />
          </div>

          <div style={{clear: "both"}} />
        </div>
  )

}

export default HealthConsole;