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

package app.nzyme.core;

import app.nzyme.core.assets.AssetManager;
import app.nzyme.core.bluetooth.Bluetooth;
import app.nzyme.core.bluetooth.sig.BluetoothSigService;
import app.nzyme.core.configuration.base.BaseConfiguration;
import app.nzyme.core.connect.ConnectService;
import app.nzyme.core.context.ContextService;
import app.nzyme.core.detection.alerts.DetectionAlertService;
import app.nzyme.core.distributed.ClusterManager;
import app.nzyme.core.distributed.NodeManager;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.environment.EnvironmentService;
import app.nzyme.core.events.EventEngine;
import app.nzyme.core.integrations.geoip.GeoIpService;
import app.nzyme.core.monitoring.health.HealthMonitor;
import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.monitors.Monitors;
import app.nzyme.core.ouis.OuiService;
import app.nzyme.core.quota.QuotaService;
import app.nzyme.core.rest.server.NzymeHttpServer;
import app.nzyme.core.security.authentication.AuthenticationService;
import app.nzyme.core.subsystems.Subsystems;
import app.nzyme.core.uav.Uav;
import app.nzyme.plugin.*;
import app.nzyme.plugin.distributed.messaging.MessageBus;
import app.nzyme.plugin.distributed.tasksqueue.TasksQueue;
import app.nzyme.plugin.retro.RetroService;
import com.codahale.metrics.MetricRegistry;
import tools.jackson.databind.ObjectMapper;
import app.nzyme.core.configuration.node.NodeConfiguration;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.TapManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface NzymeNode extends PluginEntryPoint, DatabaseProvider, NodeIdentificationProvider, MetricsRegistryProvider {

    void initialize();
    void shutdown();

    NodeManager getNodeManager();
    ClusterManager getClusterManager();
    MessageBus getMessageBus();
    TasksQueue getTasksQueue();

    AuthenticationService getAuthenticationService();

    Subsystems getSubsystems();

    HealthMonitor getHealthMonitor();
    DatabaseMaintenance getDatabaseMaintenance();

    Ethernet getEthernet();
    Dot11 getDot11();
    Bluetooth getBluetooth();
    Uav getUav();

    AssetManager getAssetsManager();

    ContextService getContextService();

    NodeConfiguration getConfiguration();
    BaseConfiguration getBaseConfiguration();

    Path getDataDirectory();

    MetricRegistry getMetrics();

    Database getDatabase();

    QuotaService getQuotaService();

    EventEngine getEventEngine();
    Monitors getMonitors();

    TapManager getTapManager();

    TablesService getTablesService();

    GeoIpService getGeoIpService();
    OuiService getOuiService();
    BluetoothSigService getBluetoothSigService();
    EnvironmentService getEnvironmentService();

    List<String> getInitializedPlugins();

    Optional<RetroService> retroService();

    Crypto getCrypto();

    ObjectMapper getObjectMapper();

    Registry getDatabaseCoreRegistry();
    RegistryChangeMonitor getRegistryChangeMonitor();

    DetectionAlertService getDetectionAlertService();

    ConnectService getConnect();

    Version getVersion();

    NzymeHttpServer getHttpServer();

}
