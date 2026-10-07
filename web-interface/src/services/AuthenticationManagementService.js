import RESTClient from '../util/RESTClient'

class AuthenticationManagementService {

  createInitialUser(email, password, name, disableMfa, successCallback, errorCallback) {
    RESTClient.post('/system/authentication/mgmt/initialuser',
        {email: email, password: password, name: name, disable_mfa: disableMfa}, successCallback, errorCallback);
  }

  findAllOrganizations(setOrganizations, limit, offset, successCallback = undefined) {
    RESTClient.get('/system/authentication/mgmt/organizations', {limit: limit, offset: offset}, function (response) {
      setOrganizations(response.data);
      if (successCallback) {
        successCallback();
      }
    })
  }

  findOrganization(id, setOrganization) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + id, {}, function (response) {
      setOrganization(response.data)
    })
  }

  createOrganization(name, description, successCallback) {
    RESTClient.post('/system/authentication/mgmt/organizations',
        {name: name, description: description}, successCallback);
  }

  editOrganization(id, name, description, successCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + id,
        {name: name, description: description}, successCallback);
  }

  deleteOrganization(id, successCallback) {
    RESTClient.delete('/system/authentication/mgmt/organizations/show/' + id, successCallback);
  }

  getSubsystemsConfigurationOfOrganization(id, setConfiguration) {
    RESTClient.get(`/system/authentication/mgmt/organizations/show/${id}/subsystems/configuration`, {}, (response) => setConfiguration(response.data))
  }

  updateSubsystemsConfigurationOfOrganization(newConfig, id, successCallback, errorCallback) {
    RESTClient.put(`/system/authentication/mgmt/organizations/show/${id}/subsystems/configuration`, { change: newConfig }, successCallback, errorCallback)
  }

  getQuotasOfOrganization(id, setQuotas) {
    RESTClient.get(`/system/authentication/mgmt/organizations/show/${id}/quotas`, {}, (response) => setQuotas(response.data))
  }

  setQuotaOfOrganization(id, quotaType, quota, successCallback, errorCallback) {
    RESTClient.put(`/system/authentication/mgmt/organizations/show/${id}/quotas/show/${quotaType}`, { quota: quota }, successCallback, errorCallback)
  }

  findAllTenantsOfOrganization(organizationId, setTenants, limit, offset, successCallback = undefined) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants',
        {limit: limit, offset: offset}, function (response) {
      setTenants(response.data);
      if (successCallback) {
        successCallback();
      }
    })
  }

  findTenantOfOrganization(organizationId, tenantId, setTenant) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId, {}, function (response) {
      setTenant(response.data)
    })
  }

  createTenantOfOrganization(organizationId, name, description, sessionTimeoutMinutes, sessionInactivityTimeoutMinutes, mfaTimeoutMinutes, successCallback) {
    RESTClient.post('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants',
        {
          name: name,
          description: description,
          session_timeout_minutes: sessionTimeoutMinutes,
          session_inactivity_timeout_minutes: sessionInactivityTimeoutMinutes,
          mfa_timeout_minutes: mfaTimeoutMinutes
        }, successCallback);
  }

  editTenantOfOrganization(organizationId, tenantId, name, description, sessionTimeoutMinutes, sessionInactivityTimeoutMinutes, mfaTimeoutMinutes, successCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId,
        {
          name: name,
          description: description,
          session_timeout_minutes: sessionTimeoutMinutes,
          session_inactivity_timeout_minutes: sessionInactivityTimeoutMinutes,
          mfa_timeout_minutes: mfaTimeoutMinutes
        }, successCallback);
  }

  deleteTenantOfOrganization(organizationId, tenantId, successCallback) {
    RESTClient.delete('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId,
        successCallback);
  }

  getQuotasOfTenantOfOrganization(organizationId, tenantId, setQuotas) {
    RESTClient.get(`/system/authentication/mgmt/organizations/show/${organizationId}/tenants/show/${tenantId}/quotas`, {}, (response) => setQuotas(response.data))
  }

  setQuotaOfTenantOfOrganization(organizationId, tenantId, quotaType, quota, successCallback, errorCallback) {
    RESTClient.put(`/system/authentication/mgmt/organizations/show/${organizationId}/tenants/show/${tenantId}/quotas/show/${quotaType}`, { quota: quota }, successCallback, errorCallback)
  }

  getSubsystemsConfigurationOfTenantOfOrganization(organizationId, tenantId, setConfiguration) {
    RESTClient.get(`/system/authentication/mgmt/organizations/show/${organizationId}/tenants/show/${tenantId}/subsystems/configuration`, {}, (response) => setConfiguration(response.data))
  }

  updateSubsystemsConfigurationOfTenantOfOrganization(newConfig, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put(`/system/authentication/mgmt/organizations/show/${organizationId}/tenants/show/${tenantId}/subsystems/configuration`, {change: newConfig}, successCallback, errorCallback)
  }

  findAllOrganizationAdmins(organizationId, setUsers, limit, offset) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/administrators',
        {limit: limit, offset: offset}, function (response) {
          setUsers(response.data);
        })
  }

  createOrganizationAdministrator(organizationId, email, password, name, disableMfa, successCallback, errorCallback) {
    RESTClient.post('/system/authentication/mgmt/organizations/show/' + organizationId + '/administrators',
        {email: email, password: password, name: name, disable_mfa: disableMfa}, successCallback, errorCallback);
  }

  findOrganizationAdmin(organizationId, userId, setUser, setIsDeletable = undefined, setApiKeys = undefined) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/administrators/show/' + userId,
        {}, function (response) {
          setUser(response.data.user);

          if (setIsDeletable) {
            setIsDeletable(response.data.is_deletable);
          }

          if (setApiKeys) {
            setApiKeys(response.data.api_keys);
          }
        })
  }

  deleteOrganizationAdmin(organizationId, userId, successCallback) {
    RESTClient.delete('/system/authentication/mgmt/organizations/show/' + organizationId + '/administrators/show/' + userId,
        successCallback);
  }

  editOrganizationAdministrator(organizationId, userId, name, email, disableMfa, successCallback, errorCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + organizationId + '/administrators/show/' + userId,
        {name: name, email: email, disable_mfa: disableMfa}, successCallback, errorCallback);
  }

  editOrganizationAdministratorPassword(organizationId, userId, password, successCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + organizationId + '/administrators/show/' + userId + '/password',
        {password: password}, successCallback);
  }

  resetMFAOfOrganizationAdmin(organizationId, userId, successCallback) {
    RESTClient.post('/system/authentication/mgmt/organizations/show/' + organizationId + '/administrators/show/' + userId + '/mfa/reset',
        {}, successCallback);
  }

  createUserOfTenant(organizationId, tenantId, email, password, name, disableMfa, successCallback, errorCallback) {
    RESTClient.post('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/users',
        {email: email, password: password, name: name, disable_mfa: disableMfa}, successCallback, errorCallback);
  }

  findAllUsersOfTenant(organizationId, tenantId, setUsers, limit, offset) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/users',
        {limit: limit, offset: offset}, function (response) {
      setUsers(response.data)
    });
  }

  findUserOfTenant(organizationId, tenantId, userId, setUser, setIsDeletable = undefined, setApiKeys = undefined) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/users/show/' + userId,
        {}, function (response) {
          setUser(response.data.user)

          if (setIsDeletable) {
            setIsDeletable(response.data.is_deletable);
          }

        if (setApiKeys) {
          setApiKeys(response.data.api_keys);
        }
        });
  }

  editUserOfTenant(organizationId, tenantId, userId, name, email, disableMfa, successCallback, errorCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/users/show/' + userId,
        {name: name, email: email, disable_mfa: disableMfa}, successCallback, errorCallback);
  }

  editUserOfTenantTapPermissions(organizationId, tenantId, userId, allowAccessAllTenantTaps, taps, successCallback, errorCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/users/show/' + userId + '/taps',
        {allow_access_all_tenant_taps: allowAccessAllTenantTaps, taps: taps}, successCallback, errorCallback);
  }

  editUserOfTenantPermissions(organizationId, tenantId, userId, permissions, successCallback, errorCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/users/show/' + userId + '/permissions',
        {permissions: permissions}, successCallback, errorCallback);
  }

  editUserOfTenantPassword(organizationId, tenantId, userId, password, successCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/users/show/' + userId + '/password',
        {password: password}, successCallback);
  }

  deleteUserOfTenant(organizationId, tenantId, userId, successCallback) {
    RESTClient.delete('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/users/show/' + userId,
        successCallback);
  }

  resetMFAOfUserOfTenant(organizationId, tenantId, userId, successCallback) {
    RESTClient.post('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/users/show/' + userId + '/mfa/reset',
        {}, successCallback);
  }

  findAllSessions(setSessions, limit, offset) {
    RESTClient.get('/system/authentication/mgmt/organizations/sessions',
        {limit: limit, offset: offset}, function (response) {
          setSessions(response.data);
    })
  }

  findSessionsOfOrganization(setSessions, organizationId, limit, offset) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/sessions',
        {limit: limit, offset: offset}, function (response) {
          setSessions(response.data);
        })
  }

  findSessionsOfTenant(setSessions, organizationId, tenantId, limit, offset) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/sessions',
        {limit: limit, offset: offset}, function (response) {
          setSessions(response.data);
        })
  }

  invalidateSession(sessionId, successCallback) {
    RESTClient.delete('/system/authentication/mgmt/organizations/sessions/show/' + sessionId, successCallback)
  }

  findAllTapPermissions(organizationId, tenantId, setTaps, limit, offset) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/taps',
        {limit: limit, offset: offset}, function (response) {
      setTaps(response.data);
    })
  }

  findTapPermission(organizationId, tenantId, tapUuid, setTap) {
    RESTClient.get('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/taps/show/' + tapUuid,
        {}, function (response) {
          setTap(response.data);
    })
  }

  createTapPermission(organizationId, tenantId, name, description, location, floor, latitude, longitude, successCallback) {
    RESTClient.post('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/taps',
        {name: name, description: description, location: location, floor: floor, latitude: latitude, longitude: longitude}, successCallback);
  }

  deleteTapPermission(organizationId, tenantId, tapUuid, successCallback) {
    RESTClient.delete('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/taps/show/' + tapUuid,
        successCallback);
  }

  editTapPermission(organizationId, tenantId, tapUuid, name, description, location, floor, latitude, longitude, successCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/taps/show/' + tapUuid,
        {name: name, description: description, location: location, floor: floor, latitude: latitude, longitude: longitude}, successCallback);
  }

  cycleTapSecret(organizationId, tenantId, tapUuid, successCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/show/' + organizationId + '/tenants/show/' + tenantId + '/taps/show/' + tapUuid + '/secret/cycle', {},
        successCallback);
  }


  findAllTenantLocations(organizationId, tenantId, setLocations, limit, offset) {
    RESTClient.get("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations",
        {limit: limit, offset: offset}, (response) => {
      setLocations(response.data);
    })
  }

  findTenantLocation(locationId, organizationId, tenantId, setLocation) {
    RESTClient.get("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId,
        {}, (response) => {
          setLocation(response.data);
        })
  }

  createTenantLocation(organizationId, tenantId, name, description, latitude, longitude, successCallback) {
    RESTClient.post("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations",
        {name: name, description: description, latitude: latitude, longitude: longitude}, successCallback)
  }

  updateTenantLocation(organizationId, tenantId, locationId, name, description, latitude, longitude, successCallback) {
    RESTClient.put("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId,
        {name: name, description: description, latitude: latitude, longitude: longitude}, successCallback)
  }

  deleteTenantLocation(locationId, organizationId, tenantId, successCallback) {
    RESTClient.delete("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId, successCallback)
  }

  setEnvironmentalAlertEventingOfTenantLocation(locationId, organizationId, tenantId, enabled, successCallback) {
    RESTClient.put(`/system/authentication/mgmt/organizations/show/${organizationId}/tenants/show/${tenantId}/locations/show/${locationId}/environment/alerts/eventing/enabled/${enabled}`,
      {}, successCallback)
  }

  findAllFloorsOfTenantLocation(organizationId, tenantId, locationId, setFloors, limit, offset) {
    RESTClient.get("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors",
        {limit: limit, offset: offset}, (response) => {
          setFloors(response.data);
        })
  }

  findFloorOfTenantLocation(organizationId, tenantId, locationId, floorId, setFloor) {
    RESTClient.get("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors/show/" + floorId,
        {}, (response) => {
          setFloor(response.data);
        })
  }

  createFloorOfTenantLocation(organizationId, tenantId, locationId, number, name, pathLossExponent, successCallback, errorCallback) {
    RESTClient.post("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors",
        {number: number, name: name, path_loss_exponent: pathLossExponent}, successCallback, errorCallback)
  }

  editFloorOfTenantLocation(organizationId, tenantId, locationId, floorId, number, name, pathLossExponent, successCallback, errorCallback) {
    RESTClient.put("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors/show/" + floorId,
        {number: number, name: name, path_loss_exponent: pathLossExponent}, successCallback, errorCallback)
  }

  deleteFloorOfTenantLocation(organizationId, tenantId, locationId, floorId, successCallback) {
    RESTClient.delete("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors/show/" + floorId, successCallback)
  }

  findFloorPlan(organizationId, tenantId, locationId, floorId, setPlan) {
    RESTClient.get("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors/show/" + floorId + "/plan",
        {}, (response) => setPlan(response.data));
  }

  uploadFloorPlan(organizationId, tenantId, locationId, floorId, formData, successCallback, errorCallback) {
    RESTClient.postMultipart("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors/show/" + floorId + "/plan",
        formData, false, successCallback, errorCallback);
  }

  deleteFloorPlan(organizationId, tenantId, locationId, floorId, successCallback) {
    RESTClient.delete("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors/show/" + floorId + "/plan", successCallback);
  }

  placeTapOnFloorPlan(organizationId, tenantId, locationId, floorId, tapId, x, y, successCallback) {
    RESTClient.put("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors/show/" + floorId + "/plan/taps/show/" + tapId + "/coords",
        {x: x, y: y}, successCallback)
  }

  removeTapFromFloorPlan(organizationId, tenantId, locationId, floorId, tapId, successCallback) {
    RESTClient.delete("/system/authentication/mgmt/organizations/show/" + organizationId + "/tenants/show/" + tenantId + "/locations/show/" + locationId + "/floors/show/" + floorId + "/plan/taps/show/" + tapId, successCallback)
  }

  getGlobalAuthenticationConfiguration(setConfiguration) {
    RESTClient.get("/system/authentication/mgmt/organizations/global/configuration", {},
        (response) => {
      setConfiguration(response.data);
    });
  }

  setGlobalAuthenticationConfiguration (newConfig, successCallback, errorCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/global/configuration',
        { change: newConfig }, successCallback, errorCallback)
  }

  findAllSuperAdmins(setUsers, limit, offset) {
    RESTClient.get('/system/authentication/mgmt/organizations/superadmins',
        {limit: limit, offset: offset}, function (response) {
      setUsers(response.data);
    })
  }

  findSuperAdmin(userId, setUser, setIsDeletable = undefined, setApiKeys = undefined) {
    RESTClient.get('/system/authentication/mgmt/organizations/superadmins/show/' + userId,
        {}, function (response) {
      setUser(response.data.user);

      if (setIsDeletable) {
        setIsDeletable(response.data.is_deletable);
      }

      if (setApiKeys) {
        setApiKeys(response.data.api_keys);
      }
    })
  }

  createSuperAdministrator(email, password, name, disableMfa, successCallback, errorCallback) {
    RESTClient.post('/system/authentication/mgmt/organizations/superadmins',
        {email: email, password: password, name: name, disable_mfa: disableMfa}, successCallback, errorCallback);
  }

  editSuperAdministrator(userId, name, email, disableMfa, successCallback, errorCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/superadmins/show/' + userId,
        {name: name, email: email, disable_mfa: disableMfa}, successCallback, errorCallback);
  }

  editSuperAdministratorPassword(userId, password, successCallback) {
    RESTClient.put('/system/authentication/mgmt/organizations/superadmins/show/' + userId + '/password',
        {password: password}, successCallback);
  }

  deleteSuperAdmin(userId, successCallback) {
    RESTClient.delete('/system/authentication/mgmt/organizations/superadmins/show/' + userId,
        successCallback);
  }

  resetMFAOfSuperAdmin(userId, successCallback) {
    RESTClient.post('/system/authentication/mgmt/organizations/superadmins/show/' + userId + '/mfa/reset',
        {}, successCallback);
  }

  findAllExistingPermissions(setAllPermissions) {
    RESTClient.get('/system/authentication/mgmt/organizations/permissions/all', {},
        function (response) {
          setAllPermissions(response.data.permissions);
    })
  }

}

export default AuthenticationManagementService