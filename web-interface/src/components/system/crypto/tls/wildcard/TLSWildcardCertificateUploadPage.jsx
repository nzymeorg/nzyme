import React, {useState} from "react";
import Routes from "../../../../../util/ApiRoutes";
import TLSCertificateHelp from "../TLSCertificateHelp";
import TLSCertificateUploadForm from "../form/TLSCertificateUploadForm";
import CryptoService from "../../../../../services/CryptoService";
import {toast} from "react-toastify";
import {Link, Navigate} from "react-router-dom";
import MatchingNodesTestResult from "./MatchingNodesTestResult";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import usePageTitle from "../../../../../util/UsePageTitle";

const cryptoService = new CryptoService();

function TLSWildcardCertificateUploadPage() {

  usePageTitle("Upload TLS Wildcard Certificate");

  const [certInstallationSuccess, setCertInstallationSuccess] = useState(false)

  const [regexValue, setRegexValue] = useState(undefined);
  const [matchingNodesTestRunning, setMatchingNodesTestRunning] = useState(false);
  const [matchingNodes, setMatchingNodes] = useState(null)

  const testNodeMatcherButtonActive = function() {
    return regexValue && regexValue.length > 0;
  }

  const updateRegex = function(e) {
    setMatchingNodes(null);
    setRegexValue(e.target.value);
  }

  const testNodeMatcher = function() {
    setMatchingNodesTestRunning(true);

    cryptoService.testTLSWildcardCertificateNodeMatcher(regexValue, function(response) {
      setMatchingNodesTestRunning(false);
      setMatchingNodes(response.data);
    });
  }

  const nodeMatcherReady = function() {
    return regexValue && regexValue.length > 0;
  }

  const installCertificate = function(formData) {
    if (!confirm("Really install wildcard TLS certificate? This will replace the existing " +
        "certificate on all nodes that match the regular expression. Note that the nzyme HTTP server on those node will " +
        "restart and you will likely experience a brief loss of connection.")) {
      return;
    }

    formData.append("node_matcher", regexValue);

    cryptoService.installWildcardTLSCertificate(formData, function() {
      toast.success('TLS certificate installed.');
      setCertInstallationSuccess(true);
    })
  }

  if (certInstallationSuccess) {
    return <Navigate to={Routes.SYSTEM.CRYPTO.INDEX} />
  }

  return (
      <div>
        <div className="row">
          <div className="col-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={Routes.SYSTEM.CRYPTO.INDEX}>Crypto</Link></li>
                <li className="breadcrumb-item">TLS</li>
                <li className="breadcrumb-item active" aria-current="page">Upload TLS Wildcard Certificate</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-primary float-end" to={Routes.SYSTEM.CRYPTO.INDEX}>Back</Link>
          </div>

          <div className="col-12">
            <h1>
              Upload TLS Wildcard Certificate
            </h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Upload TLS Wildcard Certificate</h3>

                <p>
                  Use this form to upload a TLS wildcard certificate. Every node name that matches the regular
                  expression below will be automatically provisioned with the uploaded wildcard TLS certificate.
                  Wildcard certificates take precedence over individual certificates.
                </p>

                <TLSCertificateHelp />

                <div className="mb-3">
                  <label htmlFor="fu-certificate" className="form-label">
                    Node Matcher (Regular Expression)
                  </label>
                  <div className="input-group mb-3">
                    <input className="form-control form-control-sm" name="node_matcher"
                           onChange={updateRegex} type="text" />
                    <button className="btn btn-sm btn-secondary"
                            onClick={testNodeMatcher}
                            disabled={!testNodeMatcherButtonActive()}>
                      Test Regex
                    </button>
                  </div>

                  <LoadingSpinner show={matchingNodesTestRunning} />
                  <MatchingNodesTestResult matchingNodes={matchingNodes} />
                </div>

                <TLSCertificateUploadForm onInstall={installCertificate} externalTestButtonReady={nodeMatcherReady} />
              </div>
            </div>
          </div>
        </div>
      </div>
  )

}

export default TLSWildcardCertificateUploadPage;