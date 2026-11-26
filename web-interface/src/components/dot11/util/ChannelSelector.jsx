import React from "react";
import numeral from "numeral";
import {dot11FrequencyToChannel} from "../../../util/Tools";

function ChannelSelector(props) {

  const frequencies = props.frequencies.filter(f => f);
  const channels = props.channels ? props.channels : [];
  const currentFrequency = props.currentFrequency;
  const setFrequency = props.setFrequency;

  const declared = frequencies.filter(f => channels.includes(f));
  const others = frequencies.filter(f => !channels.includes(f));

  const options = (list) => list.map(function(frequency) {
    return (
        <option key={"csw-" + frequency} value={frequency}>
          Channel {dot11FrequencyToChannel(frequency)} ({numeral(frequency).format("0,0")} MHz)
        </option>
    )
  });

  return (
      <select className="form-select form-select-sm" style={{width: 215}}
              onChange={(e) => { setFrequency(parseInt(e.target.value, 10)); }}
              value={currentFrequency}>
        {declared.length === 0 ? options(others) : (
            <React.Fragment>
              <optgroup label="Declared Channels">{options(declared)}</optgroup>
              {others.length === 0 ? null : <optgroup label="Other Tap Frequencies">{options(others)}</optgroup>}
            </React.Fragment>
        )}
      </select>
  )

}

export const preferredFrequency = (frequencies, channels) => {
  const recorded = frequencies.filter(f => f);
  const declared = (channels ? channels : []).find(f => recorded.includes(f));

  return declared ? declared : (recorded.length > 0 ? recorded[0] : 0);
}

export default ChannelSelector;
