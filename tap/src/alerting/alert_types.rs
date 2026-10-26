use std::collections::HashMap;
use serde::Serialize;

#[derive(Serialize, Clone, Debug)]
pub enum AlertAttribute {
    Number(u128),
    String(String)
}

#[derive(Debug)]
pub struct Dot11Alert {
    pub alert_type: Dot11AlertType,
    pub signal_strength: i8,
    pub attributes: HashMap<String, AlertAttribute>
}

#[derive(Serialize, Clone, Debug)]
pub enum Dot11AlertType {
    PwnagotchiDetected
}
