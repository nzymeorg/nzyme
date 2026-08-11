const ApiRoutes = {
  DASHBOARD: '/',
  USERPROFILE: {
    PROFILE: '/profile',
    PASSWORD: '/profile/password'
  },
  SYSTEM: {
    VERSION: '/system/version',
    AUTHENTICATION: {
      MANAGEMENT: {
        INDEX: '/system/authentication',
        SETTINGS: '/system/authentication/settings',
        ORGANIZATIONS: {
          DETAILS: (organizationId) => `/system/authentication/organizations/show/${organizationId}`,
          TENANTS_PAGE: (organizationId) => `/system/authentication/organizations/show/${organizationId}/tenants`,
          ADMINS_PAGE: (organizationId) => `/system/authentication/organizations/show/${organizationId}/administrators`,
          EVENTS_PAGE: (organizationId) => `/system/authentication/organizations/show/${organizationId}/events`,
          DATABASE_PAGE: (organizationId) => `/system/authentication/organizations/show/${organizationId}/database`,
          QUOTAS_PAGE: (organizationId) => `/system/authentication/organizations/show/${organizationId}/quotas`,
          SUBSYSTEMS_PAGE: (organizationId) => `/system/authentication/organizations/show/${organizationId}/subsystems`,
          CREATE: '/system/authentication/organizations/create',
          EDIT: (organizationId) => `/system/authentication/organizations/show/${organizationId}/edit`,
          ADMINS: {
            CREATE: (organizationId) => `/system/authentication/organizations/show/${organizationId}/admins/create`,
            DETAILS: (organizationId, userId) => `/system/authentication/organizations/show/${organizationId}/admins/show/${userId}`,
            EDIT: (organizationId, userId) => `/system/authentication/organizations/show/${organizationId}/admins/show/${userId}/edit`,
          },
          EVENTS: {
            ACTIONS: {
              DETAILS: (organizationId, actionId) => `/system/authentication/organizations/show/${organizationId}/events/actions/show/${actionId}`,
              CREATE: (organizationId) => `/system/authentication/organizations/show/${organizationId}/events/actions/create`,
              EDIT: (organizationId, actionId) => `/system/authentication/organizations/show/${organizationId}/events/actions/show/${actionId}/edit`
            },
            SUBSCRIPTIONS: {
              DETAILS: (organizationId, eventTypeName) => `/system/authentication/organizations/show/${organizationId}/events/subscriptions/${eventTypeName}`
            }
          }
        },
        TENANTS: {
          DETAILS: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}`,
          TAPS_PAGE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/taps`,
          USERS_PAGE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/users`,
          LOCATIONS_PAGE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/locations`,
          INTEGRATIONS_PAGE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/integrations`,
          DATABASE_PAGE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/database`,
          QUOTAS_PAGE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/quotas`,
          SUBSYSTEMS_PAGE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/subsystems`,
          CREATE: (organizationId) => `/system/authentication/organizations/show/${organizationId}/tenants/create`,
          EDIT: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/edit`,
          LOCATIONS: {
            DETAILS: (organizationId, tenantId, locationId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/locations/show/${locationId}`,
            CREATE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/locations/create`,
            EDIT: (organizationId, tenantId, locationId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/locations/show/${locationId}/edit`,
            FLOORS: {
              DETAILS: (organizationId, tenantId, locationId, floorId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/locations/show/${locationId}/floors/show/${floorId}`,
              CREATE: (organizationId, tenantId, locationId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/locations/show/${locationId}/floors/create`,
              EDIT: (organizationId, tenantId, locationId, floorId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/locations/show/${locationId}/floors/show/${floorId}/edit`,
            }
          },
          INTEGRATIONS: {
          }
        },
        USERS: {
          CREATE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/users/create`,
          DETAILS: (organizationId, tenantId, userId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/users/show/${userId}`,
          EDIT: (organizationId, tenantId, userId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/users/show/${userId}/edit`,
        },
        TAPS: {
          CREATE: (organizationId, tenantId) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/taps/create`,
          DETAILS: (organizationId, tenantId, tapUuid) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/taps/show/${tapUuid}`,
          EDIT: (organizationId, tenantId, tapUuid) => `/system/authentication/organizations/show/${organizationId}/tenants/show/${tenantId}/taps/show/${tapUuid}/edit`,
        },
        SUPERADMINS: {
          CREATE: '/system/authentication/superadmins/create',
          DETAILS: (userId) => `/system/authentication/superadmins/show/${userId}`,
          EDIT: (userId) => `/system/authentication/superadmins/show/${userId}/edit`,
        },
      }
    },
    TAPS: {
      INDEX: '/system/taps',
      DETAILS: uuid => `/system/taps/show/${uuid}`,
      METRICDETAILS: (uuid, metricType, metricName) => `/system/taps/show/${uuid}/metrics/show/${metricType}/${metricName}`
    },
    CRYPTO: {
      INDEX: '/system/crypto',
      TLS: {
        CERTIFICATE: nodeUUID => `/system/crypto/tls/certificate/show/${nodeUUID}`,
        WILDCARD: {
          UPLOAD: '/system/crypto/tls/certificate/wildcard/upload',
          EDIT: (certificateId) => `/system/crypto/tls/certificate/wildcard/show/${certificateId}`
        }
      }
    },
    MONITORING: {
      INDEX: '/system/monitoring',
      PROMETHEUS: {
        INDEX: '/system/monitoring/prometheus'
      }
    },
    CLUSTER: {
      INDEX: '/system/cluster',
      MESSAGING: {
        INDEX: '/system/cluster/messaging'
      },
      NODES: {
        DETAILS: uuid => `/system/cluster/nodes/show/${uuid}`
      }
    },
    HEALTH: {
      INDEX: '/system/health'
    },
    DATABASE: {
      INDEX: '/system/database'
    },
    INTEGRATIONS: {
      INDEX: '/system/integrations',
      GEOIP: {
        IPINFO: '/system/integrations/geoip/ipinfo'
      }
    },
    EVENTS: {
      INDEX: '/system/events',
      ACTIONS: {
        DETAILS: actionId => `/system/events/actions/show/${actionId}`,
        CREATE: '/system/events/actions/create',
        EDIT: actionId => `/system/events/actions/show/${actionId}/edit`,
      },
      SUBSCRIPTIONS: {
        DETAILS: eventTypeName => `/system/events/subscriptions/show/${eventTypeName}`
      }
    },
    CONNECT: '/system/connect',
    LOOKANDFEEL: '/system/lookandfeel',
    SUBSYSTEMS: '/system/subsystems'
  },
  SEARCH: {
    RESULTS: '/search/results'
  },
  LOCATIONS: {
    INDEX: '/locations',
    DETAILS: uuid => `/locations/show/${uuid}`,
  },
  ETHERNET: {
    OVERVIEW: '/ethernet/overview',
    IP: {
      ADDRESS_DETAILS: address => `/ethernet/l4/ip/show/${address}`
    },
    L4: {
      OVERVIEW: '/ethernet/l4',
      TCP: {
        SESSION_DETAILS: (sessionKey, startTime) => `/ethernet/l4/tcp/${sessionKey}/${startTime}`
      },
      UDP: {
        SESSION_DETAILS: (sessionKey, startTime) => `/ethernet/l4/udp/${sessionKey}/${startTime}`
      }
    },
    ASSETS: {
      INDEX: '/ethernet/assets',
      DETAILS: uuid => `/ethernet/assets/${uuid}`,
      DHCP: {
        INDEX: '/ethernet/assets/dhcp',
        TRANSACTION_DETAILS: transactionId => `/ethernet/assets/dhcp/show/${transactionId}`
      },
      ARP: {
        INDEX: '/ethernet/assets/arp'
      },
      SETTINGS: '/ethernet/assets/settings'
    },
    DNS: {
      INDEX: '/ethernet/dns',
      TRANSACTION_LOGS: '/ethernet/dns/logs'
    },
    TUNNELS: {
      SOCKS: {
        INDEX: '/ethernet/tunnels/socks',
        TUNNEL_DETAILS: sessionId => `/ethernet/tunnels/socks/tunnels/show/${sessionId}`
      }
    },
    REMOTE: {
      SSH: {
        INDEX: '/ethernet/remoteaccess/ssh',
        SESSION_DETAILS: sessionId => `/ethernet/remoteaccess/ssh/sessions/show/${sessionId}`
      }
    },
    STREAMS: {
      RTSP: {
        INDEX: '/ethernet/streams/rtsp',
      }
    },
    TIME: {
      NTP: {
        INDEX: '/ethernet/time/ntp',
        TRANSACTION_DETAILS: (transactionId) => `/ethernet/time/ntp/transactions/show/${transactionId}`,
      },
      PTP: {
        INDEX: '/ethernet/time/ptp'
      }
    },
    NAT: {
      TRAVERSAL: {
        DISCOVERY: {
          INDEX: '/ethernet/nat/traversal/discovery'
        }
      }
    },
    BEACONS: {
      INDEX: '/ethernet/beacons'
    },
    RECON: {
      INDEX: '/ethernet/recon'
    }
  },
  DOT11: {
    OVERVIEW: '/dot11/overview',
    MONITORING: {
      INDEX: '/dot11/monitoring',
      CREATE: '/dot11/monitoring/ssids/create',
      SSID_DETAILS: (uuid) => `/dot11/monitoring/ssids/show/${uuid}`,
      CONFIGURATION_IMPORT: (uuid) => `/dot11/monitoring/ssids/show/${uuid}/configuration/import`,
      SIMILAR_SSID_CONFIGURATION: (uuid) => `/dot11/monitoring/ssids/show/${uuid}/configuration/similarssids`,
      RESTRICTED_SUBSTRINGS_CONFIGURATION: (uuid) => `/dot11/monitoring/ssids/show/${uuid}/configuration/restrictedsubstrings`,
      CLIENTS_CONFIGURATION: (uuid) => `/dot11/monitoring/ssids/show/${uuid}/configuration/clients`,
      BANDITS: {
        INDEX: "/dot11/monitoring/bandits",
        BUILTIN_DETAILS: (id) => `/dot11/monitoring/bandits/builtin/show/${id}`,
        CREATE: "/dot11/monitoring/bandits/custom/create",
        CUSTOM_DETAILS: (id) => `/dot11/monitoring/bandits/custom/show/${id}`,
        EDIT: (id) => `/dot11/monitoring/bandits/custom/show/${id}/edit`
      },
      DISCO: {
        CONFIGURATION: (uuid) => `/dot11/monitoring/ssids/show/${uuid}/disco/configuration`,
      },
      PROBE_REQUESTS: {
        INDEX: "/dot11/monitoring/proberequests",
        CREATE: "/dot11/monitoring/proberequests/create",
        EDIT: (id) => `/dot11/monitoring/proberequests/edit/${id}`
      },
      SSIDS: {
        INDEX: "/dot11/monitoring/ssids"
      },
      MONITORS: {
        INDEX: "/dot11/monitoring/monitors"
      }
    },
    NETWORKS: {
      BSSIDS: {
        INDEX: "/dot11/bssids",
        DETAILS: (bssid) => `/dot11/bssids/show/${bssid}`,
        TIMELIME: (bssid) => `/dot11/bssids/show/${bssid}/timeline`,
      },
      SSIDS: {
        DETAILS: (bssid, ssid, frequency) => `/dot11/bssids/show/${bssid}/ssids/show/${ssid}/frequencies/show/${frequency}`,
        TIMELINE: (bssid, ssid, frequency) => `/dot11/bssids/show/${bssid}/ssids/show/${ssid}/frequencies/show/${frequency}/timeline`,
      }
    },
    CLIENTS: {
      CONNECTED: '/dot11/clients/connected',
      DISCONNECTED: '/dot11/clients/disconnected',
      DETAILS: (mac) => `/dot11/clients/show/${mac}`
    },
    DISCO: {
      INDEX: '/dot11/disco'
    }
  },
  BLUETOOTH: {
    DEVICES: {
      INDEX: '/bluetooth/devices',
      DETAILS: (mac) => `/bluetooth/devices/show/${mac}`
    }
  },
  UAV: {
    INDEX: '/uavs/all',
    DETAILS: (identifier) => `/uavs/all/show/${identifier}`,
    MONITORING: {
      INDEX: '/uavs/monitoring'
    },
    TYPES: {
      INDEX: '/uavs/types',
      CREATE: "/uavs/types/create",
      EDIT: (uuid) => `/uavs/types/show/${uuid}/edit`,
    }
  },
  CONTEXT: {
    MAC_ADDRESSES: {
      INDEX: '/context/macs',
      SHOW: (uuid) => `/context/macs/show/${uuid}`,
      EDIT: (uuid) => `/context/macs/show/${uuid}/edit`,
      CREATE: '/context/macs/create'
    }
  },
  NOT_FOUND: '/notfound',
  ALERTS: {
    INDEX: '/alerts/overview',
    DETAILS: (uuid) => `/alerts/show/${uuid}`,
    SUBSCRIPTIONS: {
      INDEX: '/alerts/subscriptions',
      DETAILS: (detectionName) => `/alerts/subscriptions/types/show/${detectionName}`
    },
    MONITORS: {
      DETAILS: (id) => `/alerts/monitors/show/${id}`,
      EDIT: (id) => `/alerts/monitors/edit/${id}`,
    }
  }
}

export default ApiRoutes
