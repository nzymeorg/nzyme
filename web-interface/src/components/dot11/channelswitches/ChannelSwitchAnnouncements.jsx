import React, {useContext, useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import ChannelSwitchesTable from "./ChannelSwitchesTable";

const dot11Service = new Dot11Service();

export default function ChannelSwitchAnnouncements({timeRange, filters, setFilters, bssids, monitoredNetworkId,
                                                    simulatedBurstThreshold, taps, revision, emptyText,
                                                    perPage = 25}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = taps !== undefined ? taps : tapContext.taps;

  const [announcements, setAnnouncements] = useState(null);

  const [orderColumn, setOrderColumn] = useState("created_at");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [page, setPage] = useState(1);

  // Callers pass new arrays on every render.
  const bssidsKey = bssids ? bssids.join(",") : null;
  const tapsKey = Array.isArray(selectedTaps) ? selectedTaps.join(",") : selectedTaps;

  useEffect(() => {
    setPage(1);
  }, [timeRange, filters, tapsKey, bssidsKey, monitoredNetworkId, simulatedBurstThreshold, orderColumn, orderDirection]);

  useEffect(() => {
    let active = true;
    const onLoaded = (response) => {
      if (active) {
        setAnnouncements(response);
      }
    }

    setAnnouncements(null);
    if (simulatedBurstThreshold) {
      dot11Service.simulateChannelSwitchMonitor(monitoredNetworkId, simulatedBurstThreshold, timeRange,
          selectedTaps, orderColumn, orderDirection, perPage, (page - 1) * perPage, onLoaded);
    } else {
      dot11Service.findChannelSwitches(organizationId, tenantId, timeRange, filters, selectedTaps, bssids,
          monitoredNetworkId, orderColumn, orderDirection, perPage, (page - 1) * perPage, onLoaded);
    }

    return () => {
      active = false;
    }
  }, [organizationId, tenantId, timeRange, filters, tapsKey, bssidsKey, monitoredNetworkId,
    simulatedBurstThreshold, orderColumn, orderDirection, page, perPage, revision]);

  return <ChannelSwitchesTable announcements={announcements}
                               page={page}
                               setPage={setPage}
                               perPage={perPage}
                               orderColumn={orderColumn}
                               orderDirection={orderDirection}
                               setOrderColumn={setOrderColumn}
                               setOrderDirection={setOrderDirection}
                               setFilters={setFilters}
                               emptyText={emptyText} />

}
