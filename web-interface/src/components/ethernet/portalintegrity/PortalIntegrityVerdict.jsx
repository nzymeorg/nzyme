import React from "react";

export default function PortalIntegrityVerdict({verdict, setFilters}) {

  const format = () => {
    switch (verdict) {
      case "OK":
        return <span className="text-success">OK</span>
      case "MISSING":
        return <span className="text-danger">Missing</span>
      case "MISMATCH":
        return <span className="text-danger">Mismatch</span>
      case "INCONCLUSIVE":
        return <span className="text-warning">Inconclusive</span>
      case "NONE":
      default:
        return <span className="text-muted">n/a</span>
    }
  }

  return (
    <>
      {format()}

      {setFilters ? "" : null}
    </>
  )

}