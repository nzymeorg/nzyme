import React from "react";
import Consequence from "../Consequence";
import TaskStuckProcedure from "./procedures/TaskStuckProcedure";
import MessageStuckProcedure from "./procedures/MessageStuckProcedure";

function MessageStuckConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Message Stuck"
          color="red"
          problem="At least one message bus message is stuck: Claimed by the receiver node to work on but not executed."
          acceptableRange={[
            "Message processing must complete within 1 hour after acknowledgment by the receiver Nzyme node"
          ]}
          consequences={[
            "An expected background action of the program was not performed"
          ]}
          procedure={<MessageStuckProcedure />}
      />
  )

}

export default MessageStuckConsequence;