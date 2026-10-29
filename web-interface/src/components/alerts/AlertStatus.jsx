import React from "react";

export default function AlertStatus({alert}) {

  // Older responses only carry the active flag.
  const status = alert.status ? alert.status : (alert.is_active ? "Active" : "Inactive");

  switch (status) {
    case "Active":
      return <span><i className="fa-solid fa-circle text-danger blink" title="Alert is active."></i> Active</span>
    case "Resolved":
      return <span className="text-muted"><i className="fa-solid fa-circle-check" title="Alert was marked as resolved. It re-activates if the condition is seen again."></i> Resolved</span>
    default:
      return <span className="text-muted"><i className="fa-solid fa-circle" title="Alert is not active."></i> Inactive</span>
  }

}
