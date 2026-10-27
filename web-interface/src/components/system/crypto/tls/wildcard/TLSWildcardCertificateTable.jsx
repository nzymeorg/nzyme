import {Link} from "react-router-dom";
import React from "react";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../util/ApiRoutes";
import ExpirationDate from "../ExpirationDate";

function TLSWildcardCertificateTable(props) {

  const crypto = props.crypto

  if (!crypto) {
    return <LoadingSpinner />
  }

  const certificates = Object.values(crypto.tls_wildcard_certificates);

  if (certificates.length === 0) {
    return (
        <div className="alert alert-info mb-0">
          No wildcard TLS certificates installed.
        </div>
    )
  }

  return (
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Node Matcher</th>
          <th>Nodes</th>
          <th>Certificate Fingerprint</th>
          <th>Expires at</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {Object.keys(certificates.sort((a, b) => a.node_matcher.localeCompare(b.node_matcher))).map(function (key, i) {
          return (
              <tr key={'tlscert-' + i}>
                <td><code>{certificates[i].node_matcher}</code></td>
                <td>{certificates[i].matching_nodes.length}</td>
                <td>{certificates[i].fingerprint.substring(0, 16).match(/.{1,2}/g).join(' ').toUpperCase()}</td>
                <td>
                  <ExpirationDate date={certificates[i].expires_at} />
                </td>
                <td>
                  <Link to={ApiRoutes.SYSTEM.CRYPTO.TLS.WILDCARD.EDIT(certificates[i].id)}>
                    Manage
                  </Link>
                </td>
              </tr>
          )
        })}
        </tbody>
      </table>
  )

}

export default TLSWildcardCertificateTable;