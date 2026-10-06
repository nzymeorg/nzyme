import React, {useEffect, useState} from "react";
import UserProfileService from "../../../services/UserProfileService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import moment from "moment";
import {toast} from "react-toastify";

const userProfileService = new UserProfileService();

export default function UserApiKeysTable(props) {

  const revision = props.revision;
  const onKeyDeleted = props.onKeyDeleted;

  const [keys, setKeys] = useState(null);

  useEffect(() => {
    setKeys(null);
    userProfileService.findOwnApiKeys(setKeys);
  }, [revision])

  const deleteKey = function(e, key) {
    e.preventDefault();

    if (!confirm("Really delete API key \"" + key.name + "\"? Any client using it will stop working immediately.")) {
      return;
    }

    userProfileService.deleteOwnApiKey(key.uuid, function() {
      toast.success("API key deleted.");

      if (onKeyDeleted) {
        onKeyDeleted();
      }
    });
  }

  const isExpired = function(key) {
    return key.expires_at && moment(key.expires_at).isBefore(moment());
  }

  const expiresSoon = function(key) {
    return key.expires_at && !isExpired(key) && moment(key.expires_at).isBefore(moment().add(7, "days"));
  }

  const expiry = function(key) {
    if (!key.expires_at) {
      return <span className="text-bold text-warning">Never</span>;
    }

    if (isExpired(key)) {
      return <span className="text-danger">Expired {moment(key.expires_at).fromNow()}</span>;
    }

    if (expiresSoon(key)) {
      return <span className="text-bold text-warning">{moment(key.expires_at).fromNow()}</span>;
    }

    return moment(key.expires_at).fromNow();
  }

  const lastActivity = function(key) {
    if (!key.last_activity) {
      return <span className="text-bold text-warning">Never used</span>;
    }

    if (moment(key.last_activity).isBefore(moment().subtract(7, "days"))) {
      return <span className="text-bold text-warning">{moment(key.last_activity).fromNow()}</span>;
    }

    return moment(key.last_activity).fromNow();
  }

  if (keys === null) {
    return <LoadingSpinner />
  }

  if (keys.length === 0) {
    return <div className="alert alert-info mb-0 mt-1">No API keys found.</div>
  }

  return (
      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>Name</th>
          <th>Created</th>
          <th>Expires</th>
          <th>Last Activity</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {keys.map((key, i) => {
          return (
              <tr key={"apikey-" + i}>
                <td>{key.name}</td>
                <td title={moment(key.created_at).format()}>
                  {moment(key.created_at).fromNow()}
                </td>
                <td title={key.expires_at ? moment(key.expires_at).format() : "Never"}>
                  {expiry(key)}
                </td>
                <td title={key.last_activity ? moment(key.last_activity).format() : "Never used"}>
                  {lastActivity(key)}
                </td>
                <td>
                  <a href="#" onClick={(e) => deleteKey(e, key)}>Delete</a>
                </td>
              </tr>
          )
        })}
        </tbody>
      </table>
  )

}
