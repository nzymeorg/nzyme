import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import Routes from "../../../../../util/ApiRoutes";
import CryptoService from "../../../../../services/CryptoService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import TLSCertificateDetails from "../TLSCertificateDetails";
import MatchingNodes from "./MatchingNodes";
import {toast} from "react-toastify";
import TLSCertificateHelp from "../TLSCertificateHelp";
import TLSCertificateUploadForm from "../form/TLSCertificateUploadForm";
import usePageTitle from "../../../../../util/UsePageTitle";

const cryptoService = new CryptoService();

const loadData = function(certificateId, setCertificate) {
  cryptoService.findTLSWildcardCertificate(certificateId, setCertificate)
}

function TLSWildcardCertificateEditPage(props) {

  usePageTitle("Edit TLS Wildcard Certificate");

  const { certificateId } = useParams()

  const [certificate, setCertificate] = useState(null);
  const [matchingNodes, setMatchingNodes] = useState(null);
  const [nodeMatcher, setNodeMatcher] = useState("");
  const [matchingNodesPreview, setMatchingNodesPreview] = useState(false);
  const [matchingNodesTestRunning, setMatchingNodesTestRunning] = useState(false);
  const [nodeMatcherUpdateRunning, setNodeMatcherUpdateRunning] = useState(false);
  const [replaceFinished, setReplaceFinished] = useState(false);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    loadData(certificateId, setCertificate)
  }, [setCertificate, certificateId])

  useEffect(() => {
    if (certificate) {
      setNodeMatcher(certificate.node_matcher)
      setMatchingNodes(certificate.matching_nodes)
    }
  }, [certificate])

  const testNodeMatcherButtonActive = function() {
    return nodeMatcher && nodeMatcher.length > 0;
  }

  const onNodeMatcherUpdate = function(e) {
    setNodeMatcher(e.target.value);
  }

  const testNodeMatcher = function() {
    setMatchingNodesTestRunning(true);

    cryptoService.testTLSWildcardCertificateNodeMatcher(nodeMatcher, function(response) {
      setMatchingNodesTestRunning(false);
      setMatchingNodesPreview(true);
      setMatchingNodes(response.data);
    });
  }

  const saveNodeMatcher = function() {
    setNodeMatcherUpdateRunning(true);

    cryptoService.updateTLSWildcardCertificateNodeMatcher(certificate.id, nodeMatcher, function() {
      toast.success('Node matcher updated.');
      setMatchingNodesPreview(false);
      loadData(certificateId, setCertificate);
      setNodeMatcherUpdateRunning(false);
    });
  }

  const replaceCertificate = function(formData) {
    if (!confirm("Really replace wildcard TLS certificate? This will replace the existing " +
        "certificate on all nodes that match the regular expression. Note that the nzyme HTTP server on those node will " +
        "restart and you will likely experience a brief loss of connection.")) {
      return;
    }

    cryptoService.replaceWildcardTLSCertificate(certificate.id, formData, function() {
      toast.success('TLS certificate replaced.');
      setReplaceFinished(true);
      loadData(certificateId, setCertificate);
    })
  }

  const deleteCertificate = function() {
    if (!confirm("Really delete TLS wildcard certificate?")) {
      return;
    }

    cryptoService.deleteTLSWildcardCertificate(certificate.id, function() {
      toast.success('TLS wildcard certificate deleted.');
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={Routes.SYSTEM.CRYPTO.INDEX} />
  }

  if (!certificate) {
    return <LoadingSpinner />
  }

  return (
      <div>
        <div className="row">
          <div className="col-md-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={Routes.SYSTEM.CRYPTO.INDEX}>Crypto</Link></li>
                <li className="breadcrumb-item">TLS</li>
                <li className="breadcrumb-item active" aria-current="page">Wildcard Certificate #{certificate.id}</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-2">
            <Link className="btn btn-primary float-end" to={Routes.SYSTEM.CRYPTO.INDEX}>Back</Link>
          </div>

          <div className="col-md-12">
            <h1>
              TLS Wildcard Certificate #{certificate.id}
            </h1>
          </div>
        </div>

        <div className="row mt-3">

          <div className="col-md-6">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Node Matcher</h3>

                    <p>
                      All nodes matching the configured regular expression below are using this TLS wildcard
                      certificate.
                    </p>

                    <div className="mb-3">
                      <label htmlFor="fu-certificate" className="form-label">
                        Node Matcher (Regular Expression)
                      </label>
                      <div className="input-group mb-3">
                        <input className="form-control form-control-sm" name="node_matcher"
                               value={nodeMatcher}
                               onChange={onNodeMatcherUpdate} type="text" />
                        <button className="btn btn-sm btn-secondary"
                                disabled={!testNodeMatcherButtonActive()}
                                onClick={testNodeMatcher}>
                          Test Regex
                        </button>
                        <button className="btn btn-sm btn-primary"
                                onClick={saveNodeMatcher}>
                          Save
                        </button>
                      </div>
                    </div>

                    <h4>
                      Matching Nodes {matchingNodesPreview
                      ? <span className="text-warning">Test Result - Don&apos;t forget to save Regex</span>
                      : null}
                    </h4>

                    <LoadingSpinner show={matchingNodesTestRunning || nodeMatcherUpdateRunning} />
                    <MatchingNodes nodes={matchingNodes} />
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Replace Certificate</h3>

                    <p>
                      Use this form to replace the underlying TLS certificate.
                    </p>

                    <TLSCertificateHelp />

                    <TLSCertificateUploadForm
                        onInstall={replaceCertificate}
                        externalFinishSignal={replaceFinished} />
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Delete Certificate</h3>

                    <p>
                      Nodes that currently use this wildcard certificate will fall back on the certificate load order
                      to find a new certificate to load. If there are no other certificates, nodes will generate and
                      install a new self-signed certificate.
                    </p>

                    <button className="btn btn-sm btn-danger" onClick={deleteCertificate}>Delete Certificate</button>
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
                    <h3>Certificate Information</h3>

                    <TLSCertificateDetails cert={certificate} />
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
      </div>
  )

}

export default TLSWildcardCertificateEditPage;