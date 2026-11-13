package app.nzyme.core.dashboards;

import jakarta.annotation.Nullable;

import java.util.Map;

public record DashboardWidget(String id,
                              String type,
                              int x,
                              int y,
                              int w,
                              int h,
                              @Nullable Map<String, Object> options) {

    public enum Type {

        STATUS(2, 1),
        ALERTS(3, 1),
        TAPS(2, 1),
        METRIC(2, 1),
        CLOCK(2, 1),
        LOCATIONS(4, 1),
        TEXT(2, 1),
        TOPOLOGY(4, 2);

        private final int minimumWidth;
        private final int minimumHeight;

        Type(int minimumWidth, int minimumHeight) {
            this.minimumWidth = minimumWidth;
            this.minimumHeight = minimumHeight;
        }

        public int minimumWidth() {
            return minimumWidth;
        }

        public int minimumHeight() {
            return minimumHeight;
        }

    }

    public Type widgetType() {
        return Type.valueOf(type);
    }

    @Nullable
    public String option(String key) {
        Object value = options == null ? null : options.get(key);
        return value == null ? null : value.toString();
    }

}
