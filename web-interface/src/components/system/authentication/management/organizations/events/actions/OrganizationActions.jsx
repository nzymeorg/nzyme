import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import ApiRoutes from "../../../../../../../util/ApiRoutes";
import EventActionsService from "../../../../../../../services/EventActionsService";
import ActionsTable from "../../../../../events/shared/ActionsTable";

const eventActionsService = new EventActionsService();

function OrganizationActions(props) {

  const PER_PAGE = 10;

  const organizationId = props.organizationId;

  const [actions, setActions] = useState(null);
  const [page, setPage] = useState(1);

  useEffect(() => {
    setActions(null);
    eventActionsService.findAllActionsOfOrganization(organizationId, setActions, PER_PAGE, (page-1)*PER_PAGE);
  }, [page, organizationId])

  return (
      <React.Fragment>
        <ActionsTable organizationId={organizationId}
                      actions={actions}
                      perPage={PER_PAGE}
                      page={page}
                      setPage={setPage} />

        <Link className="btn btn-secondary btn-sm"
           to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS.ACTIONS.CREATE(organizationId)}>
          Create Action
        </Link>
      </React.Fragment>
  )

}

export default OrganizationActions;