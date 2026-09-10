import React, {useContext, useEffect, useState} from "react";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {Presets} from "../../shared/timerange/TimeRange";
import DNSTransactionCountChart from "../dns/logs/widgets/DNSTransactionCountChart";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import DNSTransactionsTable from "../dns/logs/DNSTransactionsTable";
import ApiRoutes from "../../../util/ApiRoutes";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeUrl";

export default function AssetDetailsDNSTransactions(props) {

  const tapContext = useContext(TapContext);

  const asset = props.asset;

  const FILTERS = {
    "client_mac": [{
      field: "client_mac",
      operator: "equals",
      value: asset.mac.address,
    }]
  };

  const [timeRange, setTimeRange] =  useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "asset_d_dnstx"));
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
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="DNS Transactions"
                                       doNotPersistTimeRange={true}
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       urlKey="asset_d_dnstx"
                                       internalLink={ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS + "?filters=" + JSON.stringify(FILTERS)}
                                       refreshAction={onRefresh} />


                <DNSTransactionCountChart timeRange={timeRange}
                                          setTimeRange={setTimeRange}
                                          filters={FILTERS}
                                          urlKey="asset_d_dnstx"
                                          revision={revision} />

                <DNSTransactionsTable timeRange={timeRange}
                                      filters={FILTERS}
                                      perPage={10}
                                      revision={revision}/>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}