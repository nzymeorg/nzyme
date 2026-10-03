import Store from './Store'
import {toast} from "react-toastify";

const axios = require('axios')

const axiosInstance = axios.create({
  paramsSerializer: {
    serialize: (params) => {
      return Object.entries(params)
        .filter(([, value]) => value !== undefined && value !== null)
        .map(([key, value]) => {
          const v = typeof value === 'object' ? JSON.stringify(value) : value;
          return `${encodeURIComponent(key)}=${encodeURIComponent(v)}`;
        })
        .join('&');
    }
  }
});

const RESTClient = {

  getAuthHeaders () {
    const headers = {}

    // Add API token authorization if we have one.
    if (Store.get('sessionid') !== undefined) {
      headers.Authorization = 'Bearer ' + Store.get('sessionid')
    }

    return headers
  },

  buildUri (uri) {
    let stableRoot = window.appConfig.nzymeApiUri
    if (window.appConfig.nzymeApiUri.slice(-1) !== '/') {
      stableRoot = window.appConfig.nzymeApiUri + '/'
    }

    let stableUri = uri
    if (uri.charAt(0) === '/') {
      stableUri = uri.substr(1)
    }

    return stableRoot + stableUri
  },

  get (uri, params, successCallback, errorCallback = undefined) {
    axiosInstance.get(this.buildUri(uri), { params: params, headers: this.getAuthHeaders() })
      .then(function (response) {
        successCallback(response)
      })
      .catch(function (error) {
        if (error.response) {
          if (errorCallback) {
            errorCallback(error)
          } else {
            toast.error('REST call failed. (HTTP ' + error.response.status + ')')
          }
        } else {
          toast.error('REST call failed. No response. Is Nzyme running?')
        }
      })
  },

  post (uri, data, successCallback, errorCallback = undefined) {
    axiosInstance.post(this.buildUri(uri), data, { headers: this.getAuthHeaders() })
      .then(function (response) {
        successCallback(response)
      })
      .catch(function (error) {
        if (errorCallback) {
          errorCallback(error)
          return;
        }

        if (error.response) {
          if (error.response.status === 422) {
            toast.error('Could not create entity. Quota exceeded. Please contact your administrator.')
          } else {
            toast.error('REST call failed. (HTTP ' + error.response.status + ')')
          }
        } else {
          toast.error('REST call failed. No response. Is Nzyme running?')
        }
      })
  },

  postMultipart (uri, formData, standardErrorHandling, successCallback, errorCallback = undefined) {
    const headers = this.getAuthHeaders();
    headers["Content-Type"] = "multipart/form-data";

    axiosInstance.post(this.buildUri(uri), formData, { headers: headers })
      .then(function (response) {
        successCallback(response)
      })
      .catch(function (error) {
        if (errorCallback) {
          errorCallback(error)
        }

        if (standardErrorHandling) {
          if (error.response) {
            toast.error('REST call failed. (HTTP ' + error.response.status + ')')
          } else {
            toast.error('REST call failed. No response. Is Nzyme running?')
          }
        }
      })
  },

  put (uri, data, successCallback, errorCallback = undefined) {
    axiosInstance.put(this.buildUri(uri), data, { headers: this.getAuthHeaders() })
      .then(function (response) {
        successCallback(response)
      })
      .catch(function (error) {
        if (errorCallback) {
          errorCallback(error)
        } else {
          if (error.response) {
            toast.error('REST call failed. (HTTP ' + error.response.status + ')')
          } else {
            toast.error('REST call failed. No response. Is Nzyme running?')
          }
        }
      })
  },

  delete(uri, successCallback, errorCallback = undefined) {
    axiosInstance.delete(this.buildUri(uri), { headers: this.getAuthHeaders() })
      .then(function (response) {
        successCallback(response)
      })
      .catch(function (error) {
        if (errorCallback) {
          errorCallback(error)
        } else {
          if (error.response) {
            toast.error('REST call failed. (HTTP ' + error.response.status + ')')
          } else {
            toast.error('REST call failed. No response. Is Nzyme running?')
          }
        }
      })
  }

}

export default RESTClient