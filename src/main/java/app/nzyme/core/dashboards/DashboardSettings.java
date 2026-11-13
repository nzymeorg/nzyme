package app.nzyme.core.dashboards;

import app.nzyme.core.ethernet.topology.Topology;
import app.nzyme.core.ethernet.topology.TopologyFilters;
import app.nzyme.core.util.filters.FilterParser;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public record DashboardSettings(String name,
                                @Nullable String description,
                                Scope scope,
                                List<UUID> scopeIds,
                                int windowMinutes,
                                List<DashboardWidget> widgets) {

    public enum Scope { ALL, LOCATIONS, TAPS }

    public static final int MAXIMUM_NAME_LENGTH = 128;
    public static final int MAXIMUM_DESCRIPTION_LENGTH = 10000;
    public static final int MAXIMUM_SCOPE_IDS = 100;
    public static final int MAXIMUM_WIDGETS = 24;
    public static final int MAXIMUM_TITLE_LENGTH = 64;
    public static final int MAXIMUM_TEXT_LENGTH = 2000;
    public static final List<String> TEXT_SIZES = List.of("SMALL", "MEDIUM", "LARGE");
    public static final List<Subsystem> ALERT_SUBSYSTEMS = List.of(Subsystem.ETHERNET, Subsystem.DOT11,
            Subsystem.BLUETOOTH);
    public static final List<Integer> TOPOLOGY_CONNECTIONS = List.of(50, 100, 250);
    public static final int DEFAULT_TOPOLOGY_CONNECTIONS = 100;
    public static final int MAXIMUM_FILTERS_LENGTH = 8000;
    public static final int GRID_COLUMNS = 12;
    public static final int GRID_ROWS = 6;
    public static final List<Integer> WINDOWS_MINUTES = List.of(60, 240, 1440, 10080);
    public static final int DEFAULT_WINDOW_MINUTES = 1440;

    private static final Pattern WIDGET_ID = Pattern.compile("^[A-Za-z0-9-]{1,36}$");

    public static DashboardSettings validated(@Nullable String name,
                                              @Nullable String description,
                                              @Nullable String scope,
                                              @Nullable List<UUID> scopeIds,
                                              @Nullable Integer windowMinutes,
                                              @Nullable List<DashboardWidget> widgets) {
        String trimmedName = storable(name == null ? "" : name.trim());
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("A name is required.");
        }
        if (trimmedName.length() > MAXIMUM_NAME_LENGTH) {
            throw new IllegalArgumentException("The name cannot be longer than " + MAXIMUM_NAME_LENGTH + " characters.");
        }

        String trimmedDescription = description == null || description.trim().isEmpty()
                ? null : storable(description.trim());
        if (trimmedDescription != null && trimmedDescription.length() > MAXIMUM_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("The description is too long.");
        }

        Scope parsedScope;
        try {
            parsedScope = scope == null ? Scope.ALL : Scope.valueOf(scope.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The scope must be ALL, LOCATIONS or TAPS.");
        }

        Set<UUID> ids = Sets.newLinkedHashSet();
        if (parsedScope != Scope.ALL) {
            String things = parsedScope == Scope.LOCATIONS ? "locations" : "taps";
            for (UUID id : scopeIds == null ? List.<UUID>of() : scopeIds) {
                if (id != null) {
                    ids.add(id);
                }
                if (ids.size() > MAXIMUM_SCOPE_IDS) {
                    throw new IllegalArgumentException("A dashboard cannot cover more than " + MAXIMUM_SCOPE_IDS
                            + " selected " + things + ".");
                }
            }

            if (ids.isEmpty()) {
                throw new IllegalArgumentException("Select at least one of the " + things + ".");
            }
        }

        int window = windowMinutes == null ? DEFAULT_WINDOW_MINUTES : windowMinutes;
        if (!WINDOWS_MINUTES.contains(window)) {
            throw new IllegalArgumentException("The time window must be one of " + WINDOWS_MINUTES + " minutes.");
        }

        return new DashboardSettings(trimmedName, trimmedDescription, parsedScope, Lists.newArrayList(ids), window,
                validatedWidgets(widgets));
    }

    public boolean showsAlerts() {
        return widgets.stream().anyMatch(DashboardSettings::showsAlerts);
    }

    public static boolean showsAlerts(DashboardWidget widget) {
        return switch (widget.widgetType()) {
            case STATUS, ALERTS, LOCATIONS -> true;
            case METRIC -> DashboardMetric.valueOf(widget.option("metric")).showsAlerts();
            case TAPS, CLOCK, TEXT, TOPOLOGY -> false;
        };
    }

    public record Access(boolean allTaps, Set<UUID> locations, Set<UUID> taps, boolean alerts) { }

    /*
     * Viewers see everything a dashboard shows of its scope, so what it shows is decided by users who can access all
     * of the scope. Keeping or narrowing a scope hands out nothing new, and neither does a new name or layout.
     */
    public void requireChangeAllowed(@Nullable DashboardSettings existing, Supplier<Access> accessOfUser) {
        List<DashboardWidget> added = widgetsNotIn(existing);

        boolean scopeWidened = existing == null || existing.scope() != scope
                || !Sets.newHashSet(existing.scopeIds()).containsAll(scopeIds);
        boolean contentChanged = !added.isEmpty() || (existing != null && existing.windowMinutes() != windowMinutes);

        if (!scopeWidened && !contentChanged) {
            return;
        }

        Access access = accessOfUser.get();

        boolean accessible = switch (scope) {
            case ALL -> access.allTaps();
            case LOCATIONS -> access.locations().containsAll(scopeIds);
            case TAPS -> access.taps().containsAll(scopeIds);
        };

        if (!accessible) {
            throw new IllegalArgumentException(scopeWidened ? "The scope includes taps you cannot access."
                    : "This dashboard covers taps you cannot access. You can only change its name, description "
                    + "and layout.");
        }

        if (!access.alerts() && added.stream().anyMatch(DashboardSettings::showsAlerts)) {
            throw new IllegalArgumentException("Widgets that show alerts need the permission to view alerts.");
        }
    }

    // The widgets that show something the other settings do not show. Position, size and title do not count.
    public List<DashboardWidget> widgetsNotIn(@Nullable DashboardSettings other) {
        return widgets.stream()
                .filter(widget -> other == null || other.widgets().stream().noneMatch(w -> w.id().equals(widget.id())
                        && w.type().equals(widget.type()) && content(w).equals(content(widget))))
                .toList();
    }

    private static Map<String, String> content(DashboardWidget widget) {
        Map<String, String> content = Maps.newHashMap();
        if (widget.options() != null) {
            widget.options().forEach((key, value) -> {
                if (!key.equals("title")) {
                    content.put(key, String.valueOf(value));
                }
            });
        }

        return content;
    }

    // PostgreSQL stores no NUL character, neither in text nor in JSON.
    static String storable(String value) {
        if (value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("The text contains a character that cannot be stored.");
        }

        return value;
    }

    private static <E extends Enum<E>> E level(Class<E> levels, @Nullable String value, E fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Enum.valueOf(levels, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown node grouping of a topology widget.");
        }
    }

    private static List<DashboardWidget> validatedWidgets(@Nullable List<DashboardWidget> widgets) {
        List<DashboardWidget> result = Lists.newArrayList();
        if (widgets == null) {
            return result;
        }

        if (widgets.size() > MAXIMUM_WIDGETS) {
            throw new IllegalArgumentException("A dashboard cannot have more than " + MAXIMUM_WIDGETS + " widgets.");
        }

        Set<String> ids = Sets.newHashSet();
        for (DashboardWidget widget : widgets) {
            if (widget == null || widget.id() == null || !WIDGET_ID.matcher(widget.id()).matches()
                    || !ids.add(widget.id())) {
                throw new IllegalArgumentException("Every widget needs a unique ID.");
            }

            DashboardWidget.Type type;
            try {
                type = DashboardWidget.Type.valueOf(widget.type() == null ? "" : widget.type().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown widget type.");
            }

            if (widget.x() < 0 || widget.y() < 0 || widget.w() < 1 || widget.h() < 1
                    || widget.x() + widget.w() > GRID_COLUMNS || widget.y() + widget.h() > GRID_ROWS) {
                throw new IllegalArgumentException("A widget is placed outside of the grid.");
            }

            if (widget.w() < type.minimumWidth() || widget.h() < type.minimumHeight()) {
                throw new IllegalArgumentException("A widget is too small for its content.");
            }

            for (DashboardWidget other : result) {
                if (widget.x() < other.x() + other.w() && other.x() < widget.x() + widget.w()
                        && widget.y() < other.y() + other.h() && other.y() < widget.y() + widget.h()) {
                    throw new IllegalArgumentException("Two widgets overlap.");
                }
            }

            // Only known options are kept.
            Map<String, Object> options = Maps.newHashMap();

            String title = widget.option("title");
            if (title != null && !title.trim().isEmpty()) {
                if (title.trim().length() > MAXIMUM_TITLE_LENGTH) {
                    throw new IllegalArgumentException("A widget title cannot be longer than "
                            + MAXIMUM_TITLE_LENGTH + " characters.");
                }
                options.put("title", storable(title.trim()));
            }

            if (type == DashboardWidget.Type.METRIC) {
                try {
                    String metric = widget.option("metric");
                    options.put("metric", DashboardMetric.valueOf(metric == null ? "" : metric.toUpperCase()).name());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown metric.");
                }
            }

            if (type == DashboardWidget.Type.ALERTS || (type == DashboardWidget.Type.METRIC
                    && DashboardMetric.valueOf((String) options.get("metric")).showsAlerts())) {
                String subsystem = widget.option("subsystem");
                if (subsystem != null && !subsystem.trim().isEmpty()) {
                    try {
                        Subsystem parsed = Subsystem.valueOf(subsystem.trim().toUpperCase());
                        if (!ALERT_SUBSYSTEMS.contains(parsed)) {
                            throw new IllegalArgumentException();
                        }
                        options.put("subsystem", parsed.name());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("The subsystem of a widget must be one of "
                                + ALERT_SUBSYSTEMS + ".");
                    }
                }
            }

            if (type == DashboardWidget.Type.CLOCK) {
                String timeZone = widget.option("time_zone");
                if (timeZone == null || !ZoneId.getAvailableZoneIds().contains(timeZone.trim())) {
                    throw new IllegalArgumentException("Unknown time zone.");
                }
                options.put("time_zone", timeZone.trim());
            }

            if (type == DashboardWidget.Type.TEXT) {
                String text = widget.option("text");
                if (text == null || text.trim().isEmpty()) {
                    throw new IllegalArgumentException("A text widget needs a text.");
                }
                if (text.trim().length() > MAXIMUM_TEXT_LENGTH) {
                    throw new IllegalArgumentException("The text of a widget cannot be longer than "
                            + MAXIMUM_TEXT_LENGTH + " characters.");
                }
                options.put("text", storable(text.trim()));

                String size = widget.option("size");
                if (size != null && !TEXT_SIZES.contains(size.toUpperCase())) {
                    throw new IllegalArgumentException("The text size must be one of " + TEXT_SIZES + ".");
                }
                options.put("size", size == null ? "MEDIUM" : size.toUpperCase());
            }

            if (type == DashboardWidget.Type.TOPOLOGY) {
                options.put("internal_level", level(Topology.InternalLevel.class, widget.option("internal_level"),
                        Topology.InternalLevel.HOST).name());
                options.put("external_level", level(Topology.ExternalLevel.class, widget.option("external_level"),
                        Topology.ExternalLevel.HOST).name());

                String connections = widget.option("connections");
                int limit;
                try {
                    limit = connections == null ? DEFAULT_TOPOLOGY_CONNECTIONS : Integer.parseInt(connections.trim());
                } catch (NumberFormatException e) {
                    limit = -1;
                }
                if (!TOPOLOGY_CONNECTIONS.contains(limit)) {
                    throw new IllegalArgumentException("The connections of a topology widget must be one of "
                            + TOPOLOGY_CONNECTIONS + ".");
                }
                options.put("connections", limit);

                String filters = widget.option("filters");
                if (filters != null && !filters.trim().isEmpty()) {
                    if (filters.trim().length() > MAXIMUM_FILTERS_LENGTH) {
                        throw new IllegalArgumentException("The filters of a topology widget are too long.");
                    }
                    try {
                        FilterSql.generate(FilterParser.parseFiltersQueryParameter(filters.trim()), new TopologyFilters());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("The filters of a topology widget are not valid.");
                    }
                    options.put("filters", storable(filters.trim()));
                }
            }

            result.add(new DashboardWidget(widget.id(), type.name(), widget.x(), widget.y(), widget.w(), widget.h(),
                    options));
        }

        return result;
    }

}
