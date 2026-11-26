import React, {useEffect, useState} from "react";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import Dot11Service from "../../../../services/Dot11Service";
import DiscoFramesTable from "../DiscoFramesTable";

const dot11Service = new Dot11Service();

export default function DiscoSignalFrameRecords({timeRange, filters, setFilters, taps, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [frames, setFrames] = useState(null);
  const [page, setPage] = useState(1);
  const [orderColumn, setOrderColumn] = useState("created_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const perPage = 25;

  useEffect(() => {
    let current = true;
    dot11Service.findDiscoFrames(organizationId, tenantId, timeRange, filters, taps, null, orderColumn, orderDirection,
        perPage, (page - 1) * perPage, (data) => current && setFrames(data));

    return () => {
      current = false;
    }
  }, [orderColumn, orderDirection, page, revision]);

  return <DiscoFramesTable frames={frames}
                           page={page}
                           setPage={setPage}
                           perPage={perPage}
                           orderColumn={orderColumn}
                           orderDirection={orderDirection}
                           setOrderColumn={setOrderColumn}
                           setOrderDirection={setOrderDirection}
                           setFilters={setFilters} />

}
