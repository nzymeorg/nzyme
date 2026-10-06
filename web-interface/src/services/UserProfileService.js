import RESTClient from '../util/RESTClient'

class UserProfileService {

  findOwnProfile(setProfile) {
    RESTClient.get('/user/profile', {}, function (response) {
      setProfile(response.data);
    })
  }

  changeOwnPassword(currentPassword, newPassword, successCallback, errorCallback) {
    RESTClient.put('/user/password',
        {current_password: currentPassword, new_password: newPassword}, successCallback, errorCallback)
  }

  findOwnMfaRecoveryCodes(setCodes) {
    RESTClient.get('/user/mfa/recoverycodes', {}, function (response) {
      setCodes(response.data);
    })
  }

  resetOwnMfa(successCallback) {
    RESTClient.put('/user/mfa/reset', {}, successCallback)
  }

  setOwnUnitSystem(unitSystem, successCallback) {
    RESTClient.put(`/user/unitsystem/${unitSystem}`, {}, successCallback)
  }

  findOwnApiKeys(setKeys) {
    RESTClient.get('/user/apikeys/', {}, function (response) {
      setKeys(response.data);
    })
  }

  createOwnApiKey(name, expiryDays, successCallback) {
    RESTClient.post(`/user/apikeys`, { name: name, expiry_days: expiryDays }, successCallback)
  }

  deleteOwnApiKey(keyId, successCallback) {
    RESTClient.delete(`/user/apikeys/show/${keyId}`, successCallback)
  }

}

export default UserProfileService
