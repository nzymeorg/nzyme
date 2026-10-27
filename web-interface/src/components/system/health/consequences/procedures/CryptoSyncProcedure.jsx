import {Link} from "react-router-dom";
import React from "react";
import SolutionCounter from "./layout/SolutionCounter";
import ApiRoutes from "../../../../../util/ApiRoutes";
import Conditional from "./layout/Conditional";

function CryptoSyncProcedure(props) {
  return (
      <ol className="consequence-solution-procedure">
        <li>
          <SolutionCounter counter="1" /> Identify irregular PGP keys on the {' '}
          <Link to={ApiRoutes.SYSTEM.CRYPTO.INDEX}>crypto overview</Link> page. Decide which PGP key is the
          correct one. It is most likely the oldest one.
        </li>
        <li>
          <SolutionCounter counter="2" /> <Conditional text="For each" /> node with an irregular PGP key:
        </li>
        <li className="consequence-solution-sublist">
          <ol>
            <li><SolutionCounter counter="2.1" /> Stop the <code>nzyme</code> service</li>
            <li><SolutionCounter counter="2.2" /> Delete the irregular PGP private and public keys. The location of
              the keys is configured in your <code>nzyme.conf</code> using the <code>crypto_directory</code> variable
            </li>
            <li><SolutionCounter counter="2.3" /> Copy the correct PGP private and public keys to the same location</li>
            <li><SolutionCounter counter="2.4" /> Start the <code>nzyme</code> service</li>
          </ol>
        </li>
        <li>
          <SolutionCounter counter="3" /> Use the <Link to={ApiRoutes.SYSTEM.CRYPTO.INDEX}>crypto overview</Link> page to
          confirm that all PGP keys are the same across the cluster now.
        </li>
        <li>
          <SolutionCounter counter="4" /> Monitor your <code>nzyme.log</code> for encryption or decryption errors.
        </li>
        <li>
          <SolutionCounter counter="5" /> Read more about this topic in the{' '}
          <a href="https://go.nzyme.org/crypto-pgp" target="_blank" rel="noopener">nzyme Crypto &amp; PGP documentation</a> to avoid
          issues in the future.
        </li>

      </ol>
  )
}

/*
        <li>
          <SolutionCounter counter="2" /> Monitor your <code>nzyme.log</code> for encryption or decryption errors.
        </li>
 */

export default CryptoSyncProcedure;