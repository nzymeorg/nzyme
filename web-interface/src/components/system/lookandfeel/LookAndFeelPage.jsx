import React from "react";
import SidebarTitleForm from "./SidebarTitleForm";
import LoginPageVideoForm from "./LoginPageVideoForm";
import usePageTitle from "../../../util/UsePageTitle";

export default function LookAndFeelPage(props) {

  usePageTitle("Look & Feel");

  const onSettingsUpdated = props.onSettingsUpdated;

  return (
      <div>
        <div className="row">
          <div className="col-md-12">
            <h1>Look &amp; Feel</h1>
          </div>
        </div>

        <div className="alert alert-warning mt-1">
          <p>
            Remember that Nzyme is licensed under the <a href="https://go.nzyme.org/license">Server Side Public
            License</a> (SSPL), which <strong>does not permit making the functionality of the program available to
            third parties as a service</strong> without meeting certain conditions.
          </p>

          <p>
            You will likely need a commercial Nzyme license if you are, for example, a managed service or cloud provider
            offering Nzyme as a service to third parties.
          </p>

          <p className="mb-0">
            Please <a href="https://go.nzyme.org/contact">contact us</a> if you have any questions.
          </p>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-8">
            <div className="card">
              <div className="card-body">
                <h3>Custom Sidebar Title</h3>

                <p>You can change the sidebar title and subtitle to any string you wish.</p>

                <SidebarTitleForm onSettingsUpdated={onSettingsUpdated} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-8">
            <div className="card">
              <div className="card-body">
                <h3>Custom Login Page Image</h3>

                <p>You can replace the login page video with your own static image.</p>

                <LoginPageVideoForm />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-8">
            <div className="card">
              <div className="card-body">
                <h3>Custom Browser Icon and Title</h3>

                You can configure a custom browser icon and title in your Nzyme node configuration files. These settings
                are not modifiable via the web interface due to the way Nzyme processes assets and its fundamental HTML template.
              </div>
            </div>
          </div>
        </div>

      </div>
  )

}