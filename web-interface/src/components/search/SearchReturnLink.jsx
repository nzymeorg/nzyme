import React, {useContext, useState} from "react";
import {Link, useLocation} from "react-router-dom";
import ApiRoutes from "../../util/ApiRoutes";
import {UserContext} from "../../App";
import {clearSearchReturn, searchReturn} from "./searchReturn";

export default function SearchReturnLink() {

  const user = useContext(UserContext);
  const location = useLocation();
  const [, setDismissed] = useState(0);

  const stored = location.pathname === ApiRoutes.SEARCH.RESULTS ? null : searchReturn(user);
  if (!stored) {
    return null;
  }

  const title = "Back to the search results for \"" + stored.query + "\"";

  return (
    <div className="btn-group nav-search-return">
      <Link to={stored.path} state={{returned: true}} className="btn btn-outline-secondary"
            title={title} aria-label={title}>
        <i className="fa-solid fa-arrow-left" /> <span>Search Results</span>
      </Link>
      <button className="btn btn-outline-secondary" type="button" title="Dismiss" aria-label="Dismiss"
              onClick={() => {
                clearSearchReturn();
                setDismissed((count) => count + 1);
              }}>
        <i className="fa-solid fa-xmark" />
      </button>
    </div>
  );

}
