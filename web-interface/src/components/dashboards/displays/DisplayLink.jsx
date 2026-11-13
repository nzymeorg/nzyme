import React, {useState} from "react";
import ApiRoutes from "../../../util/ApiRoutes";

export default function DisplayLink({token}) {

  const [copiedToClipboard, setCopiedToClipboard] = useState(false);

  const link = window.location.origin + ApiRoutes.DISPLAY + "#" + token;

  const copyLink = () => {
    navigator.clipboard.writeText(link).then(() => setCopiedToClipboard(true));
  }

  return (
    <div className="alert alert-warning">
      <h5 className="alert-heading">
        <i className="fa fa-warning"/> Copy the display link now
      </h5>
      <p>
        This is the only time the link is shown. Open it in the browser of the screen. Everyone who has the link
        can see the dashboards of this display.
      </p>

      <div className="input-group">
        <input type="text" className="form-control font-monospace" value={link} readOnly
               onFocus={(e) => e.target.select()} />
        <button className="btn btn-secondary" type="button" onClick={copyLink}>
          {copiedToClipboard ? <span><i className="fa fa-check"/> Copied</span> : "Copy"}
        </button>
      </div>
    </div>
  )

}
