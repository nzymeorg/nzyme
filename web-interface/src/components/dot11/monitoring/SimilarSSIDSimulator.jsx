import React, {useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import LoadingSpinner from "../../misc/LoadingSpinner";

import numeral from "numeral";

const dot11Service = new Dot11Service();

function SimilarSSIDSimulator(props) {

  const revision = props.revision;
  const threshold = props.threshold;
  const uuid = props.uuid;

  const [passedThreshold, setPassedThreshold] = useState(threshold);
  const [isLoading, setIsLoading] = useState(false);
  const [results, setResults] = useState(null);

  useEffect(() => {
    if (revision > 0) {
      setResults(null);
      setIsLoading(true);
      setPassedThreshold(threshold);

      dot11Service.simulateSimilarSSIDs(uuid, threshold, (response) => {
        setResults(response.data);
        setIsLoading(false);
      })
    }
  }, [revision]);

  if (!results) {
    if (isLoading) {
      return (
          <React.Fragment>
            <div className="row mt-3">
              <div className="col-xl-12 col-xxl-6">
                <div className="card">
                  <div className="card-body">
                    <LoadingSpinner />
                  </div>
                </div>
              </div>
            </div>
          </React.Fragment>
      )
    }

    return null;
  }

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Simulation Results for {passedThreshold}% Similarity Threshold</h3>

                <p>Based on SSIDs seen in previous 15 minutes.</p>

                <table className="table table-sm table-hover table-striped">
                  <thead>
                  <tr>
                    <th>SSID</th>
                    <th>Similarity</th>
                  </tr>
                  </thead>
                  <tbody>
                  {results.map((result, i) => {
                    return (
                        <tr key={i}>
                          <td>{result.ssid}</td>
                          <td>
                            <span className={result.alerted ? "text-danger" : "text-success"}>
                              {numeral(result.similarity).format("0.[00]")}%
                            </span>{' '}
                            {result.is_monitored ? <span className="text-muted">(Monitored Network, exempt)</span> : null}
                            {result.is_allowed ? <span className="text-muted">(Allowed SSID, exempt)</span> : null}
                          </td>
                        </tr>
                    )
                  })}
                  </tbody>
                </table>

                {results.length === 0 ? <div className="alert alert-warning mb-0">
                  No SSIDs found. Make sure you have taps recording WiFi data and that WiFi networks are in range.</div>
                    : null}
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
)

}

export default SimilarSSIDSimulator;