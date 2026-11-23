package app.nzyme.core.uav;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectDataset;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.shared.Classification;
import app.nzyme.core.uav.db.UavEntry;
import app.nzyme.core.uav.db.UavTimelineEntry;
import app.nzyme.core.uav.db.UavTypeEntry;
import app.nzyme.core.uav.db.UavVectorEntry;
import app.nzyme.core.uav.types.*;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.TimeRange;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.validation.constraints.NotNull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class Uav {

    private static final Logger LOG = LogManager.getLogger(Uav.class);

    private final NzymeNode nzyme;

    private final Timer connectModelLookupTimer;

    private final ScheduledExecutorService connectModelsRefresher;

    private final ConnectDataset connectModelsDataset;

    private final ReentrantLock connectModelsLock = new ReentrantLock();
    private List<ConnectUavModel> connectModels = List.of();

    // Can be disabled if Connect is not set up or UAV models data source is not enabled in Connect.
    private volatile boolean connectModelsIsEnabled = false;

    public Uav(NzymeNode nzyme) {
        this.nzyme = nzyme;

        this.connectModelLookupTimer = nzyme.getMetrics().timer(MetricRegistry.name(MetricNames.UAV_CONNECT_TYPE_LOOKUP_TIMING));

        ObjectMapper om = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        this.connectModelsDataset = ConnectDataset.create(nzyme, "uav-models", List.of("data", "uav", "models"), false,
                new ConnectDataset.Consumer() {
                    @Override
                    public void load(Path file) {
                        List<ConnectUavModel> models = Lists.newArrayList();
                        for (ConnectUavModelDetailsResponse model : om.readValue(file.toFile(), ConnectUavModelListResponse.class).models()) {
                            models.add(ConnectUavModel.create(
                                    model.masterId(),
                                    model.make(),
                                    model.model(),
                                    model.fccId(),
                                    model.classification(),
                                    model.serialType(),
                                    model.serial()
                            ));
                        }

                        setConnectModels(models);
                    }

                    @Override
                    public void unload() {
                        setConnectModels(List.of());
                    }
                }
        );

        // Reload connect models on configuration change.
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.CONNECT_API_KEY.key(), this::reloadConnectModels);
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.CONNECT_ENABLED.key(), this::reloadConnectModels);

        // Reload if provided services by Connect change.
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.PROVIDED_SERVICES.key(), this::reloadConnectModels);

        connectModelsRefresher = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("uav-connect-refresher-%d")
                        .build()
        );

        connectModelsRefresher.scheduleAtFixedRate(this::reloadConnectModels, 1, 1, TimeUnit.HOURS);
    }

    private void setConnectModels(List<ConnectUavModel> models) {
        connectModelsLock.lock();

        try {
            this.connectModels = models;
        } finally {
            connectModelsLock.unlock();
        }
    }

    private synchronized void reloadConnectModels() {
        try {
            if (!nzyme.getConnect().isEnabled()) {
                this.connectModelsIsEnabled = false;
                connectModelsDataset.delete();
                return;
            }

            this.connectModelsIsEnabled = connectModelsDataset.refresh();
        } catch (Exception e) {
            LOG.error("Could not reload UAV model data.", e);
        }
    }

    public synchronized void initializeConnectModels() {
        if (!nzyme.getConnect().isEnabled()) {
            connectModelsDataset.delete();
            return;
        }

        if (connectModelsDataset.loadFromDisk()) {
            this.connectModelsIsEnabled = true;
            connectModelsRefresher.execute(this::reloadConnectModels);
        } else {
            reloadConnectModels();
        }
    }

    public long countAllUavs(TimeRange timeRange, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT identifier) FROM uavs " +
                                "WHERE last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "AND tap_uuid IN (<taps>)")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UavEntry> findAllUavsOfTenant(TimeRange timeRange,
                                              int limit,
                                              int offset,
                                              UUID organizationId,
                                              UUID tenantId,
                                              List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM (SELECT DISTINCT ON (u.identifier) *, " +
                                "c.classification AS classification FROM uavs AS u " +
                                "LEFT JOIN uavs_classifications AS c ON c.uav_identifier = u.identifier " +
                                "AND c.organization_id = :organization_id AND c.tenant_id = :tenant_id " +
                                "WHERE u.last_seen >= :tr_from AND u.last_seen <= :tr_to AND u.tap_uuid IN (<taps>) " +
                                "ORDER BY u.identifier, u.last_seen DESC) AS sub ORDER BY sub.last_seen DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(UavEntry.class)
                        .list()
        );
    }

    public List<UavEntry> findAllUavsOfTenant(TimeRange timeRange, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT ON (u.identifier) *, c.classification AS classification FROM uavs AS u " +
                                "LEFT JOIN uavs_classifications AS c ON c.uav_identifier = u.identifier " +
                                "AND c.organization_id = :organization_id AND c.tenant_id = :tenant_id " +
                                "WHERE u.last_seen >= :tr_from AND u.last_seen <= :tr_to")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(UavEntry.class)
                        .list()
        );
    }

    public Optional<UavEntry> findUav(String identifier,
                                      UUID organizationId,
                                      UUID tenantId,
                                      List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT *, c.classification AS classification FROM uavs AS u  " +
                                "LEFT JOIN uavs_classifications AS c ON c.uav_identifier = u.identifier " +
                                "AND c.organization_id = :organization_id AND c.tenant_id = :tenant_id " +
                                "WHERE u.identifier = :identifier AND u.tap_uuid IN (<taps>) " +
                                "ORDER BY u.last_seen DESC LIMIT 1")
                        .bind("identifier", identifier)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("taps", taps)
                        .mapTo(UavEntry.class)
                        .findOne()
        );
    }

    public long countTimelines(String identifier,
                               TimeRange timeRange,
                               @NotNull UUID organizationId,
                               @NotNull UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM uavs_timelines AS t " +
                                "WHERE t.seen_to >= :tr_from AND t.seen_to <= :tr_to " +
                                "AND t.uav_identifier = :identifier AND t.organization_id = :organization_id " +
                                "AND t.tenant_id = :tenant_id")
                        .bind("identifier", identifier)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UavTimelineEntry> findUavTimelines(String identifier,
                                                   TimeRange timeRange,
                                                   @NotNull UUID organizationId,
                                                   @NotNull UUID tenantId,
                                                   int limit,
                                                   int offset) {
        return nzyme.getDatabase().withHandle(handle ->
            handle.createQuery("SELECT t.seen_from, t.seen_to, t.uuid FROM uavs_timelines AS t " +
                            "WHERE t.seen_to >= :tr_from AND t.seen_to <= :tr_to " +
                            "AND t.uav_identifier = :identifier AND t.organization_id = :organization_id " +
                            "AND t.tenant_id = :tenant_id " +
                            "ORDER BY t.seen_to DESC LIMIT :limit OFFSET :offset")
                    .bind("identifier", identifier)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("limit", limit)
                    .bind("offset", offset)
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to())
                    .mapTo(UavTimelineEntry.class)
                    .list()
        );
    }

    public Optional<UavTimelineEntry> findUavTimeline(String uavIdentifier,
                                                      UUID timelineId,
                                                      @NotNull UUID organizationId,
                                                      @NotNull UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT t.id, t.seen_from, t.seen_to, t.uuid FROM uavs_timelines AS t " +
                                "WHERE t.uuid = :timeline_uuid AND t.uav_identifier = :identifier " +
                                "AND t.organization_id = :organization_id AND t.tenant_id = :tenant_id " +
                                "ORDER BY t.seen_to DESC LIMIT 1")
                        .bind("timeline_uuid", timelineId)
                        .bind("identifier", uavIdentifier)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(UavTimelineEntry.class)
                        .findOne()
        );
    }

    public List<UavVectorEntry> findVectorsOfTimeline(String uavIdentifier,
                                                      @NotNull UUID organizationId,
                                                      @NotNull UUID tenantId,
                                                      DateTime from,
                                                      DateTime to) {


        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM uavs_vectors AS v " +
                                "LEFT JOIN uavs AS u ON v.uav_id = u.id " +
                                "LEFT JOIN taps AS t ON u.tap_uuid = t.uuid " +
                                "WHERE u.identifier = :identifier AND " +
                                "t.organization_id = :organization_id AND t.tenant_id = :tenant_id AND " +
                                "v.timestamp >= :from AND v.timestamp <= :to " +
                                "ORDER BY v.timestamp ASC LIMIT 2000")
                        .bind("identifier", uavIdentifier)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("from", from)
                        .bind("to", to)
                        .mapTo(UavVectorEntry.class)
                        .list()
        );
    }

    public void setUavClassification(String identifier,
                                     UUID organizationId,
                                     UUID tenantId,
                                     Classification classification) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO uavs_classifications(uav_identifier, classification, " +
                                "organization_id, tenant_id) VALUES(:uav_identifier, :classification, " +
                                ":organization_id, :tenant_id) " +
                                "ON CONFLICT (uav_identifier, organization_id, tenant_id) " +
                                "DO UPDATE SET classification = EXCLUDED.classification")
                        .bind("uav_identifier", identifier)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("classification", classification)
                        .execute()
        );
    }

    public long countAllCustomTypes(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM uavs_types " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<UavTypeEntry> findAllCustomTypes(UUID organizationId, UUID tenantId, int limit, int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM uavs_types " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "ORDER BY name DESC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(UavTypeEntry.class)
                        .list()
        );
    }

    public List<UavTypeEntry> findAllCustomTypes(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM uavs_types " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(UavTypeEntry.class)
                        .list()
        );
    }

    public Optional<UavTypeEntry> findCustomType(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM uavs_types WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(UavTypeEntry.class)
                        .findOne()
        );
    }

    public void createCustomType(UUID organizationId,
                                 UUID tenantId,
                                 UavTypeMatchType matchType,
                                 String matchValue,
                                 Classification defaultClassification,
                                 String type,
                                 String name,
                                 String model) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO uavs_types(uuid, organization_id, tenant_id, match_type, " +
                                "match_value, default_classification, type, name, model, created_at, updated_at) " +
                                "VALUES(:uuid, :organization_id, :tenant_id, :match_type, :match_value, " +
                                ":default_classification, :type, :name, :model, NOW(), NOW())")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("match_type", matchType)
                        .bind("match_value", matchValue)
                        .bind("default_classification", defaultClassification)
                        .bind("type", type)
                        .bind("name", name)
                        .bind("model", model)
                        .execute()
        );
    }

    public void updateCustomType(long id,
                                 UavTypeMatchType matchType,
                                 String matchValue,
                                 Classification defaultClassification,
                                 String type,
                                 String name,
                                 String model
    ) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE uavs_types SET match_type = :match_type, " +
                                "match_value = :match_value, default_classification = :default_classification, " +
                                "type = :type, name = :name, model = :model, updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("match_type", matchType)
                        .bind("match_value", matchValue)
                        .bind("default_classification", defaultClassification)
                        .bind("type", type)
                        .bind("name", name)
                        .bind("model", model)
                        .execute()
        );
    }

    public void deleteCustomType(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM uavs_types WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }
    
    public Optional<UavTypeMatch> matchUavType(String serial, UUID tenantId, UUID organizationId) {
        return matchUavType(findAllCustomTypes(organizationId, tenantId), serial);
    }

    public Optional<UavTypeMatch> matchUavType(List<UavTypeEntry> types, String serial) {
        if (serial  == null) {
            return Optional.empty();
        }

        for (UavTypeEntry type : types) {
            UavTypeMatchType matchType = UavTypeMatchType.valueOf(type.matchType());

            switch (matchType) {
                case EXACT -> {
                    if (serial.equals(type.matchValue())) {
                        return Optional.of(UavTypeMatch.create(
                                type.type(), type.name(), type.model(), type.defaultClassification())
                        );
                    }
                }
                case PREFIX -> {
                    if (serial.startsWith(type.matchValue())) {
                        return Optional.of(UavTypeMatch.create(
                                type.type(), type.name(), type.model(), type.defaultClassification())
                        );
                    }
                }
            }
        }

        // Check Connect models.
        try(Timer.Context ignored = connectModelLookupTimer.time()) {
            Optional<List<ConnectUavModel>> models = findAllConnectUavModels();

            if (models.isPresent()) {
                for (ConnectUavModel connectModel : models.get()) {
                    switch (connectModel.serialType()) {
                        case RANGE -> {
                            String[] serialParts = connectModel.serial().split("-");

                            if (serialParts.length != 2) {
                                LOG.error("Unexpected RANGE type Connect serial: {}", connectModel.serial());
                                continue;
                            }

                            String lowerBound = serialParts[0];
                            String upperBound = serialParts[1];

                            if (serial.compareTo(lowerBound) >= 0 &&
                                    serial.compareTo(upperBound) <= 0) {
                                return Optional.of(UavTypeMatch.create(
                                        connectModel.classification() == null ? "Unknown" : connectModel.classification(),
                                        null,
                                        connectModel.make() + " " + connectModel.model(),
                                        null
                                ));
                            }
                        }
                        case ANSICTA2063A -> {
                            if (serial.equals(connectModel.serial())) {
                                return Optional.of(UavTypeMatch.create(
                                        connectModel.classification() == null ? "Unknown" : connectModel.classification(),
                                        null,
                                        connectModel.make() + " " + connectModel.model(),
                                        null
                                ));
                            }
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Integer> countAllConnectUavModels() {
        if (!connectModelsIsEnabled || connectModels == null) {
            return Optional.empty();
        }

        connectModelsLock.lock();

        try {
            return Optional.of(connectModels.size());
        } finally {
            connectModelsLock.unlock();
        }
    }

    public Optional<List<ConnectUavModel>> findAllConnectUavModels() {
        if (!connectModelsIsEnabled || connectModels == null) {
            return Optional.empty();
        }

        connectModelsLock.lock();

        try {
            return Optional.of(Lists.newArrayList(connectModels));
        } finally {
            connectModelsLock.unlock();
        }
    }

    public Optional<List<ConnectUavModel>> findAllConnectUavModels(int limit, int offset) {
        if (!connectModelsIsEnabled || connectModels == null) {
            return Optional.empty();
        }

        connectModelsLock.lock();

        try {
            return Optional.of(connectModels.stream()
                    .sorted(Comparator.comparing(ConnectUavModel::make))
                    .skip(offset)
                    .limit(limit)
                    .collect(Collectors.toList()));
        } finally {
            connectModelsLock.unlock();
        }
    }
}