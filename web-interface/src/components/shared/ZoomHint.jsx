import React, {useCallback, useEffect, useRef, useState} from "react";

const HINT_MS = 1400;

export function useZoomHint() {
  const [visible, setVisible] = useState(false);
  const timer = useRef(null);

  const show = useCallback(() => {
    setVisible(true);
    if (timer.current !== null) window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      timer.current = null;
      setVisible(false);
    }, HINT_MS);
  }, []);

  useEffect(() => () => {
    if (timer.current !== null) window.clearTimeout(timer.current);
  }, []);

  return [visible, show];
}

export const wheelZooms = (event) => event.ctrlKey || event.metaKey;

export default function ZoomHint({visible, dragText}) {
  return (
    <React.Fragment>
      <div style={{
        position: "absolute",
        right: "8px",
        bottom: "8px",
        zIndex: 5,
        pointerEvents: "none",
        padding: "2px 8px",
        borderRadius: "4px",
        background: "rgba(15, 23, 42, 0.6)",
        color: "#e2e8f0",
        fontSize: "11px"
      }}>
        Ctrl + scroll to zoom{dragText ? ", drag to " + dragText : ""}
      </div>
      <div style={{
        position: "absolute",
        left: "50%",
        top: "50%",
        transform: "translate(-50%, -50%)",
        zIndex: 10,
        pointerEvents: "none",
        padding: "8px 14px",
        borderRadius: "4px",
        background: "rgba(15, 23, 42, 0.85)",
        color: "#f8fafc",
        fontSize: "13px",
        opacity: visible ? 1 : 0,
        transition: "opacity 0.25s"
      }}>
        Hold Ctrl and scroll to zoom
      </div>
    </React.Fragment>
  )
}
