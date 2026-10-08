use crate::api::endpoints::taps;
use crate::apps::api::{connect, exit_with_api_error, print_json, BOLD, RESET};
use crate::arguments::ConnectionArgs;

pub fn run(connection: &ConnectionArgs, uuid: String) {
    let client = connect(connection);

    let response = match taps::find_metrics(&client, &uuid) {
        Ok(response) => response,
        Err(e) => exit_with_api_error(&e),
    };

    if connection.json {
        print_json(&response.raw);
        return;
    }

    let metrics = &response.data;

    eprintln!("{BOLD}==> Tap Metrics{RESET}");
    eprintln!("    Server: {}", client.base_url());
    eprintln!("    Tap:    {}\n", uuid);

    if metrics.gauges.is_empty() && metrics.timers.is_empty() {
        println!("No metrics reported yet.");
        return;
    }

    if !metrics.gauges.is_empty() {
        println!("{BOLD}Gauges:{RESET}");
        let width = metrics.gauges.keys().map(|k| k.len()).max().unwrap_or(0);
        for (name, gauge) in &metrics.gauges {
            println!(
                "  {:<width$}  {:>16}  {}",
                name,
                gauge.metric_value.map(format_metric).unwrap_or_else(|| "<none>".to_string()),
                gauge.created_at.as_deref().unwrap_or(""),
                width = width
            );
        }
        println!();
    }

    if !metrics.timers.is_empty() {
        println!("{BOLD}Timers:{RESET}");
        let width = metrics.timers.keys().map(|k| k.len()).max().unwrap_or(0);
        println!("  {:<width$}  {:>16}  {:>16}", "", "mean", "p99", width = width);
        for (name, timer) in &metrics.timers {
            println!(
                "  {:<width$}  {:>16}  {:>16}  {}",
                name,
                timer.mean.map(format_metric).unwrap_or_else(|| "<none>".to_string()),
                timer.p99.map(format_metric).unwrap_or_else(|| "<none>".to_string()),
                timer.created_at.as_deref().unwrap_or(""),
                width = width
            );
        }
        println!();
    }

    eprintln!("[*] {} gauges, {} timers.", metrics.gauges.len(), metrics.timers.len());
}

fn format_metric(value: f64) -> String {
    if value.fract() == 0.0 && value.abs() < 1e15 {
        crate::tools::format_count(value as i64)
    } else {
        format!("{:.3}", value)
    }
}
