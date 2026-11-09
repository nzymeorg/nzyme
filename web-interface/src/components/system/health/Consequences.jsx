import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import NodeClockConsequence from "./consequences/NodeClockConsequence";
import CryptoSyncConsequence from "./consequences/CryptoSyncConsequence";
import DatabaseClockConsequence from "./consequences/DatabaseClockConsequence";
import TapClockConsequence from "./consequences/TapClockConsequence";
import NodeOfflineConsequence from "./consequences/NodeOfflineConsequence";
import TapOfflineConsequence from "./consequences/TapOfflineConsequence";
import TapThroughputConsequence from "./consequences/TapThroughputConsequence";
import TapDropConsequence from "./consequences/TapDropConsequence";
import TapBufferConsequence from "./consequences/TapBufferConsequence";
import TapErrorConsequence from "./consequences/TapErrorConsequence";
import TapFilePermissionsConsequence from "./consequences/TapFilePermissionsConsequence";
import NodeFilePermissionsConsequence from "./consequences/NodeFilePermissionsConsequence";
import TLSExpirationConsequence from "./consequences/TLSExpirationConsequence";
import TaskFailureConsequence from "./consequences/TaskFailureConsequence";
import TaskStuckConsequence from "./consequences/TaskStuckConsequence";
import MessageStuckConsequence from "./consequences/MessageStuckConsequence";
import MessageFailureConsequence from "./consequences/MessageFailureConsequence";
import ConnectConsequence from "./consequences/ConnectConsequence";
import AssetIdentityBacklogConsequence from "./consequences/AssetIdentityBacklogConsequence";
import DatabaseConfigurationConsequence from "./consequences/DatabaseConfigurationConsequence";
import DatabaseVacuumConsequence from "./consequences/DatabaseVacuumConsequence";
import DatabaseBloatConsequence from "./consequences/DatabaseBloatConsequence";
import DatabasePerformanceConsequence from "./consequences/DatabasePerformanceConsequence";
import DatabaseTransactionsConsequence from "./consequences/DatabaseTransactionsConsequence";

function Consequences(props) {

  const indicators = props.indicators

  const [consequences, setConsequences] = useState([]);

  useEffect(() => {
    const tempCons = [];
    for (const k in indicators) {
      const indicator = indicators[k];

      if (!indicator.expired && indicator.active && (indicator.level === 'RED' || indicator.level === 'ORANGE')) {
        tempCons.push(indicator.id)
      }
    }

    setConsequences(tempCons);

  }, [indicators])

  if (!indicators) {
    return <LoadingSpinner />
  }

  if (consequences.length === 0) {
    return (
        <div className="alert alert-info">
          This section will show consequences and resolution steps in case of illuminated indicators above.
        </div>
    )
  }

  return (
      <div className="health-consequences">
        <CryptoSyncConsequence show={consequences.includes("crypto_sync")} />
        <NodeClockConsequence show={consequences.includes("node_clock")} />
        <DatabaseClockConsequence show={consequences.includes("db_clock")} />
        <TapClockConsequence show={consequences.includes("tap_clock")} />
        <NodeOfflineConsequence show={consequences.includes("node_offline")} />
        <TapOfflineConsequence show={consequences.includes("tap_offline")} />
        <TapThroughputConsequence show={consequences.includes("tap_tpx")} />
        <TLSExpirationConsequence show={consequences.includes("tls_exp")} />
        <TapDropConsequence show={consequences.includes("tap_drop")} />
        <TapBufferConsequence show={consequences.includes("tap_buffer")} />
        <TapErrorConsequence show={consequences.includes("tap_error")} />
        <TapFilePermissionsConsequence show={consequences.includes("tap_file_permissions")} />
        <NodeFilePermissionsConsequence show={consequences.includes("node_file_permissions")} />
        <TaskFailureConsequence show={consequences.includes("tasks_queue_task_failure")} />
        <TaskStuckConsequence show={consequences.includes("tasks_queue_task_stuck")} />
        <MessageFailureConsequence show={consequences.includes("message_bus_message_failure")} />
        <MessageStuckConsequence show={consequences.includes("message_bus_message_stuck")} />
        <ConnectConsequence show={consequences.includes("connect")} />
        <AssetIdentityBacklogConsequence show={consequences.includes("asset_identity_backlog")} />
        <DatabaseConfigurationConsequence show={consequences.includes("db_configuration")} />
        <DatabaseVacuumConsequence show={consequences.includes("db_vacuum")} />
        <DatabaseBloatConsequence show={consequences.includes("db_bloat")} />
        <DatabasePerformanceConsequence show={consequences.includes("db_performance")} />
        <DatabaseTransactionsConsequence show={consequences.includes("db_transactions")} />
      </div>
  )

}

export default Consequences;