package app.nzyme.core.dashboards;

import app.nzyme.plugin.Subsystem;
import jakarta.annotation.Nullable;

public enum DashboardMetric {

    ALERTS_ACTIVE("Active Alerts", null, null, Aggregation.LATEST),
    ALERTS_NEW("New Alerts", null, null, Aggregation.SUM),
    THROUGHPUT("Throughput", "BITS_PER_SECOND", null, Aggregation.AVERAGE),
    ETHERNET_ACTIVE_ASSETS("Active Assets", null, Subsystem.ETHERNET, Aggregation.DISTINCT),
    DOT11_BSSIDS("BSSIDs", null, Subsystem.DOT11, Aggregation.PEAK),
    DOT11_SSIDS("SSIDs", null, Subsystem.DOT11, Aggregation.PEAK),
    DOT11_DISCONNECTIONS("Disconnection Frames", null, Subsystem.DOT11, Aggregation.SUM),
    BLUETOOTH_DEVICES("Bluetooth Devices", null, Subsystem.BLUETOOTH, Aggregation.PEAK);

    // Counts of distinct things cannot be summed up over buckets.
    public enum Aggregation { LATEST, SUM, AVERAGE, PEAK, DISTINCT }

    private final String title;
    @Nullable
    private final String unit;
    @Nullable
    private final Subsystem subsystem;
    private final Aggregation aggregation;

    DashboardMetric(String title, @Nullable String unit, @Nullable Subsystem subsystem, Aggregation aggregation) {
        this.title = title;
        this.unit = unit;
        this.subsystem = subsystem;
        this.aggregation = aggregation;
    }

    public String title() {
        return title;
    }

    @Nullable
    public String unit() {
        return unit;
    }

    @Nullable
    public Subsystem subsystem() {
        return subsystem;
    }

    public Aggregation aggregation() {
        return aggregation;
    }

    public boolean showsAlerts() {
        return this == ALERTS_ACTIVE || this == ALERTS_NEW;
    }

}
