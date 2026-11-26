use crate::api::client::ApiClient;
use crate::api::endpoints::organizations;
use crate::api::error::ApiError;
use crate::apps::api::{exit_with_api_error, FG_RED, FG_YELLOW, RESET};
use crate::exit_codes::EX_PERMISSION_DENIED;

pub struct Scope {
    pub organization_id: String,
    pub organization_name: Option<String>,
    pub tenant_id: String,
    pub tenant_name: Option<String>,
}

impl Scope {
    pub fn organization_label(&self) -> &str {
        self.organization_name.as_deref().unwrap_or(&self.organization_id)
    }

    pub fn tenant_label(&self) -> &str {
        self.tenant_name.as_deref().unwrap_or(&self.tenant_id)
    }
}

/// Exits the process with a message if discovery is not permitted.
pub fn discover_scopes(client: &ApiClient, organization_id: Option<String>, tenant_id: Option<String>) -> Vec<Scope> {
    match (organization_id, tenant_id) {
        (Some(org), Some(tenant)) => vec![Scope {
            organization_id: org, organization_name: None, tenant_id: tenant, tenant_name: None
        }],

        (Some(org), None) => {
            eprintln!("{FG_YELLOW}[>] Discovering tenants of organization {}...{RESET}", org);
            match organizations::find_tenants(client, &org) {
                Ok(tenants) => tenants.into_iter().map(|t| Scope {
                    organization_id: org.clone(), organization_name: None, tenant_id: t.id, tenant_name: t.name
                }).collect(),
                Err(ApiError::Unauthorized) => {
                    eprintln!("{FG_RED}[x] ERROR:{RESET} Listing the tenants of an organization requires \
                        organization administrator permissions. Pass --tenant-id as well to run this for a single \
                        tenant.");
                    std::process::exit(EX_PERMISSION_DENIED);
                }
                Err(e) => exit_with_api_error(&e),
            }
        }

        (None, _) => {
            eprintln!("{FG_YELLOW}[>] Discovering all organizations and tenants...{RESET}");
            let orgs = match organizations::find_all(client) {
                Ok(orgs) => orgs,
                Err(ApiError::Unauthorized) => {
                    eprintln!("{FG_RED}[x] ERROR:{RESET} Listing all organizations requires super administrator \
                        permissions. Organization administrators pass --organization-id, all other users pass \
                        --organization-id and --tenant-id.");
                    std::process::exit(EX_PERMISSION_DENIED);
                }
                Err(e) => exit_with_api_error(&e),
            };

            let mut scopes = Vec::new();
            for org in orgs {
                let tenants = match organizations::find_tenants(client, &org.id) {
                    Ok(tenants) => tenants,
                    Err(e) => exit_with_api_error(&e),
                };

                for tenant in tenants {
                    scopes.push(Scope {
                        organization_id: org.id.clone(),
                        organization_name: org.name.clone(),
                        tenant_id: tenant.id,
                        tenant_name: tenant.name,
                    });
                }
            }

            scopes
        }
    }
}
