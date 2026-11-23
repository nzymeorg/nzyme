package app.nzyme.core.environment;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.environment.dto.EnvironmentData;
import app.nzyme.core.environment.dto.LocationEnvironmentAlertDetails;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.validation.constraints.NotNull;
import net.iakovlev.timeshape.TimeZoneEngine;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public class EnvironmentService {

    private static final Logger LOG = LogManager.getLogger(EnvironmentService.class);

    public static final int MIN_ALERT_SEVERITY = 3;

    public static int severityOrdinal(@NotNull String severity) {
        return switch (severity) {
            case "Extreme"-> 4;
            case "Severe" -> 3;
            case "Moderate" -> 2;
            case "Minor" -> 1;
            default -> 0;  // Unknown
        };
    }

    private static final int ALERT_FUTURE_WINDOW_HOURS = 12;

    private static final int FETCH_PARALLELISM = 4;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .build();

    private static final ObjectMapper OM = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModule(new JodaModule())
            .build();

    private static class EnvironmentDataDisabledException extends Exception { }

    private final NzymeNode nzyme;

    private final ScheduledExecutorService refresher;
    private final ReentrantLock lock = new ReentrantLock();

    // Can be disabled if Connect is not set up or environment data source is not enabled in Connect.
    private boolean isEnabled = false;

    private Map<UUID, EnvironmentData> environmentData;

    private final TimeZoneEngine timeZoneEngine;

    public EnvironmentService(NzymeNode nzyme) {
        this.nzyme = nzyme;

        this.timeZoneEngine = TimeZoneEngine.initialize();

        // Reload on configuration change.
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.CONNECT_API_KEY.key(), this::reload);
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.CONNECT_ENABLED.key(), this::reload);

        // Reload if provided services by Connect change.
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.PROVIDED_SERVICES.key(), this::reload);

        refresher = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("environment-refresher-%d")
                        .build()
        );

        refresher.scheduleAtFixedRate(this::reload, 30, 30, TimeUnit.MINUTES);
    }

    private void reload() {
        // Reload with new registry settings.
        initialize();
    }

    public void initialize() {
        // IMPORTANT: This method will also be called on configuration changes and cache invalidations.

        this.isEnabled = nzyme.getConnect().isEnabled();
        if (!this.isEnabled) {
            return;
        }

        lock.lock();

        try {
            Optional<Map<UUID, EnvironmentData>> data = fetchEnvironmentFromConnect();

            // Check if environment data was disabled in Connect for this cluster.
            if (data.isEmpty()) {
                this.isEnabled = false;
                return;
            }

            this.isEnabled = true;
            this.environmentData = data.get();
        } catch (Exception e) {
            LOG.error("Could not download environment data from Connect.", e);
            this.isEnabled = false;
        } finally {
            lock.unlock();
        }
    }

    private Optional<Map<UUID, EnvironmentData>> fetchEnvironmentFromConnect() {
        List<TenantLocationEntry> locations = Lists.newArrayList();
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
            for (TenantLocationEntry location : nzyme.getAuthenticationService()
                    .findAllTenantLocations(tenant.organizationUuid(), tenant.uuid(), Integer.MAX_VALUE, 0)) {
                if (location.latitude() == null || location.longitude() == null
                        || location.latitude() == 0.0 || location.longitude() == 0.0) {
                    continue;
                }

                locations.add(location);
            }
        }

        Map<UUID, EnvironmentData> data = Maps.newHashMap();
        if (locations.isEmpty()) {
            return Optional.of(data);
        }

        ExecutorService fetchers = Executors.newFixedThreadPool(
                Math.min(FETCH_PARALLELISM, locations.size()),
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("environment-fetcher-%d")
                        .build()
        );

        try {
            List<Future<Optional<EnvironmentData>>> fetches = Lists.newArrayList();
            for (TenantLocationEntry location : locations) {
                fetches.add(fetchers.submit(() -> fetchLocationFromConnect(location)));
            }

            // Alerts are raised here and not by the fetchers, one location after the other.
            for (int i = 0; i < locations.size(); i++) {
                TenantLocationEntry location = locations.get(i);

                Optional<EnvironmentData> environment;
                try {
                    environment = fetches.get(i).get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof EnvironmentDataDisabledException) {
                        // Environment data disabled in Connect for this cluster.
                        return Optional.empty();
                    }

                    LOG.error("Could not download environment data from Connect for location [{}].",
                            location.uuid(), e.getCause());
                    continue;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.of(data);
                }

                if (environment.isEmpty()) {
                    // No environmental data available for location.
                    continue;
                }

                data.put(location.uuid(), environment.get());

                try {
                    raiseAlerts(location, environment.get());
                } catch (Exception e) {
                    LOG.error("Could not raise environment alerts for location [{}].", location.uuid(), e);
                }
            }
        } finally {
            fetchers.shutdownNow();
        }

        return Optional.of(data);
    }

    private Optional<EnvironmentData> fetchLocationFromConnect(TenantLocationEntry location) throws Exception {
        LOG.debug("Loading new environment data from Connect for location [{}].", location.uuid());

        HttpUrl url = HttpUrl.get(nzyme.getConnect().getApiUri())
                .newBuilder()
                .addPathSegment("data")
                .addPathSegment("environment")
                .addPathSegment("location")
                .addQueryParameter("latitude", String.valueOf(location.latitude()))
                .addQueryParameter("longitude", String.valueOf(location.longitude()))
                .build();

        Response response = HTTP.newCall(new Request.Builder()
                .get()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nzyme.getConnect().getApiKey())
                .addHeader("Content-Type", "application/json")
                .addHeader(HttpHeaders.USER_AGENT, "nzyme-node")
                .build()
        ).execute();

        try (response) {
            if (!response.isSuccessful()) {
                if (response.code() == 403) {
                    throw new EnvironmentDataDisabledException();
                }

                if (response.code() == 400) {
                    return Optional.empty();
                }

                throw new RuntimeException("Expected HTTP 200 or 403 but got HTTP " + response.code());
            }

            LOG.debug("Environment data download from Connect complete.");

            return Optional.of(OM.readValue(response.body().bytes(), EnvironmentData.class));
        }
    }

    private void raiseAlerts(TenantLocationEntry location, EnvironmentData environment) {
        if (!location.environmentalAlertEventingEnabled() || environment.alerts().isEmpty()) {
            return;
        }

        for (LocationEnvironmentAlertDetails alert : environment.alerts()) {
            if (alert.severity() != null && severityOrdinal(alert.severity()) < MIN_ALERT_SEVERITY) {
                continue;
            }

            if (!alertIsCurrentlyRelevant(alert)) {
                continue;
            }

            String headline = alert.event() == null ? "Unknown Alert Type" : alert.event();
            String sender = alert.senderName() == null ? "Unknown" : alert.senderName();
            String severity = alert.severity() == null ? "Unknown" : alert.severity();
            String urgency = alert.urgency() == null ? "Unknown" : alert.urgency();
            String certainty = alert.certainty() == null ? "Unknown" : alert.certainty();
            String effective = alert.effective() == null ? "Unknown" : alert.effective().toString();

            String alertKey = Hashing.sha256()
                    .hashString(location.uuid() + headline + alert.effective(), StandardCharsets.UTF_8)
                    .toString();

            nzyme.getDetectionAlertService().raiseAlert(
                    location.organizationId(),
                    location.tenantId(),
                    null,
                    AlertOrigin.location(location.uuid()),
                    DetectionType.ENVIRONMENTAL_SEVERE_ALERT,
                    Subsystem.GENERIC,
                    "Severe environmental/weather monitoring alert received for location \"" + location.name() + "\": " + headline,
                    Map.of(
                            "alert_key", alertKey,
                            "headline", headline,
                            "sender", sender,
                            "severity", severity,
                            "urgency", urgency,
                            "certainty", certainty,
                            "effective", effective,
                            "location_uuid", location.uuid().toString(),
                            "location_name", location.name()
                    ),
                    Set.of("alert_key")
            );
        }
    }

    public Optional<EnvironmentData> getEnvironmentData(UUID locationId) {
        if (!isEnabled) {
            return Optional.empty();
        }

        EnvironmentData data = environmentData.get(locationId);

        if (data == null) {
            return Optional.empty();
        } else {
            return Optional.of(data);
        }
    }

    public Optional<ZoneId> getTimezoneAtCoordinates(double longitude, double latitude) {
        return timeZoneEngine.query(latitude, longitude);
    }

    public static boolean alertIsCurrentlyRelevant(LocationEnvironmentAlertDetails a) {
        DateTime now = DateTime.now();

        // Message already expired.
        if (a.expires() != null && a.expires().isBefore(now)) {
            return false;
        }

        // Event itself already ended (when known).
        if (a.ends() != null && a.ends().isBefore(now)) {
            return false;
        }

        // Event starts further out than our display window.
        if (a.effective() != null && a.effective().isAfter(now.plusHours(ALERT_FUTURE_WINDOW_HOURS))) {
            return false;
        }

        return true;
    }

}
