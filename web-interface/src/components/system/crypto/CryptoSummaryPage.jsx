import {Link} from "react-router-dom";
import React, { useEffect, useState } from 'react'
import PGPKeyTable from './pgp/PGPKeyTable'
import PGPMetrics from './pgp/PGPMetrics'
import CryptoService from '../../../services/CryptoService'
import TLSCertificateTable from "./tls/TLSCertificateTable";
import TLSWildcardCertificateTable from "./tls/wildcard/TLSWildcardCertificateTable";
import ApiRoutes from "../../../util/ApiRoutes";
import PGPConfiguration from "./pgp/PGPConfiguration";
import usePageTitle from "../../../util/UsePageTitle";

const cryptoService = new CryptoService()

function CryptoSummaryPage () {

  usePageTitle("Keys & Certificates");

  const [crypto, setCrypto] = useState(null)
  const [pgpMetrics, setPGPMetrics] = useState(null)
  const [localRevision, setLocalRevision] = useState(0)

  useEffect(() => {
    setCrypto(null);
    cryptoService.getPGPSummary(setCrypto, setPGPMetrics);
  }, [localRevision])

  return (
        <div>
          <div className="row">
              <div className="col-md-12">
                  <h1>Keys &amp; Certificates</h1>
              </div>
          </div>

          <div className="row">
            <div className="col-md-6">

              <div className="row">
                  <div className="col-md-12">
                      <div className="card">
                          <div className="card-body">
                              <h3>PGP Keys</h3>

                              <p>
                                  The nzyme system will automatically generate PGP keys for you to encrypt sensitive
                                  information in the database. Learn more about PGP keys
                                  in the <a href="https://go.nzyme.org/crypto-pgp" target="_blank" rel="noreferrer">nzyme documentation</a>.
                              </p>

                              <PGPKeyTable crypto={crypto} />
                          </div>
                      </div>
                  </div>
              </div>

              <div className="row mt-3">
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <h3>PGP Configuration</h3>

                      <p>
                        The same PGP keys have to be deployed on all nzyme nodes in a cluster. Nzyme will automatically
                        pull the keys from other nodes when joining an existing cluster. The key exchange is encrypted
                        but you can still disable the automatic exchange here. You can also temporarily enable the
                        exchange when you are joining new nodes and then disable it again when you are done. If you
                        disable the key exchange, you must manually copy the keys or the new nzyme node will not
                        start up.
                      </p>

                      <PGPConfiguration crypto={crypto} setLocalRevision={setLocalRevision} />
                    </div>
                  </div>
                </div>
              </div>

              <div className="row mt-3">
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <h3>Wildcard TLS Certificates</h3>

                      <p>
                        Every node name that matches a regular expression below will be automatically provisioned with
                        the corresponding wildcard TLS certificate. Wildcard certificates take precedence over individual
                        certificates.
                      </p>

                      <TLSWildcardCertificateTable crypto={crypto} />

                      <div className="mt-3">
                        <Link to={ApiRoutes.SYSTEM.CRYPTO.TLS.WILDCARD.UPLOAD} className="btn btn-sm btn-primary">
                          Upload Wildcard Certificate
                        </Link>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="row mt-3">
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <h3>TLS Certificates in Use</h3>

                      <p>
                        Individual TLS certificates per node. By default, nzyme will generate a self-signed certificate for
                        you, but you can also upload your own. For auto-provisioning, please look at the wildcard
                        certificate configuration on this page.
                      </p>

                      <TLSCertificateTable crypto={crypto} />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="row">
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <h3 style={{display: "inline-block"}}>PGP Metrics</h3>

                      <PGPMetrics metrics={pgpMetrics} />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
  )
}

export default CryptoSummaryPage
