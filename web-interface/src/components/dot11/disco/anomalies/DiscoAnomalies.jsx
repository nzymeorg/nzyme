import React, {useContext, useEffect, useState} from "react";
import Dot11Service from "../../../../services/Dot11Service";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import DiscoAnomaliesTable from "./DiscoAnomaliesTable";

const dot11Service = new Dot11Service();

export default function DiscoAnomalies({timeRange, filters, setFilters, revision, onLoaded, perPage = 25}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [anomalies, setAnomalies] = useState(null);

  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [page, setPage] = useState(1);

  // Callers pass new objects on every render.
  const filtersKey = JSON.stringify(filters);
  const tapsKey = Array.isArray(selectedTaps) ? selectedTaps.join(",") : selectedTaps;

  useEffect(() => {
    setPage(1);
  }, [organizationId, tenantId, timeRange, filtersKey, tapsKey, orderColumn, orderDirection]);

  useEffect(() => {
    let active = true;

    setAnomalies(null);
    dot11Service.findDiscoAnomalies(organizationId, tenantId, timeRange, filters, selectedTaps, orderColumn,
        orderDirection, perPage, (page - 1) * perPage, (response) => {
          if (active) {
            setAnomalies(response);

            if (onLoaded) {
              onLoaded(response);
            }
          }
        });

    return () => {
      active = false;
    }
  }, [organizationId, tenantId, timeRange, filtersKey, tapsKey, orderColumn, orderDirection, page, perPage, revision]);

  return <DiscoAnomaliesTable anomalies={anomalies}
                              page={page}
                              setPage={setPage}
                              perPage={perPage}
                              orderColumn={orderColumn}
                              orderDirection={orderDirection}
                              setOrderColumn={setOrderColumn}
                              setOrderDirection={setOrderDirection}
                              setFilters={setFilters} />

}
