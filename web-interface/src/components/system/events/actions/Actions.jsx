import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import EventActionsService from "../../../../services/EventActionsService";
import ActionsTable from "../shared/ActionsTable";
import ApiRoutes from "../../../../util/ApiRoutes";

const eventActionsService = new EventActionsService();

function Actions() {

  const PER_PAGE = 10;

  const [actions, setActions] = useState(null);
  const [page, setPage] = useState(1);

  useEffect(() => {
    setActions(null);
    eventActionsService.findAllActions(setActions, PER_PAGE, (page-1)*PER_PAGE);
  }, [])

  return (
      <React.Fragment>
        <ActionsTable actions={actions}
                      perPage={PER_PAGE}
                      page={page}
                      setPage={setPage} />

        <Link className="btn btn-secondary btn-sm" to={ApiRoutes.SYSTEM.EVENTS.ACTIONS.CREATE}>
          Create Action
        </Link>
      </React.Fragment>
  )
}

export default Actions;