use std::collections::HashMap;
use chrono::{DateTime, Utc};
use serde::Serialize;
use crate::state::tables::name_resolution_table::{ResponseAggregate, ResponseKey};

#[derive(Serialize)]
pub struct NameResolutionResponsesReport {
    pub responses: Vec<NameResolutionResponseReport>
}

#[derive(Serialize)]
pub struct NameResolutionResponseReport {
    pub protocol: String,
    pub responder_mac: String,
    pub responder_address: String,
    pub name: String,
    pub answer_addresses: Vec<String>,
    pub response_count: u64,
    pub unicast_count: u64,
    pub victims: Vec<NameResolutionVictimReport>,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

#[derive(Serialize)]
pub struct NameResolutionVictimReport {
    pub mac: String,
    pub address: Option<String>,
    pub count: u64,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

pub fn generate(responses: &HashMap<ResponseKey, ResponseAggregate>) -> NameResolutionResponsesReport {
    let mut result = Vec::with_capacity(responses.len());

    for (key, agg) in responses {
        let mut answer_addresses: Vec<String> = agg.answer_addresses.iter().map(|a| a.to_string()).collect();
        answer_addresses.sort();

        let victims = agg.victims.iter().map(|(mac, v)| NameResolutionVictimReport {
            mac: mac.clone(),
            address: v.address.map(|a| a.to_string()),
            count: v.count,
            first_seen: v.first_seen,
            last_seen: v.last_seen
        }).collect();

        result.push(NameResolutionResponseReport {
            protocol: key.protocol.to_string(),
            responder_mac: key.responder_mac.clone(),
            responder_address: key.responder_address.to_string(),
            name: key.name.clone(),
            answer_addresses,
            response_count: agg.response_count,
            unicast_count: agg.unicast_count,
            victims,
            first_seen: agg.first_seen,
            last_seen: agg.last_seen
        });
    }

    NameResolutionResponsesReport { responses: result }
}
