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

package app.nzyme.core.tables;

import app.nzyme.core.tables.bluetooth.BluetoothTable;
import app.nzyme.core.tables.dot11.Dot11Table;
import app.nzyme.core.tables.ethernet.*;
import app.nzyme.core.tables.uav.UAVTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import app.nzyme.core.NzymeNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TablesService {

    private static final Logger LOG = LogManager.getLogger(TablesService.class);

    private final NzymeNode nzyme;

    private final Map<String, DataTable> tables;

    private final ExecutorService processorPool;

    public TablesService(NzymeNode nzyme) {
        this.nzyme = nzyme;

        this.tables = new ImmutableMap.Builder<String, DataTable>()
                .put("dot11", new Dot11Table(this))
                .put("bluetooth", new BluetoothTable(this))
                .put("uav", new UAVTable(this))
                .put("arp", new ARPTable(this))
                .put("dhcp", new DHCPTable(this))
                .put("tcp", new TCPTable(this))
                .put("udp", new UDPTable(this))
                .put("dns", new DNSTable(this))
                .put("ssh", new SSHTable(this))
                .put("socks", new SOCKSTable(this))
                .put("ntp", new NTPTable(this))
                .put("rtsp", new RTSPTable(this))
                .put("nat", new NATTable(this))
                .build();

        this.processorPool = Executors.newFixedThreadPool(
                nzyme.getConfiguration().performance().reportProcessorPoolSize(),
                new ThreadFactoryBuilder()
                        .setNameFormat("dot11-report-writer-%d")
                        .setDaemon(true)
                        .build()
        );

        Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("tables-cleaner-%d")
                        .setDaemon(true)
                        .build()
        ).scheduleAtFixedRate(this::retentionClean, 0, 1, TimeUnit.HOURS);
    }

    private void retentionClean() {
        for (Map.Entry<String, DataTable> table : tables.entrySet()) {
            LOG.debug("Retention cleaning data table [{}].", table.getKey());
            table.getValue().retentionClean();
        }

    }

    public Dot11Table dot11() {
        return (Dot11Table) tables.get("dot11");
    }

    public BluetoothTable bluetooth() {
        return (BluetoothTable) tables.get("bluetooth");
    }

    public ARPTable arp() {
        return (ARPTable) tables.get("arp");
    }

    public DHCPTable dhcp() { return (DHCPTable) tables.get("dhcp"); }

    public DNSTable dns() {
        return (DNSTable) tables.get("dns");
    }

    public TCPTable tcp() {
        return (TCPTable) tables.get("tcp");
    }

    public UDPTable udp() {
        return (UDPTable) tables.get("udp");
    }

    public SSHTable ssh() {
        return (SSHTable) tables.get("ssh");
    }

    public SOCKSTable socks() {
        return (SOCKSTable) tables.get("socks");
    }

    public NTPTable ntp() {
        return (NTPTable) tables.get("ntp");
    }

    public RTSPTable rtsp() {
        return (RTSPTable) tables.get("rtsp");
    }

    public NATTable nat() {
        return (NATTable) tables.get("nat");
    }

    public UAVTable uav() { return (UAVTable) tables.get("uav"); }

    public ExecutorService getProcessorPool() {
        return processorPool;
    }

    public NzymeNode getNzyme() {
        return nzyme;
    }

}
