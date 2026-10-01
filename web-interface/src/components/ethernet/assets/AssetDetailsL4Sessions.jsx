import React, {useContext, useEffect, useState} from "react";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {Presets} from "../../shared/timerange/TimeRange";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import ApiRoutes from "../../../util/ApiRoutes";
import L4SessionsTable from "../l4/L4SessionsTable";

export default function AssetDetailsL4Sessions({title, filters}) {

  const tapContext = useContext(TapContext);

  const [timerange, setTimerange] = useState(Presets.RELATIVE_HOURS_24);
  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  const onRefresh = () => {
    setRevision(new Date());
  }

  return (
    <>
      <CardTitleWithControls title={title}
                             doNotPersistTimeRange={true}
                             timeRange={timerange}
                             setTimeRange={setTimerange}
                             internalLink={ApiRoutes.ETHERNET.L4.SESSIONS + "?filters=" + JSON.stringify(filters)}
                             refreshAction={onRefresh} />

      <L4SessionsTable filters={filters}
                       timeRange={timerange}
                       revision={revision}
                       perPage={10} />
    </>
  )

}