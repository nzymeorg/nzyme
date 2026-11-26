import React, {useState} from "react";
import {useNavigate} from "react-router-dom";
import validateMACAddressValid from "../../shared/filtering/validators/MACAddressValidator";
import {useFrameSignalInvestigation} from "./FrameSignalInvestigation";

export default function FrameSignalAddressForm({timeRange}) {

  const investigation = useFrameSignalInvestigation();

  const navigate = useNavigate();
  const [address, setAddress] = useState("");

  const entered = address.trim();

  const investigate = (e) => {
    e.preventDefault();

    if (validateMACAddressValid(entered)) {
      navigate(investigation.link([entered.toUpperCase()], timeRange));
    }
  }

  return (
      <form onSubmit={investigate}>
        <label htmlFor="signal-investigation-address" className="form-label">
          {investigation.addressLabel}
        </label>

        <div className="row g-2">
          <div className="col-md-4">
            <input type="text"
                   id="signal-investigation-address"
                   className="form-control"
                   placeholder="00:00:00:00:00:00"
                   autoComplete="off"
                   autoFocus={true}
                   value={address}
                   onChange={(e) => setAddress(e.target.value)} />
          </div>
          <div className="col-auto">
            <button type="submit" className="btn btn-primary" disabled={!validateMACAddressValid(entered)}>
              Investigate
            </button>
          </div>
        </div>
      </form>
  )

}
