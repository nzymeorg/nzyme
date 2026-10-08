//! Dashboard preferences and the shared context handed to every view. Preferences are selected in the shell's
//! dedicated selectors, apply to all views, and are persisted into the active profile.

use std::collections::BTreeSet;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use crate::api::client::ApiClient;
use crate::api::endpoints::{dot11, taps};
use crate::api::error::ApiError;
use crate::apps::api::gui::common::{lock, DEFAULT_REFRESH_SECONDS};
use crate::apps::api::gui::scope::{OrganizationNode, Scope, ScopePair, ScopeTree};
use crate::profiles::file::GuiSettings;

#[derive(Clone, Debug, PartialEq)]
pub enum TapSelection {
    All,
    Selected(BTreeSet<String>),
}

impl TapSelection {
    /// Value for the `taps` query parameter of tap-scoped endpoints.
    pub fn query_value(&self) -> String {
        match self {
            TapSelection::All => dot11::ALL_TAPS.to_string(),
            TapSelection::Selected(uuids) => uuids.iter().cloned().collect::<Vec<_>>().join(","),
        }
    }

    pub fn label(&self, catalog: &TapCatalog) -> String {
        match self {
            TapSelection::All => match catalog {
                TapCatalog::Available(taps) => format!("All taps ({})", taps.len()),
                _ => "All taps".to_string(),
            },
            TapSelection::Selected(uuids) => if uuids.len() == 1 { "1 tap selected".to_string() } else { format!("{} taps selected", uuids.len()) },
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct Preferences {
    pub refresh_seconds: u64,
    pub scope: Scope,
    pub taps: TapSelection,
}

impl Default for Preferences {
    fn default() -> Self {
        Preferences { refresh_seconds: DEFAULT_REFRESH_SECONDS, scope: Scope::all(), taps: TapSelection::All }
    }
}

impl Preferences {
    pub fn from_gui_settings(settings: Option<&GuiSettings>) -> Self {
        let Some(s) = settings else { return Preferences::default(); };

        Preferences {
            refresh_seconds: s.refresh_seconds.filter(|r| *r > 0).unwrap_or(DEFAULT_REFRESH_SECONDS),
            scope: Scope { organization_id: s.organization_id.clone(), tenant_id: s.tenant_id.clone() },
            taps: if s.taps.is_empty() { TapSelection::All } else { TapSelection::Selected(s.taps.iter().cloned().collect()) },
        }
    }

    pub fn to_gui_settings(&self) -> GuiSettings {
        GuiSettings {
            refresh_seconds: Some(self.refresh_seconds),
            organization_id: self.scope.organization_id.clone(),
            tenant_id: self.scope.tenant_id.clone(),
            taps: match &self.taps {
                TapSelection::All => Vec::new(),
                TapSelection::Selected(uuids) => uuids.iter().cloned().collect(),
            },
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct TapChoice {
    pub uuid: String,
    pub name: String,
    pub organization_id: String,
    pub tenant_id: String,
    pub tenant_name: String,
    pub online: Option<bool>,
}

#[derive(Clone)]
pub enum TapCatalog {
    Pending,
    Available(Vec<TapChoice>),
    Unavailable(String),
}

/// How often organizations, tenants and taps are rediscovered while the dashboard runs, so deletions and
/// additions on the server show up without a restart.
pub const REDISCOVERY_INTERVAL: Duration = Duration::from_secs(60);

/// Everything a view needs: the client, the live preferences and the discovered organizations, tenants and taps.
pub struct Context {
    pub client: Arc<ApiClient>,
    pub prefs: Mutex<Preferences>,
    pub scope_tree: Mutex<ScopeTree>,
    pub taps: Mutex<TapCatalog>,
    /// Explains a correction discovery had to make to the preferences, until the user changes them.
    pub notice: Mutex<Option<String>>,
    /// Set when discovery corrected the preferences and the shell should persist them.
    needs_persist: AtomicBool,
    discovering: AtomicBool,
    last_discovery: Mutex<Option<Instant>>,
}

impl Context {
    pub fn new(client: Arc<ApiClient>, prefs: Preferences) -> Arc<Self> {
        Arc::new(Context {
            client,
            prefs: Mutex::new(prefs),
            scope_tree: Mutex::new(ScopeTree::Pending),
            taps: Mutex::new(TapCatalog::Pending),
            notice: Mutex::new(None),
            needs_persist: AtomicBool::new(false),
            discovering: AtomicBool::new(false),
            last_discovery: Mutex::new(None),
        })
    }

    /// Discovers organizations, tenants and taps in the background. The previous result stays in place until the
    /// new one is complete, so views keep working during a rediscovery. Only one discovery runs at a time.
    pub fn discover(self: &Arc<Self>) {
        if self.discovering.swap(true, Ordering::AcqRel) {
            return;
        }

        let ctx = Arc::clone(self);
        thread::spawn(move || {
            let tree = ScopeTree::discover(&ctx.client);
            let catalog = match &tree {
                ScopeTree::Available(orgs) => match discover_taps(&ctx.client, &Scope::all().pairs(orgs)) {
                    Ok(taps) => TapCatalog::Available(taps),
                    Err(e) => TapCatalog::Unavailable(format!("Could not list taps: {}", e)),
                },
                ScopeTree::Unavailable(message) => TapCatalog::Unavailable(message.clone()),
                ScopeTree::Pending => TapCatalog::Pending,
            };

            // Preferences that point at deleted organizations, tenants or taps are corrected and persisted.
            if let ScopeTree::Available(orgs) = &tree {
                let catalog_taps: Option<&[TapChoice]> = match &catalog {
                    TapCatalog::Available(taps) => Some(taps.as_slice()),
                    _ => None,
                };
                let correction = reconcile(&mut lock(&ctx.prefs), orgs, catalog_taps);
                if let Some(message) = correction {
                    *lock(&ctx.notice) = Some(message);
                    ctx.needs_persist.store(true, Ordering::Release);
                }
            }

            *lock(&ctx.scope_tree) = tree;
            *lock(&ctx.taps) = catalog;
            *lock(&ctx.last_discovery) = Some(Instant::now());
            ctx.discovering.store(false, Ordering::Release);
        });
    }

    /// True once per correction: the shell persists the preferences when this returns true.
    pub fn take_persist_request(&self) -> bool {
        self.needs_persist.swap(false, Ordering::AcqRel)
    }

    /// True if the last discovery is older than the rediscovery interval.
    pub fn discovery_due(&self) -> bool {
        !self.discovering.load(Ordering::Acquire)
            && lock(&self.last_discovery).map_or(false, |t| t.elapsed() >= REDISCOVERY_INTERVAL)
    }

    pub fn is_discovering(&self) -> bool {
        self.discovering.load(Ordering::Acquire)
    }

    pub fn prefs(&self) -> Preferences {
        lock(&self.prefs).clone()
    }

    pub fn refresh_seconds(&self) -> u64 {
        lock(&self.prefs).refresh_seconds
    }

    /// The tenants covered by the current scope, or the reason they are unknown.
    pub fn pairs(&self) -> Result<Vec<ScopePair>, String> {
        match &*lock(&self.scope_tree) {
            ScopeTree::Available(orgs) => Ok(lock(&self.prefs).scope.pairs(orgs)),
            ScopeTree::Unavailable(message) => Err(message.clone()),
            ScopeTree::Pending => Err("Discovering organizations and tenants...".to_string()),
        }
    }

    /// Taps of the catalog that fall into the current scope.
    pub fn taps_in_scope(&self) -> Vec<TapChoice> {
        let TapCatalog::Available(taps) = &*lock(&self.taps) else { return Vec::new(); };
        let scope = lock(&self.prefs).scope.clone();

        taps.iter()
            .filter(|t| scope.organization_id.as_deref().map_or(true, |o| o == t.organization_id))
            .filter(|t| scope.organization_id.is_none() || scope.tenant_id.as_deref().map_or(true, |id| id == t.tenant_id))
            .cloned()
            .collect()
    }

    /// Name of a tap, if it is in the catalog.
    pub fn tap_name(&self, uuid: &str) -> Option<String> {
        let TapCatalog::Available(taps) = &*lock(&self.taps) else { return None; };
        taps.iter().find(|t| t.uuid == uuid).map(|t| t.name.clone())
    }
}

/// Drops references to organizations, tenants and taps that no longer exist. Returns a message describing the
/// correction, or None if the preferences were valid.
pub fn reconcile(prefs: &mut Preferences, orgs: &[OrganizationNode], taps: Option<&[TapChoice]>) -> Option<String> {
    let mut messages = Vec::new();

    if !prefs.scope.is_valid(orgs) {
        let what = if prefs.scope.tenant_id.is_some() { "tenant" } else { "organization" };
        prefs.scope = Scope::all();
        prefs.taps = TapSelection::All;
        messages.push(format!("Saved {} no longer exists. Scope reset to all tenants and taps.", what));
    }

    if let (TapSelection::Selected(selected), Some(taps)) = (&prefs.taps, taps) {
        let scope = prefs.scope.clone();
        let in_scope = |t: &&TapChoice| {
            scope.organization_id.as_deref().map_or(true, |o| o == t.organization_id)
                && (scope.organization_id.is_none() || scope.tenant_id.as_deref().map_or(true, |id| id == t.tenant_id))
        };
        let known: BTreeSet<&str> = taps.iter().filter(in_scope).map(|t| t.uuid.as_str()).collect();
        let kept: BTreeSet<String> = selected.iter().filter(|u| known.contains(u.as_str())).cloned().collect();
        let dropped = selected.len() - kept.len();

        if dropped > 0 {
            prefs.taps = if kept.is_empty() { TapSelection::All } else { TapSelection::Selected(kept) };
            let gone = if dropped == 1 { "1 saved tap no longer exists".to_string() } else { format!("{} saved taps no longer exist", dropped) };
            messages.push(match &prefs.taps {
                TapSelection::All => format!("{}. Selection reset to all taps.", gone),
                TapSelection::Selected(k) => format!("{}. {} remain{} selected.", gone, k.len(), if k.len() == 1 { "s" } else { "" }),
            });
        }
    }

    if messages.is_empty() { None } else { Some(messages.join(" ")) }
}

fn discover_taps(client: &ApiClient, pairs: &[ScopePair]) -> Result<Vec<TapChoice>, ApiError> {
    let mut result = Vec::new();

    for pair in pairs {
        for tap in taps::find_all_high_level(client, &pair.organization_id, &pair.tenant_id)?.data.taps {
            if let Some(uuid) = tap.uuid {
                result.push(TapChoice {
                    name: tap.name.unwrap_or_else(|| uuid.clone()),
                    uuid,
                    organization_id: pair.organization_id.clone(),
                    tenant_id: pair.tenant_id.clone(),
                    tenant_name: pair.tenant_name.clone(),
                    online: tap.is_online,
                });
            }
        }
    }

    result.sort_by(|a, b| a.tenant_name.cmp(&b.tenant_name).then_with(|| a.name.cmp(&b.name)));
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn preferences_roundtrip_through_gui_settings() {
        let mut taps = BTreeSet::new();
        taps.insert("b".to_string());
        taps.insert("a".to_string());
        let prefs = Preferences { refresh_seconds: 30, scope: Scope::tenant("o", "t"), taps: TapSelection::Selected(taps) };

        let back = Preferences::from_gui_settings(Some(&prefs.to_gui_settings()));
        assert_eq!(back, prefs);
        assert_eq!(prefs.to_gui_settings().taps, vec!["a".to_string(), "b".to_string()]);
    }

    fn tap(uuid: &str, org: &str, tenant: &str) -> TapChoice {
        TapChoice { uuid: uuid.into(), name: uuid.into(), organization_id: org.into(), tenant_id: tenant.into(), tenant_name: tenant.into(), online: None }
    }

    fn orgs() -> Vec<OrganizationNode> {
        use crate::apps::api::gui::scope::TenantNode;
        vec![OrganizationNode { id: "o1".into(), name: "Org".into(), tenants: vec![TenantNode { id: "t1".into(), name: "T1".into() }] }]
    }

    #[test]
    fn reconcile_keeps_valid_preferences() {
        let mut prefs = Preferences { refresh_seconds: 10, scope: Scope::tenant("o1", "t1"), taps: TapSelection::Selected(["a".to_string()].into()) };
        let before = prefs.clone();
        assert!(reconcile(&mut prefs, &orgs(), Some(&[tap("a", "o1", "t1")])).is_none());
        assert_eq!(prefs, before);
    }

    #[test]
    fn reconcile_resets_deleted_tenant() {
        let mut prefs = Preferences { refresh_seconds: 10, scope: Scope::tenant("o1", "gone"), taps: TapSelection::Selected(["a".to_string()].into()) };
        let message = reconcile(&mut prefs, &orgs(), Some(&[tap("a", "o1", "t1")])).unwrap();
        assert!(message.contains("tenant no longer exists"));
        assert_eq!(prefs.scope, Scope::all());
        assert_eq!(prefs.taps, TapSelection::All);
    }

    #[test]
    fn reconcile_drops_deleted_taps() {
        let mut prefs = Preferences { refresh_seconds: 10, scope: Scope::all(), taps: TapSelection::Selected(["a".to_string(), "gone".to_string()].into()) };
        let message = reconcile(&mut prefs, &orgs(), Some(&[tap("a", "o1", "t1")])).unwrap();
        assert!(message.contains("1 saved tap no longer exists"));
        assert_eq!(prefs.taps, TapSelection::Selected(["a".to_string()].into()));

        let mut prefs = Preferences { refresh_seconds: 10, scope: Scope::all(), taps: TapSelection::Selected(["gone".to_string()].into()) };
        let message = reconcile(&mut prefs, &orgs(), Some(&[tap("a", "o1", "t1")])).unwrap();
        assert!(message.contains("reset to all taps"));
        assert_eq!(prefs.taps, TapSelection::All);
    }

    #[test]
    fn missing_settings_give_defaults() {
        let prefs = Preferences::from_gui_settings(None);
        assert_eq!(prefs, Preferences::default());
        assert_eq!(prefs.taps.query_value(), "*");
    }
}
