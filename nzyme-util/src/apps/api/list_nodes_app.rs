use crate::api::endpoints::nodes;
use crate::api::types::NodeResponse;
use crate::apps::api::{connect, exit_with_api_error, or_unknown, print_json, status_label, BOLD, RESET};
use crate::arguments::ConnectionArgs;
use crate::tools::{format_bytes, format_usage};

pub fn run(connection: &ConnectionArgs) {
    let client = connect(connection);

    let response = match nodes::find_all(&client) {
        Ok(response) => response,
        Err(e) => exit_with_api_error(&e),
    };

    if connection.json {
        print_json(&response.raw);
        return;
    }

    eprintln!("{BOLD}==> Nzyme Cluster Nodes{RESET}");
    eprintln!("    Server: {}\n", client.base_url());

    let nodes = &response.data.nodes;
    if nodes.is_empty() {
        println!("No nodes found.");
        return;
    }

    for node in nodes {
        print_node(node);
        println!();
    }

    let online = nodes.iter().filter(|n| n.active == Some(true)).count();
    eprintln!("[*] {} nodes, {} online.", nodes.len(), online);
}

fn print_node(node: &NodeResponse) {
    let mut flags = Vec::new();
    if node.deleted == Some(true) { flags.push("deleted"); }
    if node.is_ephemeral == Some(true) { flags.push("ephemeral"); }
    let flags = if flags.is_empty() { String::new() } else { format!(" ({})", flags.join(", ")) };

    println!(
        "Node:\n\
         ├─ Name:            {BOLD}{}{RESET}\n\
         ├─ UUID:            {}\n\
         ├─ Status:          {}{}\n\
         ├─ Version:         {}\n\
         ├─ Last Seen:       {}\n\
         ├─ Clock Drift:     {}\n\
         ├─ Cycle:           {}\n\
         ├─ CPU Load:        {}\n\
         ├─ Threads:         {}\n\
         ├─ Memory:          {}\n\
         ├─ Heap:            {}\n\
         ├─ Process VSZ:     {}\n\
         ├─ Process Start:   {}\n\
         ├─ HTTP Listen:     {}\n\
         ├─ HTTP External:   {}\n\
         ├─ TLS Expiration:  {}\n\
         ├─ TLS Fingerprint: {}\n\
         └─ OS:              {}",
        or_unknown(&node.name),
        or_unknown(&node.uuid),
        status_label(node.active), flags,
        or_unknown(&node.version),
        or_unknown(&node.last_seen),
        node.clock_drift_ms.map(|d| format!("{} ms", d)).unwrap_or_else(|| "<unknown>".to_string()),
        node.cycle.map(|c| c.to_string()).unwrap_or_else(|| "<unknown>".to_string()),
        node.cpu_system_load.map(|l| format!("{:.1} %", l)).unwrap_or_else(|| "<unknown>".to_string()),
        node.cpu_thread_count.map(|t| t.to_string()).unwrap_or_else(|| "<unknown>".to_string()),
        format_usage(node.memory_bytes_used, node.memory_bytes_total),
        format_usage(node.heap_bytes_used, node.heap_bytes_total),
        node.process_virtual_size.map(format_bytes).unwrap_or_else(|| "<unknown>".to_string()),
        or_unknown(&node.process_start_time),
        or_unknown(&node.http_listen_uri),
        or_unknown(&node.http_external_uri),
        or_unknown(&node.tls_cert_expiration_date),
        or_unknown(&node.tls_cert_fingerprint),
        or_unknown(&node.os_information),
    );
}
