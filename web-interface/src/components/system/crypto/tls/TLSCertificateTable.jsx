import {Link} from "react-router-dom";
import React from "react";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import TLSCertificateSourceType from "./TLSCertificateSourceType";
import ExpirationDate from "./ExpirationDate";

function TLSCertificateTable(props) {

  const crypto = props.crypto

  if (!crypto) {
    return <LoadingSpinner />
  }

  const certificates = Object.values(crypto.tls_certificates);

  return (
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Node</th>
          <th>Source/Type</th>
          <th>Certificate Fingerprint</th>
          <th>Expires at</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {Object.keys(certificates.sort((a, b) => a.node_name.localeCompare(b.node_name))).map(function (key, i) {
          return (
              <tr key={'tlscert-' + i}>
                <td>{certificates[i].node_name}</td>
                <td><TLSCertificateSourceType type={certificates[i].sourcetype} /></td>
                <td>{certificates[i].fingerprint.substring(0, 16).match(/.{1,2}/g).join(' ').toUpperCase()}</td>
                <td><ExpirationDate date={certificates[i].expires_at} /></td>
                <td>
                  <Link to={ApiRoutes.SYSTEM.CRYPTO.TLS.CERTIFICATE(certificates[i].node_id)}>Manage</Link>
                </td>
              </tr>
          )
        })}
        </tbody>
      </table>
  )

}

export default TLSCertificateTable;