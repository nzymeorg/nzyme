package app.nzyme.core.quota;

public enum QuotaType {

    TAPS("Taps"),
    TENANTS("Tenants"),
    TENANT_USERS("Users"),
    DASHBOARDS("Dashboards"),
    DASHBOARD_DISPLAYS("Dashboard Displays");

    private final String humanReadable;

    QuotaType(String humanReadable) {
        this.humanReadable = humanReadable;
    }

    public String getHumanReadable() {
        return humanReadable;
    }

}
