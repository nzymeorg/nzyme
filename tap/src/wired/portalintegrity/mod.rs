pub mod probe;
pub mod stack;
pub mod tls;

use std::sync::{Arc, Mutex};
use std::time::Duration as StdDuration;

use anyhow::Result;
use log::{error, info};

use crate::wired::mac;
use stack::Stack;
use crate::configuration::PortalIntegrityConfiguration;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::portal_integrity_report;
use crate::metrics::Metrics;

const DHCP_TIMEOUT: StdDuration = StdDuration::from_secs(15);

pub fn run(cfg: PortalIntegrityConfiguration,
           metrics: Arc<Mutex<Metrics>>,
           leaderlink: Arc<Mutex<Leaderlink>>) -> Result<()> {
    tls::install_crypto_provider();

    let interval = StdDuration::from_secs(cfg.interval_minutes * 60);
    info!(
        "portal_integrity starting on '{}', {} control URL(s), every {} min",
        cfg.interface,
        cfg.control_urls.len(),
        cfg.interval_minutes
    );

    loop {
        if let Err(e) = run_cycle(&cfg, &metrics, &leaderlink) {
            error!("portal_integrity cycle failed: {:#}", e);
        }
        info!("portal_integrity: cycle complete, sleeping {:?}", interval);

        std::thread::sleep(interval);
    }
}

fn run_cycle(cfg: &PortalIntegrityConfiguration,
             metrics: &Arc<Mutex<Metrics>>,
             leaderlink: &Arc<Mutex<Leaderlink>>) -> Result<()> {
    let mac = mac::resolve(&cfg.mac)?;
    info!("portal_integrity: probe cycle using MAC {}", mac);

    let mut stack = Stack::new(&cfg.interface, mac)?;
    let _lease = stack.run_dhcp(DHCP_TIMEOUT)?;

    for url in &cfg.control_urls {
        let mac = mac::resolve(&cfg.mac)?;
        let mut stack = Stack::new(&cfg.interface, mac)?;
        let lease = stack.run_dhcp(DHCP_TIMEOUT)?;

        let context = probe::ProbeContext {
            interface: cfg.interface.clone(),
            mac: mac.to_string().replace("-", ":").to_uppercase(),
            assigned_cidr: lease.address.to_string(),
            gateway: lease.router.map(|r| r.to_string()),
            dhcp_server: lease.dhcp_server.map(|s| s.to_string()),
            dns_servers: lease.dns_servers.iter().map(|d| d.to_string()).collect(),
        };

        let result = probe::probe_url(&mut stack, url, cfg.max_redirects, &context);
        result.log();


        let mut timer = Timer::new();
        // Generate JSON.
        let report = match serde_json::to_string(&portal_integrity_report::generate(&result)) {
            Ok(report) => report,
            Err(e) => {
                error!("Could not serialize portal integrity report: {}", e);
                continue;
            }
        };
        timer.stop();
        record_timer(
            timer.elapsed_microseconds(),
            "portalintegrity.timer.report_generation",
            &metrics
        );

        // Send report.
        match leaderlink.lock() {
            Ok(link) => {
                if let Err(e) = link.send_report("portalintegrity/url", report) {
                    error!("Could not submit portal integrity report: {}", e);
                }
            },
            Err(e) => error!("Could not acquire leader link lock for portal integrity \
                        report submission: {}", e)
        }
    }

    Ok(())
}