/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.configuration;

public class ConfigurationKeys {

    public static final String GENERAL = "general";
    public static final String DATA_DIRECTORY = "data_directory";
    public static final String PLUGIN_DIRECTORY = "plugin_directory";
    public static final String CRYPTO_DIRECTORY = "crypto_directory";
    public static final String NTP_SERVER = "ntp_server";
    public static final String INTERFACES = "interfaces";
    public static final String NAME = "name";
    public static final String DATABASE_PATH = "database_path";
    public static final String VERSIONCHECKS = "versionchecks";
    public static final String VERSIONCHECKS_URI = "versionchecks_uri";
    public static final String REST_LISTEN_URI = "rest_listen_uri";
    public static final String HTTP_EXTERNAL_URI = "http_external_uri";
    public static final String HTTP_MAX_POST_SIZE = "http_max_post_size";
    public static final String SLOW_QUERY_LOG_THRESHOLD = "slow_query_log_threshold";
    public static final String PERFORMANCE = "performance";
    public static final String REPORT_PROCESSOR_POOL_SIZE = "report_processor_pool_size";
    public static final String DATABASE_POOL_SIZE = "database_pool_size";
    public static final String MISC = "misc";
    public static final String CUSTOM_TITLE = "custom_title";
    public static final String CUSTOM_FAVICON_URL = "custom_favicon_url";
    public static final String CONNECT_SERVER = "connect_server";
    public static final String CONNECT_SKIP = "connect_skip";
    public static final String PROTOCOLS = "protocols";
    public static final String TCP = "tcp";
    public static final String TCP_SESSION_TIMEOUT_SECONDS = "session_timeout_seconds";

}