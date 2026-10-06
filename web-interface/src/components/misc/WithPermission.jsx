import {useContext} from "react";
import {UserContext} from "../../App";
import {userHasPermission} from "../../util/Tools";

export default function WithPermission({permission, children}) {

  const user = useContext(UserContext);

  if (!userHasPermission(user, permission)) {
    return null;
  }

  return children;

}