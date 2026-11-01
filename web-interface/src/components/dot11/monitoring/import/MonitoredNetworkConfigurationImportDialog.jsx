import React, {useEffect, useState} from "react";
import Dot11Service from "../../../../services/Dot11Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Dot11MacAddress from "../../../shared/context/macs/Dot11MacAddress";

import numeral from "numeral";

const dot11Service = new Dot11Service();

function MonitoredNetworkConfigurationImportDialog(props) {

  const uuid = props.uuid;
  const onSubmit = props.onSubmit;

  const [previewImport, setPreviewImport] = useState(false);
  const [importData, setImportData] = useState(null);

  const [selectedBSSIDs, setSelectedBSSIDs] = useState([]);
  const [selectedChannels, setSelectedChannels] = useState([]);
  const [selectedSecSuites, setSelectedSecSuites] = useState([]);

  useEffect(() => {
    if (previewImport) {
      dot11Service.getMonitoredNetworkImportData(uuid, setImportData)
    }
  }, [uuid, previewImport]);

  useEffect(() => {
    if (importData) {
     importData.bssids.forEach((bssid) => {
       if (!bssid.exists) {
         setSelectedBSSIDs(prev => [...prev, bssid.bssid.address]);
       }
     });

      importData.channels.forEach((channel) => {
        if (!channel.exists) {
          setSelectedChannels(prev => [...prev, channel.channel]);
        }
      });

      importData.security_suites.forEach((ss) => {
        if (!ss.exists) {
          setSelectedSecSuites(prev => [...prev, ss.security_suite]);
        }
      });
    }
  }, [importData]);

  const formReady = () => {
    return selectedBSSIDs.length > 0 || selectedChannels.length > 0 || selectedSecSuites.length > 0
  }

  const submit = () => {
    const bssidsData = [];

    selectedBSSIDs.forEach((b) => {
      importData.bssids.forEach((ib) => {
        if (ib.bssid.address === b) {
          bssidsData.push({bssid: ib.bssid.address, fingerprints: ib.fingerprints.map((fp) => {return fp.fingerprint})})
        }
      });
    });

    dot11Service.writeMonitoredNetworkImportData(uuid, bssidsData, selectedChannels, selectedSecSuites, onSubmit);
  }

  const currentlyMonitored = (state) => {
    if (state) {
      return <span><i className="fa-regular fa-circle-check text-success"></i> Monitored</span>
    } else {
      return <span><i className="fa-regular fa-circle-xmark text-danger"></i> Not Monitored</span>
    }
  }

  const bssidIsSelected = (bssid) => {
    return selectedBSSIDs.includes(bssid);
  }

  const channelIsSelected = (channel) => {
    return selectedChannels.includes(channel);
  }

  const secSuiteIsSelected = (ss) => {
    return selectedSecSuites.includes(ss);
  }

  const onBSSIDSelection = (bssid) => {
    if (selectedBSSIDs.includes(bssid)) {
      setSelectedBSSIDs(selectedBSSIDs.filter(b => b !== bssid));
    } else {
      setSelectedBSSIDs(prev => [...prev, bssid]);
    }
  }

  const onChannelSelection = (channel) => {
    if (selectedChannels.includes(channel)) {
      setSelectedChannels(selectedChannels.filter(c => c !== channel));
    } else {
      setSelectedChannels(prev => [...prev, channel]);
    }
  }

  const onSecSuiteSelection = (ss) => {
    if (selectedSecSuites.includes(ss)) {
      setSelectedSecSuites(selectedSecSuites.filter(s => s !== ss));
    } else {
      setSelectedSecSuites(prev => [...prev, ss]);
    }
  }

  const selectAll = (target) => {
    switch (target) {
      case "bssids":
        setSelectedBSSIDs([]);
        importData.bssids.forEach((ib) => {
          setSelectedBSSIDs(prev => [...prev, ib.bssid.address]);
        })
        break;
      case "channels":
        setSelectedChannels([]);
        importData.channels.forEach((ic) => {
          setSelectedChannels(prev => [...prev, ic.channel])
        })
        break;
      case "secsuites":
        setSelectedSecSuites([]);
        importData.security_suites.forEach((iss) => {
          setSelectedSecSuites(prev => [...prev, iss.security_suite])
        })
        break;
    }
  }

  const unselectAll = (target) => {
    switch (target) {
      case "bssids":
        setSelectedBSSIDs([]);
        break;
      case "channels":
        setSelectedChannels([]);
        break;
      case "secsuites":
        setSelectedSecSuites([]);
        break;
    }
  }

  const noDataWarning = (data) => {
    if (!data || data.length === 0) {
      return <div className="alert alert-info">
        No data was located for the monitored network. Ensure that access points broadcasting this network are present
        and within the range of the Nzyme taps accessible to your user.
      </div>
    }
  }

  if (!previewImport) {
    return (
        <button type="button" className="btn btn-primary btn-sm" onClick={() => setPreviewImport(true)}>
          Preview Import
        </button>
    )
  }

  if (previewImport && !importData) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <h4>BSSIDs</h4>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th style={{width: 55}}>Import</th>
            <th style={{width: 145}}>Currently Monitored</th>
            <th>BSSID</th>
            <th>OUI</th>
            <th>Fingerprints</th>
          </tr>
          </thead>
          <tbody>
          {importData.bssids.map((bssid, i) => {
            return (
                <tr key={i}>
                  <td style={{textAlign: "center"}}>
                    <input className="form-check-input"
                           type="checkbox"
                           checked={bssidIsSelected(bssid.bssid.address)}
                           onChange={() => onBSSIDSelection(bssid.bssid.address)}/>
                  </td>
                  <td>{currentlyMonitored(bssid.exists)}</td>
                  <td><Dot11MacAddress addressWithContext={bssid.bssid}/></td>
                  <td>{bssid.bssid.oui ? bssid.bssid.oui : "Unknown"}</td>
                  <td>
                    {bssid.fingerprints.length}
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>

        {noDataWarning(importData.bssids)}

        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => selectAll("bssids")}>
          Select All BSSIDs
        </button>{' '}
        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => unselectAll("bssids")}>
          Unselect All BSSIDs
        </button>

        <h4 className="mt-4">Channels</h4>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th style={{width: 55}}>Import</th>
            <th style={{width: 145}}>Currently Monitored</th>
            <th>Frequency</th>
          </tr>
          </thead>
          <tbody>
          {importData.channels.map((channel, i) => {
            return (
                <tr key={i}>
                  <td style={{textAlign: "center"}}>
                    <input className="form-check-input"
                           type="checkbox"
                           checked={channelIsSelected(channel.channel)}
                           onChange={() => onChannelSelection(channel.channel)}/>
                  </td>
                  <td>{currentlyMonitored(channel.exists)}</td>
                  <td>{numeral(channel.channel).format("0,0")} MHz</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        {noDataWarning(importData.channels)}

        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => selectAll("channels")}>
          Select All Channels
        </button>{' '}
        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => unselectAll("channels")}>
          Unselect All Channels
        </button>

        <h4 className="mt-4">Security Suites</h4>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th style={{width: 55}}>Import</th>
            <th style={{width: 145}}>Currently Monitored</th>
            <th>Security Suite</th>
          </tr>
          </thead>
          <tbody>
          {importData.security_suites.map((ss, i) => {
            return (
                <tr key={i}>
                  <td style={{textAlign: "center"}}>
                    <input className="form-check-input"
                           type="checkbox"
                           checked={secSuiteIsSelected(ss.security_suite)}
                           onChange={() => onSecSuiteSelection(ss.security_suite)}/>
                  </td>
                  <td>{currentlyMonitored(ss.exists)}</td>
                  <td>{ss.security_suite}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        {noDataWarning(importData.security_suites)}

        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => selectAll("secsuites")}>
          Select All Security Suites
        </button>{' '}
        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => unselectAll("secsuites")}>
          Unselect All Security Suites
        </button>

        <div className="mt-4">
          <button type="button" className="btn btn-primary" onClick={submit} disabled={!formReady()}>
            Perform Data Import
          </button>
        </div>
      </React.Fragment>
  )

}

export default MonitoredNetworkConfigurationImportDialog;