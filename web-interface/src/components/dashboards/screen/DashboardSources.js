import React from "react";

// {key, topology(widgetId, onSuccess, onError)}. Without it, like in the layout editor, widgets with a feed of their
// own show sample data.
const DashboardSources = React.createContext(null);

export default DashboardSources;
