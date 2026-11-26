use serde_json::{json, Value};

use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::assets::{self, AssetRef};
use crate::api::error::ApiError;
use crate::api::types::{
    AssetActivityResponse, AssetAlertsResponse, AssetAttributesResponse, AssetCommunicationResponse,
    AssetDetailsResponse, AssetExclusionsResponse, AssetFindingsResponse, AssetHostnameDetailsResponse,
    AssetIpAddressDetailsResponse, AssetProfileSummaryResponse, AssetRisksResponse, AssetTimelineResponse,
};

pub const DEFAULT_ACTIVITY_DAYS: u32 = 90;
pub const DEFAULT_EDGE_LIMIT: usize = 500;
pub const DEFAULT_TIMELINE_LIMIT: usize = 100;
pub const IDENTITY_LIMIT: usize = 50;

#[derive(Clone, Debug)]
pub struct LookupScope {
    pub organization_id: String,
    pub organization_name: Option<String>,
    pub tenant_id: String,
    pub tenant_name: Option<String>,
}

impl LookupScope {
    pub fn tenant_label(&self) -> &str {
        self.tenant_name.as_deref().unwrap_or(&self.tenant_id)
    }
}

#[derive(Clone, Debug)]
pub struct LocatedAsset {
    pub scope: LookupScope,
    pub asset: AssetDetailsResponse,
}

impl LocatedAsset {
    pub fn uuid(&self) -> &str {
        self.asset.uuid.as_deref().unwrap_or("")
    }

    pub fn as_ref(&self) -> AssetRef<'_> {
        AssetRef {
            asset_id: self.uuid(),
            organization_id: &self.scope.organization_id,
            tenant_id: &self.scope.tenant_id,
        }
    }
}

pub fn lookup(client: &ApiClient, scopes: &[LookupScope], mac: &str) -> Result<Vec<LocatedAsset>, ApiError> {
    let mut found = Vec::new();
    for scope in scopes {
        let page = match assets::find_by_mac(client, &scope.organization_id, &scope.tenant_id, mac) {
            Ok(page) => page,
            Err(ApiError::NotFound(_)) => continue,
            Err(e) => return Err(e),
        };
        for asset in page.data.assets {
            if asset.uuid.is_some() {
                found.push(LocatedAsset { scope: scope.clone(), asset });
            }
        }
    }
    Ok(found)
}

pub struct ProfileOptions {
    pub time_range: String,
    pub time_range_label: String,
    pub activity_days: u32,
    pub tz_offset: i32,
    pub edge_limit: usize,
    pub timeline_limit: usize,
}

impl ProfileOptions {
    pub fn last_minutes(minutes: u32) -> Self {
        ProfileOptions {
            time_range: json!({"type": "relative", "minutes": minutes}).to_string(),
            time_range_label: describe_minutes(minutes),
            activity_days: DEFAULT_ACTIVITY_DAYS,
            tz_offset: 0,
            edge_limit: DEFAULT_EDGE_LIMIT,
            timeline_limit: DEFAULT_TIMELINE_LIMIT,
        }
    }
}

pub fn describe_minutes(minutes: u32) -> String {
    if minutes % 1440 == 0 {
        let days = minutes / 1440;
        if days == 1 { "last 24 hours".to_string() } else { format!("last {} days", days) }
    } else if minutes % 60 == 0 {
        let hours = minutes / 60;
        if hours == 1 { "last hour".to_string() } else { format!("last {} hours", hours) }
    } else {
        format!("last {} minutes", minutes)
    }
}

pub type Section<T> = Result<T, String>;

pub struct AssetProfile {
    pub located: LocatedAsset,
    pub options_label: String,
    pub activity_days: u32,
    pub hostnames: Section<(Vec<AssetHostnameDetailsResponse>, i64)>,
    pub ip_addresses: Section<(Vec<AssetIpAddressDetailsResponse>, i64)>,
    pub summary: Section<AssetProfileSummaryResponse>,
    pub attributes: Section<AssetAttributesResponse>,
    pub activity: Section<AssetActivityResponse>,
    pub communication: Section<AssetCommunicationResponse>,
    pub timeline: Section<AssetTimelineResponse>,
    pub alerts: Section<AssetAlertsResponse>,
    pub findings: Section<AssetFindingsResponse>,
    pub exclusions: Section<AssetExclusionsResponse>,
    pub risks: Section<AssetRisksResponse>,
    pub raw: Value,
}

impl AssetProfile {
    pub fn mac(&self) -> &str {
        self.located.asset.mac.as_ref().and_then(|m| m.address.as_deref()).unwrap_or("")
    }

    pub fn name(&self) -> Option<&str> {
        self.located.asset.name.as_deref().filter(|n| !n.is_empty())
    }

    pub fn label(&self) -> String {
        self.name().map(|n| n.to_string()).unwrap_or_else(|| self.mac().to_string())
    }

    pub fn section_errors(&self) -> Vec<(&'static str, &str)> {
        let sections: [(&'static str, Option<&String>); 11] = [
            ("hostnames", self.hostnames.as_ref().err()),
            ("ip addresses", self.ip_addresses.as_ref().err()),
            ("summary", self.summary.as_ref().err()),
            ("attributes", self.attributes.as_ref().err()),
            ("activity", self.activity.as_ref().err()),
            ("communication", self.communication.as_ref().err()),
            ("timeline", self.timeline.as_ref().err()),
            ("alerts", self.alerts.as_ref().err()),
            ("findings", self.findings.as_ref().err()),
            ("exclusions", self.exclusions.as_ref().err()),
            ("risks", self.risks.as_ref().err()),
        ];
        sections.into_iter().filter_map(|(name, error)| error.map(|e| (name, e.as_str()))).collect()
    }
}

fn section<T>(raw: &mut serde_json::Map<String, Value>, key: &str, result: Result<Typed<T>, ApiError>) -> Section<T> {
    match result {
        Ok(typed) => {
            raw.insert(key.to_string(), typed.raw);
            Ok(typed.data)
        }
        Err(e) => Err(e.to_string()),
    }
}

pub fn load(client: &ApiClient, located: LocatedAsset, options: &ProfileOptions) -> AssetProfile {
    let asset = located.as_ref();
    let mut raw = serde_json::Map::new();
    raw.insert("organization_id".to_string(), json!(located.scope.organization_id));
    raw.insert("tenant_id".to_string(), json!(located.scope.tenant_id));
    let details = section(&mut raw, "asset", assets::find_one(client, &asset));

    let hostnames = section(&mut raw, "hostnames", assets::find_hostnames(client, &asset, IDENTITY_LIMIT))
        .map(|r| { let total = r.total.unwrap_or(r.hostnames.len() as i64); (r.hostnames, total) });
    let ip_addresses = section(&mut raw, "ip_addresses", assets::find_ip_addresses(client, &asset, IDENTITY_LIMIT))
        .map(|r| { let total = r.total.unwrap_or(r.addresses.len() as i64); (r.addresses, total) });
    let summary = section(&mut raw, "summary", assets::find_profile_summary(client, &asset));
    let attributes = section(&mut raw, "attributes", assets::find_profile_attributes(client, &asset));
    let activity = section(&mut raw, "activity", assets::find_profile_activity(client, &asset, options.activity_days, options.tz_offset));
    let communication = section(&mut raw, "communication", assets::find_profile_communication(client, &asset, &options.time_range, options.edge_limit));
    let timeline = section(&mut raw, "timeline", assets::find_profile_timeline(client, &asset, &options.time_range, options.timeline_limit));
    let alerts = section(&mut raw, "alerts", assets::find_profile_alerts(client, &asset));
    let findings = section(&mut raw, "findings", assets::find_profile_findings(client, &asset));
    let exclusions = section(&mut raw, "exclusions", assets::find_profile_exclusions(client, &asset));
    let risks = section(&mut raw, "risks", assets::find_profile_risks(client, &asset, &options.time_range));

    let located = match details {
        Ok(fresh) => LocatedAsset { scope: located.scope, asset: fresh },
        Err(_) => located,
    };

    AssetProfile {
        located,
        options_label: options.time_range_label.clone(),
        activity_days: options.activity_days,
        hostnames, ip_addresses, summary, attributes, activity, communication, timeline, alerts, findings,
        exclusions, risks,
        raw: Value::Object(raw),
    }
}
