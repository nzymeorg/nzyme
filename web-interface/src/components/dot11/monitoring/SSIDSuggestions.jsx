import React, {useEffect, useState} from "react";

function SSIDSuggestions(props) {

  const ssidNames = props.ssidNames;
  const input = props.input;
  const onSuggestionSelected = props.onSuggestionSelected;

  const [suggestedSSIDs, setSuggestedSSIDs] = useState([]);

  useEffect(() => {
    if (ssidNames && input) {
      const suggestions = [];
      ssidNames.map(function (name) {
        if (name.toLowerCase().includes(input.toLowerCase())) {
          suggestions.push(name);
        }
      });

      setSuggestedSSIDs(suggestions);
    }
  }, [input, ssidNames])

  if (!input) {
    return (
        <div className="alert alert-info">
          Start typing an SSID in the input field above to see suggestions.
        </div>
    )
  }

  if (!ssidNames || ssidNames.length === 0) {
    return (
        <div className="alert alert-info">
          Cannot suggest SSIDs, because Nzyme has not recorded any yet.
        </div>
    )
  }

  if (suggestedSSIDs && suggestedSSIDs.length > 0) {
    return (
        <React.Fragment>
          <h4>Suggested SSIDs</h4>

          <p className="text-muted">
            Click on a suggested SSID to use it.
          </p>

          <ul>
            {suggestedSSIDs.map(function (ssid, i) {
              return (
                  <li key={"suggestedssids-" + i}>
                    <a href="#" onClick={() => onSuggestionSelected(ssid)}>{ssid}</a>
                  </li>
              )
            })}
          </ul>
        </React.Fragment>
    )
  } else {
    return (
      <React.Fragment>
        <h4>Suggested SSIDs</h4>

        <ul>
          <li className="text-muted">
            Could not determine any suggested SSIDs. You can still type any network name you wish
            into the input box above.
          </li>
        </ul>
      </React.Fragment>
    )
  }



}

export default SSIDSuggestions;