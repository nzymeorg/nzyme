//! Organization and tenant scope shared by all views. The scope is stored by id so it can be persisted with a
//! profile. Discovery walks the organization and tenant endpoints, which an API key can only do with super
//! administrator permissions, because the API offers no way for a key to look up its own organization and tenant.

use crate::api::client::ApiClient;
use crate::api::endpoints::organizations;
use crate::api::error::ApiError;

#[derive(Clone, Debug, PartialEq)]
pub struct TenantNode {
    pub id: String,
    pub name: String,
}

#[derive(Clone, Debug, PartialEq)]
pub struct OrganizationNode {
    pub id: String,
    pub name: String,
    pub tenants: Vec<TenantNode>,
}

#[derive(Clone)]
pub enum ScopeTree {
    Pending,
    Available(Vec<OrganizationNode>),
    Unavailable(String),
}

impl ScopeTree {
    pub fn discover(client: &ApiClient) -> ScopeTree {
        match discover(client) {
            Ok(orgs) => ScopeTree::Available(orgs),
            Err(ApiError::Unauthorized) => ScopeTree::Unavailable(
                "Listing organizations and tenants requires super administrator permissions. The API offers no \
                way for an API key to look up its own organization and tenant, so tenant-scoped views cannot load \
                data for this key yet.".to_string()
            ),
            Err(e) => ScopeTree::Unavailable(format!("Could not list organizations and tenants: {}", e)),
        }
    }
}

fn discover(client: &ApiClient) -> Result<Vec<OrganizationNode>, ApiError> {
    let mut result = Vec::new();

    for org in organizations::find_all(client)? {
        let tenants = organizations::find_tenants(client, &org.id)?
            .into_iter()
            .map(|t| TenantNode { name: t.name.unwrap_or_else(|| t.id.clone()), id: t.id })
            .collect();

        result.push(OrganizationNode {
            name: org.name.clone().unwrap_or_else(|| org.id.clone()),
            id: org.id,
            tenants,
        });
    }

    Ok(result)
}

/// The selected scope by id. `None` means all.
#[derive(Clone, Debug, PartialEq, Default)]
pub struct Scope {
    pub organization_id: Option<String>,
    pub tenant_id: Option<String>,
}

/// One tenant covered by a scope.
#[derive(Clone, Debug, PartialEq)]
pub struct ScopePair {
    pub organization_id: String,
    pub tenant_id: String,
    pub tenant_name: String,
}

impl Scope {
    pub fn all() -> Self {
        Scope::default()
    }

    pub fn organization(id: &str) -> Self {
        Scope { organization_id: Some(id.to_string()), tenant_id: None }
    }

    pub fn tenant(organization_id: &str, tenant_id: &str) -> Self {
        Scope { organization_id: Some(organization_id.to_string()), tenant_id: Some(tenant_id.to_string()) }
    }

    /// All tenants covered by the scope. A selected id that no longer exists in the tree yields nothing.
    pub fn pairs(&self, tree: &[OrganizationNode]) -> Vec<ScopePair> {
        let mut pairs = Vec::new();

        for org in tree {
            if self.organization_id.as_deref().is_some_and(|id| id != org.id) {
                continue;
            }
            for tenant in &org.tenants {
                if self.organization_id.is_some() && self.tenant_id.as_deref().is_some_and(|id| id != tenant.id) {
                    continue;
                }
                pairs.push(ScopePair {
                    organization_id: org.id.clone(),
                    tenant_id: tenant.id.clone(),
                    tenant_name: tenant.name.clone(),
                });
            }
        }

        pairs
    }

    /// Short label for headers: "All tenants", "Org 1 / all tenants" or "Org 1 / T2".
    pub fn label(&self, tree: &ScopeTree) -> String {
        let ScopeTree::Available(orgs) = tree else {
            return match tree {
                ScopeTree::Pending => "discovering...".to_string(),
                _ => "unavailable".to_string(),
            };
        };

        let Some(org_id) = &self.organization_id else {
            let tenants: usize = orgs.iter().map(|o| o.tenants.len()).sum();
            return format!("All tenants ({})", tenants);
        };

        let Some(org) = orgs.iter().find(|o| &o.id == org_id) else {
            return "unknown organization".to_string();
        };

        match &self.tenant_id {
            None => format!("{} / all tenants ({})", org.name, org.tenants.len()),
            Some(tenant_id) => match org.tenants.iter().find(|t| &t.id == tenant_id) {
                Some(t) => format!("{} / {}", org.name, t.name),
                None => format!("{} / unknown tenant", org.name),
            },
        }
    }

    /// True if the selected ids still exist in the tree.
    pub fn is_valid(&self, orgs: &[OrganizationNode]) -> bool {
        match (&self.organization_id, &self.tenant_id) {
            (None, _) => true,
            (Some(o), None) => orgs.iter().any(|org| &org.id == o),
            (Some(o), Some(t)) => orgs.iter().any(|org| &org.id == o && org.tenants.iter().any(|tenant| &tenant.id == t)),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tree() -> Vec<OrganizationNode> {
        vec![
            OrganizationNode { id: "o1".into(), name: "Org 1".into(), tenants: vec![
                TenantNode { id: "t1".into(), name: "T1".into() },
                TenantNode { id: "t2".into(), name: "T2".into() },
            ]},
            OrganizationNode { id: "o2".into(), name: "Org 2".into(), tenants: vec![
                TenantNode { id: "t3".into(), name: "T3".into() },
            ]},
        ]
    }

    #[test]
    fn all_scope_covers_everything() {
        let pairs = Scope::all().pairs(&tree());
        assert_eq!(pairs.len(), 3);
        assert_eq!(pairs[2].tenant_name, "T3");
    }

    #[test]
    fn narrows_by_organization_and_tenant() {
        assert_eq!(Scope::organization("o1").pairs(&tree()).len(), 2);

        let single = Scope::tenant("o1", "t2").pairs(&tree());
        assert_eq!(single.len(), 1);
        assert_eq!(single[0].tenant_id, "t2");

        assert!(Scope::tenant("o1", "missing").pairs(&tree()).is_empty());
        assert!(!Scope::tenant("o1", "missing").is_valid(&tree()));
        assert!(Scope::tenant("o2", "t3").is_valid(&tree()));
    }

    #[test]
    fn labels() {
        let t = ScopeTree::Available(tree());
        assert_eq!(Scope::all().label(&t), "All tenants (3)");
        assert_eq!(Scope::organization("o2").label(&t), "Org 2 / all tenants (1)");
        assert_eq!(Scope::tenant("o1", "t1").label(&t), "Org 1 / T1");
        assert_eq!(Scope::all().label(&ScopeTree::Pending), "discovering...");
    }
}
