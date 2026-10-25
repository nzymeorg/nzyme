import React from 'react'
import ApiRoutes from "../../util/ApiRoutes";
import {Link} from "react-router-dom";

function UserProfileBlock(props) {

  const user = props.user;

  return (
    <div className="user-profile">
      <div className="name">
        {user.name}<br />
        <Link to={ApiRoutes.USERPROFILE.PROFILE}>
          <i className="fa-solid fa-chevron-right settings-icon"></i>
          Settings
        </Link>
      </div>
    </div>
  )
}

export default UserProfileBlock
