import React from "react";

export default function TopologySummaryStrip({facts}) {

  const pending = <span className="text-muted">&hellip;</span>;

  return (
    <div className="row mt-3">
      <div className="col-12">
        <div className="card">
          <div className="card-body py-2">
            <div className="d-flex flex-wrap align-items-center">
              {facts.map(([label, value], i) => (
                <div key={label} className={"py-1 " + (i === facts.length - 1 ? "" : "pe-4 me-4 border-end")}>
                  <div className="small text-muted text-uppercase">{label}</div>
                  <div>{value === null ? pending : value}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );

}
