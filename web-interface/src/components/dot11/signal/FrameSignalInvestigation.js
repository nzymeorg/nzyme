import React, {useContext} from "react";

export const FrameSignalInvestigationContext = React.createContext(null);

export function useFrameSignalInvestigation() {
  return useContext(FrameSignalInvestigationContext);
}
