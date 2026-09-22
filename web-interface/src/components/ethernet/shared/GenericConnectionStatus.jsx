import React from "react";

export default function GenericConnectionStatus({status, style = "badge"}) {

  if (style === "badge") {
    switch (status) {
      case "Active":
        return <span className="badge bg-success">Active</span>
      case "Inactive":
        return <span className="badge bg-warning">Inactive</span>
      case "InactiveTimeout":
        return <span className="badge bg-warning">TCP Timeout</span>
      default:
        return <span className="badge bg-secondary">Invalid</span>
    }
  } else if (style === "icon") {
    switch (status) {
      case "Active":
        return <span><i className="fa fa-circle text-success blink" /> Active</span>
      case "Inactive":
        return <span><i className="fa fa-circle text-muted" /> Inactive</span>
      case "InactiveTimeout":
        return <span><i className="fa fa-circle text-muted" /> TCP Timeout</span>
      default:
        return <span><i className="fa fa-circle text-muted" /> Invalid</span>
    }
  }

}