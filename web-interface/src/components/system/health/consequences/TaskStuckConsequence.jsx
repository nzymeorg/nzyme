import React from "react";
import Consequence from "../Consequence";
import TaskStuckProcedure from "./procedures/TaskStuckProcedure";

function TaskStuckConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Task Stuck"
          color="red"
          problem="At least one cluster task is stuck: Claimed by a node to work on but not executed. Example cluster
                  tasks are detection runs or delivery of alert notifications."
          acceptableRange={[
            "Task processing must complete within 1 hour after acknowledgment by a Nzyme node"
          ]}
          consequences={[
            "An expected background action of the program was not performed"
          ]}
          procedure={<TaskStuckProcedure />}
      />
  )

}

export default TaskStuckConsequence;