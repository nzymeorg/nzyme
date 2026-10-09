package app.nzyme.core.distributed;

public enum MetricExternalName {

    MEMORY_BYTES_TOTAL("memory_bytes_total"),
    MEMORY_BYTES_AVAILABLE("memory_bytes_available"),
    MEMORY_BYTES_USED("memory_bytes_used"),
    HEAP_BYTES_TOTAL("heap_bytes_total"),
    HEAP_BYTES_AVAILABLE("heap_bytes_available"),
    HEAP_BYTES_USED("heap_bytes_used"),
    CPU_SYSTEM_LOAD("cpu_system_load"),
    PROCESS_VIRTUAL_SIZE("process_virtual_size"),
    TAP_REPORT_SIZE("tap_report_size"),
    PGP_ENCRYPTION_TIMER("pgp_encryption_timer"),
    PGP_DECRYPTION_TIMER("pgp_decryption_timer"),
    PASSWORD_HASHING_TIMER("password_hashing_timer"),
    CONTEXT_MAC_LOOKUP_TIMER("context_mac_lookup_timer"),
    REPORT_PROCESSING_DOT11_TIMER("report_processing_dot11_timer"),
    REPORT_PROCESSING_TCP_TIMER("report_processing_tcp_timer"),
    REPORT_PROCESSING_DNS_TIMER("report_processing_dns_timer"),
    REPORT_PROCESSING_SSH_TIMER("report_processing_ssh_timer"),
    REPORT_PROCESSING_SOCKS_TIMER("report_processing_socks_timer"),

    GEOIP_CACHE_SIZE("geoip_cache_size"),
    CONTEXT_MAC_CACHE_SIZE("context_mac_cache_size"),
    CONTEXT_NETWORK_CACHE_SIZE("context_network_cache_size"),
    DATABASE_POOL_ACTIVE_CONNECTIONS("database_pool_active_connections"),
    DATABASE_POOL_IDLE_CONNECTIONS("database_pool_idle_connections"),
    DATABASE_POOL_PENDING_CONNECTIONS("database_pool_pending_connections"),
    DATABASE_POOL_TOTAL_CONNECTIONS("database_pool_total_connections"),
    DATABASE_POOL_MAX_CONNECTIONS("database_pool_max_connections"),
    DATABASE_POOL_CONNECTION_TIMEOUTS("database_pool_connection_timeouts"),
    DATABASE_POOL_WAIT_TIMER("database_pool_wait_timer"),
    DATABASE_POOL_USAGE_TIMER("database_pool_usage_timer"),
    FILE_PERMISSION_ISSUES("file_permission_issues"),
    LOG_COUNTS_TRACE("log_counts_trace"),
    LOG_COUNTS_DEBUG("log_counts_debug"),
    LOG_COUNTS_INFO("log_counts_info"),
    LOG_COUNTS_WARN("log_counts_warn"),
    LOG_COUNTS_ERROR("log_counts_error"),
    LOG_COUNTS_FATAL("log_counts_fatal");

    public final String database_label;

    MetricExternalName(String database_label) {
        this.database_label = database_label;
    }

}
