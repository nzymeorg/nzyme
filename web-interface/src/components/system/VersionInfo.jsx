import React from 'react'

class VersionInfo extends React.Component {
  render () {
    if (this.props.version.checks_version) {
      if (this.props.version.new_version_available) {
        return (
                    <div className="alert alert-warning mb-0">
                        <i className="fa fa-exclamation-circle" /> You are running an outdated version of Nzyme: {this.props.version.version}. Please upgrade if possible.
                    </div>
        )
      } else {
        return (
                    <div className="alert alert-success mb-0">
                        <i className="fa fa-check-circle" /> You are running the latest stable version of Nzyme: {this.props.version.version}.
                    </div>
        )
      }
    } else {
      return (
                <div className="alert alert-warning mb-0">
                    <i className="fa fa-exclamation-circle" /> Versionchecks are disabled in Nzyme configuration. Please check for new releases manually.
                    Your version of Nzyme: {this.props.version.version}.
                </div>
      )
    }
  }
}

export default VersionInfo
