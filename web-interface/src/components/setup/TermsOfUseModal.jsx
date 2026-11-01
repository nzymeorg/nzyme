import React from 'react';

export default function TermsOfUseModal() {

  return (
    <>
      <p style={{fontWeight: "bold"}}>
        By continuing, you agree to the{' '}
        <a href="#" data-bs-toggle="modal" data-bs-target="#termsOfUseModal">
          Open Source License and Disclaimer Acknowledgement
        </a>
      </p>


      <div className="modal fade" id="termsOfUseModal">
        <div className="modal-dialog">
          <div className="modal-content">
            <div className="modal-body">
              <p>
                Use of the Nzyme system (the “Nzyme Program”) is made available by [nzyme LLC] (“Nzyme”) on an open
                source basis pursuant to the Server Side Public License available here:
                https://www.mongodb.com/legal/licensing/server-side-public-license (the “License”).
              </p>

              <p>
                This License is not a sale and does not convey to you any rights of ownership in or related to the Nzyme
                Program. Nzyme reserves all rights not expressly granted in the License. All trademarks, service marks,
                logos, slogans and taglines of Nzyme are the property of Nzyme and nothing contained herein should be
                construed as granting any license or right to use any trademarks, service marks, logos, slogans or
                taglines of Nzyme without its express written permission.
              </p>

              <p style={{fontWeight: "bold"}}>
                PLEASE ALSO READ THE FOLLOWING ADDITIONAL DISCLAIMERS AS THEY GOVERN YOUR ACCESS TO AND USE OF THE
                NZYME PROGRAM. PLEASE DO NOT USE THE NZYME PROGRAM IF YOU DO NOT AGREE TO THE LICENSE AND THE ADDITIONAL
                DISCLAIMERS. BY ACCESSING, DOWNLOADING AND/OR USING THE NZYME PROGRAM: (1) YOU REPRESENT YOU HAVE READ
                AND UNDERSTAND THE LICENSE AND THE FOLLOWING DISCLAIMERS; AND (2) YOU ACKNOWLEDGE, ACCEPT AND AGREE
                TO THE LICENSE AND THESE ADDITIONAL DISCLAIMERS.
              </p>

              <p>
                NZYME MAKES NO, AND DISCLAIMS ALL, REPRESENTATIONS OR WARRANTIES OF ANY KIND, WHETHER EXPRESS, STATUTORY
                OR IMPLIED, WITH RESPECT TO THE NZYME PROGRAM AND/OR YOUR USE OF THE NZYME PROGRAM, INCLUDING WITHOUT
                LIMITATION, ANY WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT,
                OR OF ANY WARRANTIES ARISING OUT OF CUSTOM, DEALING, TRADE OR USAGE. NZYME DOES NOT AND CANNOT REPRESENT
                OR WARRANT THAT THE NZYME PROGRAM WILL ELIMINATE OR PREVENT OCCURRENCES OF SECURITY EVENTS OR THREATS
                IT IS INTENDED TO DETECT (INCLUDING, BUT NOT LIMITED TO, ROGUE ACCESS POINTS OR OTHER UNAUTHORIZED OR
                CRIMINAL ACTIVITIES) (COLLECTIVELY, “SECURITY INCIDENTS”) OR PREVENT SECURITY INCIDENTS FROM CAUSING
                HARM OR DAMAGE TO YOUR PREMISES, NETWORKS, SYSTEMS, EMPLOYEES, VISITORS, OR OTHER THIRD PARTIES).
              </p>

              <p>
                THE NZYME PROGRAM PROVIDES ALERTS CONCERNING POTENTIAL INCIDENTS BUT IT DOES NOT BLOCK, JAM OR ACTIVELY
                INTERVENE TO STOP A SECURITY INCIDENT. NZYME MAKES NO GUARANTY OR WARRANTY THAT THE NZYME PROGRAM
                WILL DETECT, MITIGATE, ELIMINATE, PREVENT OR AVERT SECURITY INCIDENTS OR THEIR CONSEQUENCES OR THAT
                THE NZYME PROGRAM MAY NOT BE COMPROMISED OR CIRCUMVENTED. YOU ACKNOWLEDGE, ACCEPT AND AGREE THAT NZYME
                AND ITS OFFICERS, DIRECTORS, EMPLOYEES AND AGENTS SHALL NOT BE HELD LIABLE FOR ANY FAILURE TO DETECT,
                ELIMINATE, PREVENT, OR MITIGATE SECURITY INCIDENTS, IN WHOLE OR IN PART. YOU SHALL BE SOLELY RESPONSIBLE
                FOR THE ACTS OR OMISSIONS OF YOUR PERSONNEL, CONTRACTORS, AND AGENTS, INCLUDING THOSE RESPONSIBLE FOR
                OPERATING THE NZYME PROGRAM AND FOR THE SECURITY OF YOUR PREMISES, PERSONNEL, AND VISITORS.
              </p>

              <p>
                NZYME DOES NOT OBTAIN OR HAVE ACCESS TO ANY INFORMATION EXCHANGED AMONG PARTIES OVER WIRELESS
                CONNECTIONS WHEN USING THE NZYME PROGRAM. NZYME IS NOT RESPONSIBLE FOR ANY LOSS OF YOUR DATA.
                NZYME ALSO DOES NOT REPRESENT OR WARRANT THAT THE NZYME PROGRAM WILL OPERATE UNINTERRUPTED OR ERROR
                FREE, OR THAT THE NZYME PROGRAM WILL BE FREE FROM ERRORS OR DEFECTS, OR THAT ANY ERRORS WILL BE
                CORRECTED.
              </p>

              <p>
                NZYME CANNOT CONTROL HOW THE NZYME PROGRAM IS USED AND DOES NOT WARRANT OR REPRESENT, EXPRESSLY OR
                IMPLICITLY, THAT USE OF THE NZYME PROGRAM WILL COMPLY WITH OR CONFORM TO THE REQUIREMENTS OF FEDERAL,
                STATE, OR LOCAL STATUTES. YOU ARE SOLELY RESPONSIBLE FOR USING THE NZYME PROGRAM IN FULL COMPLIANCE
                WITH APPLICABLE LAW, GOVERNMENTAL RULES AND REGULATIONS, AND THE RIGHTS OF THIRD PERSONS.
              </p>
            </div>
            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            </div>
          </div>
        </div>
      </div>
    </>
  )

}