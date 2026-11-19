package app.nzyme.core.tables.uav;

import app.nzyme.core.designation.Designation;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.rest.resources.taps.reports.tables.uav.*;
import app.nzyme.core.shared.Classification;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.uav.UavRegistryKeys;
import app.nzyme.core.uav.db.UavEntry;
import app.nzyme.core.uav.db.UavTypeEntry;
import app.nzyme.core.uav.types.UavTypeMatch;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.collect.Maps;
import com.google.common.math.Quantiles;
import com.google.common.math.Stats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.*;
import java.util.stream.Collectors;

public class UAVTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(UAVTable.class);

    private final TablesService tablesService;

    private final Timer totalReportTimer;

    public UAVTable(TablesService tablesService) {
        this.tablesService = tablesService;

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.UAV_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime ignored, UavsReport report) {
        Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);

        if (tap.isEmpty()) {
            // Should be impossible.
            LOG.error("Could not find tap [{}].", tapUuid);
            return;
        }

        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            try(Timer.Context ignored2 = totalReportTimer.time()) {
                writeUavs(handle, tap.get(), report.uavs());
                alertUavs(tap.get(), report.uavs());
            }
        });
    }

    private void writeUavs(Handle handle, Tap tap, List<UavReport> uavs) {
        PreparedBatch insertBatch = handle.prepareBatch("INSERT INTO uavs(tap_uuid, identifier, " +
                "designation, uav_type, detection_source, id_serial, id_registration, id_utm, id_session, " +
                "operator_id, rssi_average, operational_status, latitude, longitude, ground_track, speed, " +
                "vertical_speed, altitude_pressure, altitude_geodetic, height_type, height, accuracy_horizontal, " +
                "accuracy_vertical, accuracy_barometer, accuracy_speed, operator_location_type, operator_latitude, " +
                "operator_longitude, operator_altitude, latest_vector_timestamp, latest_operator_location_timestamp, " +
                "first_seen, last_seen) VALUES(:tap_uuid, :identifier, :designation, :uav_type, " +
                ":detection_source, :id_serial, :id_registration, :id_utm, :id_session, :operator_id, :rssi_average, " +
                ":operational_status, :latitude, :longitude, :ground_track, :speed, :vertical_speed, " +
                ":altitude_pressure, :altitude_geodetic, :height_type, :height, :accuracy_horizontal, " +
                ":accuracy_vertical, :accuracy_barometer, :accuracy_speed, :operator_location_type, " +
                ":operator_latitude, :operator_longitude, :operator_altitude, :latest_vector_timestamp, " +
                ":latest_operator_location_timestamp, :first_seen, :last_seen)");

        PreparedBatch updateBatch = handle.prepareBatch("UPDATE uavs SET rssi_average = :rssi_average, " +
                "operational_status = :operational_status, latitude = :latitude, longitude = :longitude, " +
                "ground_track = :ground_track, speed = :speed, vertical_speed = :vertical_speed, " +
                "altitude_pressure = :altitude_pressure, altitude_geodetic = :altitude_geodetic, " +
                "height_type = :height_type, height = :height, accuracy_horizontal = :accuracy_horizontal, " +
                "accuracy_vertical = :accuracy_vertical, accuracy_barometer = :accuracy_barometer, " +
                "accuracy_speed = :accuracy_speed, operator_location_type = :operator_location_type, " +
                "operator_latitude = :operator_latitude, operator_longitude = :operator_longitude, " +
                "operator_altitude = :operator_altitude, latest_vector_timestamp = :latest_vector_timestamp, " +
                "latest_operator_location_timestamp = :latest_operator_location_timestamp, last_seen = :last_seen " +
                "WHERE id = :id");

        for (UavReport uav : uavs) {
            String designation = Designation.fromSha256ShortDigest(uav.identifier().subSequence(0, 7).toString());
            double rssiAverage = uav.rssis()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);

            String operatorId = uav.operatorIds()
                    .stream()
                    .findFirst()
                    .orElse(null);

            // Operational Status (Latest)
            String operationalStatus = null;
            Double latitude = null;
            Double longitude = null;
            Integer groundTrack = null;
            Double speed = null;
            Double verticalSpeed = null;
            Double altitudePressure = null;
            Double altitudeGeodetic = null;
            String heightType = null;
            Double height = null;
            Integer accuracyHorizontal = null;
            Integer accuracyVertical = null;
            Integer accuracyBarometer = null;
            Integer accuracySpeed = null;
            DateTime latestVectorTimestamp = null;
            if (!uav.vectorReports().isEmpty()) {
                UavVectorReport lastReport = uav.vectorReports().get(uav.vectorReports().size() - 1);
                operationalStatus = lastReport.operationalStatus();
                latitude = lastReport.latitude();
                longitude = lastReport.longitude();
                groundTrack = lastReport.groundTrack();
                altitudePressure = lastReport.altitudePressure();
                altitudeGeodetic = lastReport.altitudeGeodetic();
                heightType = lastReport.heightType();
                height = lastReport.height();
                latestVectorTimestamp = lastReport.timestamp();

                List<Double> speeds = uav.vectorReports().stream()
                        .map(UavVectorReport::speed)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (!speeds.isEmpty()) {
                    speed = Stats.meanOf(speeds);
                }

                List<Double> verticalSpeeds = uav.vectorReports().stream()
                        .map(UavVectorReport::verticalSpeed)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!verticalSpeeds.isEmpty()) {
                    verticalSpeed = Stats.meanOf(verticalSpeeds);
                }

                List<Integer> horizontalAccuracies = uav.vectorReports().stream()
                        .map(UavVectorReport::horizontalAccuracy)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!horizontalAccuracies.isEmpty()) {
                    accuracyHorizontal = (int) Quantiles.median().compute(horizontalAccuracies);
                }

                List<Integer> verticalAccuracies = uav.vectorReports().stream()
                        .map(UavVectorReport::verticalAccuracy)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!verticalAccuracies.isEmpty()) {
                    accuracyVertical = (int) Quantiles.median().compute(verticalAccuracies);
                }

                List<Integer> barometerAccuracies = uav.vectorReports().stream()
                        .map(UavVectorReport::barometerAccuracy)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!barometerAccuracies.isEmpty()) {
                    accuracyBarometer = (int) Quantiles.median().compute(barometerAccuracies);
                }

                List<Integer> speedAccuracies = uav.vectorReports().stream()
                        .map(UavVectorReport::speedAccuracy)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!speedAccuracies.isEmpty()) {
                    accuracySpeed = (int) Quantiles.median().compute(speedAccuracies);
                }
            }

            String operatorLocationType = null;
            Double operatorLatitude = null;
            Double operatorLongitude = null;
            Double operatorAltitude = null;
            DateTime latestOperatorLocationTimestamp = null;
            if (!uav.operatorLocationReports().isEmpty()) {
                UavOperatorLocationReport lastReport = uav.operatorLocationReports()
                        .get(uav.operatorLocationReports().size() - 1);
                operatorLocationType = lastReport.locationType();
                operatorLatitude = lastReport.latitude();
                operatorLongitude = lastReport.longitude();
                operatorAltitude = lastReport.altitude();
                latestOperatorLocationTimestamp = lastReport.timestamp();
            }

            String idSerial = null;
            String idRegistration = null;
            String idUtm = null;
            String idSession = null;
            for (UavIdReport uavId : uav.uavIds()) {
                switch (uavId.idType()) {
                    case "AnsiCtaSerial":
                        idSerial = uavId.id();
                        break;
                    case "CaaRegistrationId":
                        idRegistration = uavId.id();
                        break;
                    case "UtmAssignedUuid":
                        idUtm = uavId.id();
                        break;
                    case "SpecificSessionId":
                        idSession = uavId.id();
                        break;
                    default:
                        LOG.warn("Unknown UAV ID type [{}]", uavId.idType());
                }
            }

            Optional<Long> existingUav = handle.createQuery("SELECT id FROM uavs " +
                            "WHERE tap_uuid = :tap_uuid AND identifier = :identifier")
                    .bind("tap_uuid", tap.uuid())
                    .bind("identifier", uav.identifier())
                    .mapTo(Long.class)
                    .findOne();

            if (existingUav.isEmpty()) {
                // First time seeing this UAV from this tap.
                insertBatch
                        .bind("tap_uuid", tap.uuid())
                        .bind("identifier", uav.identifier())
                        .bind("designation", designation)
                        .bind("uav_type", uav.uavType())
                        .bind("detection_source", uav.detectionSource())
                        .bind("id_serial", idSerial)
                        .bind("id_registration", idRegistration)
                        .bind("id_utm", idUtm)
                        .bind("id_session", idSession)
                        .bind("operator_id", operatorId)
                        .bind("rssi_average", rssiAverage)
                        .bind("operational_status", operationalStatus)
                        .bind("latitude", latitude)
                        .bind("longitude", longitude)
                        .bind("ground_track", groundTrack)
                        .bind("speed", speed)
                        .bind("vertical_speed", verticalSpeed)
                        .bind("altitude_pressure", altitudePressure)
                        .bind("altitude_geodetic", altitudeGeodetic)
                        .bind("height_type", heightType)
                        .bind("height", height)
                        .bind("accuracy_horizontal", accuracyHorizontal)
                        .bind("accuracy_vertical", accuracyVertical)
                        .bind("accuracy_barometer", accuracyBarometer)
                        .bind("accuracy_speed", accuracySpeed)
                        .bind("operator_location_type", operatorLocationType)
                        .bind("operator_latitude", operatorLatitude)
                        .bind("operator_longitude", operatorLongitude)
                        .bind("operator_altitude", operatorAltitude)
                        .bind("latest_vector_timestamp", latestVectorTimestamp)
                        .bind("latest_operator_location_timestamp", latestOperatorLocationTimestamp)
                        .bind("first_seen", uav.firstSeen())
                        .bind("last_seen", uav.lastSeen())
                        .add();
            } else {
                updateBatch
                        .bind("id", existingUav.get())
                        .bind("rssi_average", rssiAverage)
                        .bind("operational_status", operationalStatus)
                        .bind("latitude", latitude)
                        .bind("longitude", longitude)
                        .bind("ground_track", groundTrack)
                        .bind("speed", speed)
                        .bind("vertical_speed", verticalSpeed)
                        .bind("altitude_pressure", altitudePressure)
                        .bind("altitude_geodetic", altitudeGeodetic)
                        .bind("height_type", heightType)
                        .bind("height", height)
                        .bind("accuracy_horizontal", accuracyHorizontal)
                        .bind("accuracy_vertical", accuracyVertical)
                        .bind("accuracy_barometer", accuracyBarometer)
                        .bind("accuracy_speed", accuracySpeed)
                        .bind("operator_location_type", operatorLocationType)
                        .bind("operator_latitude", operatorLatitude)
                        .bind("operator_longitude", operatorLongitude)
                        .bind("operator_altitude", operatorAltitude)
                        .bind("latest_vector_timestamp", latestVectorTimestamp)
                        .bind("latest_operator_location_timestamp", latestOperatorLocationTimestamp)
                        .bind("last_seen", uav.lastSeen())
                        .add();
            }
        }

        insertBatch.execute();
        updateBatch.execute();

        // Write vectors.
        PreparedBatch vectorBatch = handle.prepareBatch("INSERT INTO uavs_vectors(uav_id, " +
                "operational_status, latitude, longitude, ground_track, speed, vertical_speed, altitude_pressure, " +
                "altitude_geodetic, height_type, height, accuracy_horizontal, accuracy_vertical, " +
                "accuracy_barometer, accuracy_speed, timestamp) VALUES(:uav_id, :operational_status, " +
                ":latitude, :longitude, :ground_track, :speed, :vertical_speed, :altitude_pressure, " +
                ":altitude_geodetic, :height_type, :height, :accuracy_horizontal, :accuracy_vertical, " +
                ":accuracy_barometer, :accuracy_speed, :timestamp)");


        for (UavReport uav : uavs) {
            Optional<Long> existingUav = handle.createQuery("SELECT id FROM uavs " +
                            "WHERE tap_uuid = :tap_uuid AND identifier = :identifier")
                    .bind("tap_uuid", tap.uuid())
                    .bind("identifier", uav.identifier())
                    .mapTo(Long.class)
                    .findOne();

            if (existingUav.isEmpty()) {
                LOG.error("Could not find UAV to write additional data. UAV identifier: [{}]", uav.identifier());
                continue;
            }

            if (!uav.vectorReports().isEmpty()) {
                DateTime earliestVectorTimestamp = null;
                DateTime latestVectorTimestamp = null;
                for (UavVectorReport vector : uav.vectorReports()) {
                    vectorBatch
                            .bind("uav_id", existingUav.get())
                            .bind("operational_status", vector.operationalStatus())
                            .bind("latitude", vector.latitude())
                            .bind("longitude", vector.longitude())
                            .bind("ground_track", vector.groundTrack())
                            .bind("speed", vector.speed())
                            .bind("vertical_speed", vector.verticalSpeed())
                            .bind("altitude_pressure", vector.altitudePressure())
                            .bind("altitude_geodetic", vector.altitudeGeodetic())
                            .bind("height_type", vector.heightType())
                            .bind("height", vector.height())
                            .bind("accuracy_horizontal", vector.horizontalAccuracy())
                            .bind("accuracy_vertical", vector.verticalAccuracy())
                            .bind("accuracy_barometer", vector.barometerAccuracy())
                            .bind("accuracy_speed", vector.speedAccuracy())
                            .bind("timestamp", vector.timestamp())
                            .add();

                    if (earliestVectorTimestamp == null) {
                        earliestVectorTimestamp = vector.timestamp();
                    } else {
                        if (vector.timestamp().isBefore(earliestVectorTimestamp)) {
                            earliestVectorTimestamp = vector.timestamp();
                        }
                    }

                    if (latestVectorTimestamp == null) {
                        latestVectorTimestamp = vector.timestamp();
                    } else {
                        if (vector.timestamp().isAfter(latestVectorTimestamp)) {
                            latestVectorTimestamp = vector.timestamp();
                        }
                    }
                }

                // Do we have an existing and active timeline for this UAV?
                Optional<Long> timelineId = handle.createQuery("SELECT id FROM uavs_timelines " +
                                "WHERE uav_identifier = :uav_identifier " +
                                "AND tenant_id = :tenant_id AND organization_id = :organization_id " +
                                "AND seen_to >= NOW() - INTERVAL '5 minutes'")
                        .bind("uav_identifier", uav.identifier())
                        .bind("organization_id", tap.organizationId())
                        .bind("tenant_id", tap.tenantId())
                        .mapTo(Long.class)
                        .findOne();

                if (timelineId.isPresent()) {
                    // Update existing timeline.
                    handle.createUpdate("UPDATE uavs_timelines SET seen_to = :seen_to WHERE id = :id")
                            .bind("seen_to", latestVectorTimestamp)
                            .bind("id", timelineId.get())
                            .execute();
                } else {
                    // Create new timeline.
                    handle.createUpdate("INSERT INTO uavs_timelines(uav_identifier, organization_id, tenant_id, " +
                                    "uuid, seen_from, seen_to) VALUES(:uav_identifier, :organization_id, :tenant_id, " +
                                    ":uuid, :seen_from, :seen_to)")
                            .bind("uav_identifier", uav.identifier())
                            .bind("organization_id", tap.organizationId())
                            .bind("tenant_id", tap.tenantId())
                            .bind("seen_from", earliestVectorTimestamp)
                            .bind("seen_to", latestVectorTimestamp)
                            .bind("uuid", UUID.randomUUID())
                            .execute();
                }
            }

            vectorBatch.execute();
        }
    }

    private void alertUavs(Tap tap, List<UavReport> uavs) {
        boolean alertOnUnknown = tablesService.getNzyme().getDatabaseCoreRegistry()
                .getValue(UavRegistryKeys.MONITORING_ALERT_ON_UNKNOWN.key(), tap.organizationId(), tap.tenantId())
                .map(Boolean::parseBoolean).orElse(false);

        boolean alertOnFriendly = tablesService.getNzyme().getDatabaseCoreRegistry()
                .getValue(UavRegistryKeys.MONITORING_ALERT_ON_FRIENDLY.key(), tap.organizationId(), tap.tenantId())
                .map(Boolean::parseBoolean).orElse(false);

        boolean alertOnNeutral = tablesService.getNzyme().getDatabaseCoreRegistry()
                .getValue(UavRegistryKeys.MONITORING_ALERT_ON_NEUTRAL.key(), tap.organizationId(), tap.tenantId())
                .map(Boolean::parseBoolean).orElse(false);

        boolean alertOnHostile = tablesService.getNzyme().getDatabaseCoreRegistry()
                .getValue(UavRegistryKeys.MONITORING_ALERT_ON_HOSTILE.key(), tap.organizationId(), tap.tenantId())
                .map(Boolean::parseBoolean).orElse(false);

        List<UavTypeEntry> customTypes = tablesService.getNzyme().getUav()
                .findAllCustomTypes(tap.organizationId(), tap.tenantId());

        for (UavReport uav : uavs) {
            String serial = null;
            for (UavIdReport id : uav.uavIds()) {
                if (id.idType().equals("AnsiCtaSerial")) {
                    serial = id.id();
                }
            }

            Classification classification = null;
            if (serial != null) {
                Optional<UavTypeMatch> typeMatch = tablesService.getNzyme().getUav().matchUavType(customTypes, serial);

                if (typeMatch.isPresent() && typeMatch.get().defaultClassification() != null) {
                    classification = Classification.valueOf(typeMatch.get().defaultClassification());
                }
            }

            // No custom classification or no UAV serial. Pull custom classification.
            if (classification == null) {
                Optional<UavEntry> dbUav = tablesService.getNzyme().getUav()
                        .findUav(uav.identifier(), tap.organizationId(), tap.tenantId(), List.of(tap.uuid()));

                if (dbUav.isEmpty()) {
                    LOG.error("Skipping UAV with identifier [{}] not found in database for tap [{}].",
                            uav.identifier(), tap.uuid());
                    continue;
                }

                classification = Classification.valueOf(dbUav.get().classification());
            }

            DetectionType detectionType;
            boolean doAlert;
            switch (classification) {
                case UNKNOWN -> {
                    detectionType = DetectionType.UAV_DETECTED_CLASSIFICATION_UNKNOWN;
                    doAlert = alertOnUnknown;
                }
                case FRIENDLY -> {
                    detectionType = DetectionType.UAV_DETECTED_CLASSIFICATION_FRIENDLY;
                    doAlert = alertOnFriendly;
                }
                case HOSTILE -> {
                    detectionType = DetectionType.UAV_DETECTED_CLASSIFICATION_HOSTILE;
                    doAlert = alertOnHostile;
                }
                case NEUTRAL -> {
                    detectionType = DetectionType.UAV_DETECTED_CLASSIFICATION_NEUTRAL;
                    doAlert = alertOnNeutral;
                }
                default -> throw new IllegalStateException("Unexpected value: " + classification);
            }

            if (doAlert) {
                raiseUavAlert(tap, uav.identifier(), uav.detectionSource(), classification.toString(), detectionType);
            }
        }
    }

    private void raiseUavAlert(Tap tap, String identifier, String detectionSource, String classification, DetectionType detectionType) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("identifier", identifier);
        attributes.put("classification", classification);
        attributes.put("detection_source", detectionSource);

        String designation = Designation.fromSha256ShortDigest(identifier.subSequence(0, 7).toString());

        tablesService.getNzyme().getDetectionAlertService().raiseAlert(
                tap.organizationId(),
                tap.tenantId(),
                null,
                AlertOrigin.tap(tap.uuid()),
                detectionType,
                Subsystem.UAV,
                "UAV with " + classification + " classification and designation [" + designation + "] detected in range.",
                attributes,
                Set.of("identifier", "classification")
        );
    }

    @Override
    public void retentionClean() {
        // NOOP. Remove from plugin APIs if there remains no use. Database cleaned by category/tenant independently.
    }
}
