package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.util.TenantCacheKey;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.RegistryKey;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Segmentation {

    private final NzymeNode nzyme;

    private final Segments segments;
    private final Exclusions exclusions;
    private final Policies policies;
    private final Findings findings;
    private final Flows flows;
    private final SegmentationViews views;

    private final Cache<TenantCacheKey, MembershipResolver> resolvers = CacheBuilder.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    private final Cache<TenantCacheKey, List<UUID>> taps = CacheBuilder.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    public Segmentation(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.segments = new Segments(nzyme, this);
        this.exclusions = new Exclusions(nzyme, this);
        this.policies = new Policies(nzyme, this);
        this.findings = new Findings(nzyme, this);
        this.flows = new Flows(nzyme);
        this.views = new SegmentationViews(nzyme, this);
    }

    public Segments segments() {
        return segments;
    }

    public Exclusions exclusions() {
        return exclusions;
    }

    public Policies policies() {
        return policies;
    }

    public Findings findings() {
        return findings;
    }

    public Flows flows() {
        return flows;
    }

    public SegmentationViews views() {
        return views;
    }

    public SegmentationConfiguration getConfiguration(UUID organizationId, UUID tenantId) {
        return new SegmentationConfiguration(
                Boolean.parseBoolean(value(SegmentationRegistryKeys.ENABLED, organizationId, tenantId)),
                Integer.parseInt(value(SegmentationRegistryKeys.SETTLE_SECONDS, organizationId, tenantId)),
                Integer.parseInt(value(SegmentationRegistryKeys.INITIAL_BACKFILL_HOURS, organizationId, tenantId)),
                Integer.parseInt(value(SegmentationRegistryKeys.FLOW_RETENTION_TIME_DAYS, organizationId, tenantId)),
                Integer.parseInt(value(SegmentationRegistryKeys.FINDING_RETENTION_TIME_DAYS, organizationId, tenantId))
        );
    }

    private String value(RegistryKey key, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabaseCoreRegistry().getValue(key.key(), organizationId, tenantId)
                .orElse(key.defaultValue().orElseThrow());
    }

    public MembershipResolver resolver(UUID organizationId, UUID tenantId) {
        TenantCacheKey key = TenantCacheKey.create(organizationId, tenantId);
        MembershipResolver cached = resolvers.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        MembershipResolver resolver = nzyme.getDatabase().withHandle(handle ->
                buildResolver(handle, organizationId, tenantId, policies.findAll(handle, organizationId, tenantId), null));
        resolvers.put(key, resolver);

        return resolver;
    }

    public MembershipResolver.AnyTap segmentOfAddress(UUID organizationId, UUID tenantId, InetAddress address, @Nullable Long asn) {
        if (PolicyMatcher.isBroadcastAddress(address) || address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            return MembershipResolver.AnyTap.NONE;
        }

        TenantCacheKey key = TenantCacheKey.create(organizationId, tenantId);
        List<UUID> tenantTaps = taps.getIfPresent(key);
        if (tenantTaps == null) {
            tenantTaps = Tools.getTapUuids(nzyme, organizationId, tenantId);
            taps.put(key, tenantTaps);
        }

        return resolver(organizationId, tenantId).resolveForAnyTap(tenantTaps, address, asn);
    }

    public MembershipResolver buildResolver(Handle handle,
                                            UUID organizationId,
                                            UUID tenantId,
                                            Collection<Policy> policies,
                                            @Nullable Set<UUID> additionalAssets) {
        List<Segment> all = segments.findAll(handle, organizationId, tenantId);

        Set<UUID> assets = Policies.referencedAssets(policies);
        if (additionalAssets != null) {
            assets.addAll(additionalAssets);
        }

        return new MembershipResolver(
                all,
                nzyme.getAssetsManager().tags().resolveAddresses(handle, organizationId, tenantId, assets)
        );
    }

    public void invalidateResolver(UUID organizationId, UUID tenantId) {
        resolvers.invalidate(TenantCacheKey.create(organizationId, tenantId));
    }

    // The time of the request is the time of the write and not the start of the transaction: the write waits for an
    // engine that holds the row, and a request stamped before that engine began would look served.
    public void requestRestamp(Handle handle, UUID organizationId, UUID tenantId) {
        handle.createUpdate("INSERT INTO segmentation_engine_state(organization_id, tenant_id, " +
                        "restamp_requested_at, updated_at) VALUES(:organization_id, :tenant_id, " +
                        "clock_timestamp(), NOW()) ON CONFLICT (organization_id, tenant_id) DO UPDATE " +
                        "SET restamp_requested_at = clock_timestamp(), updated_at = NOW()")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .execute();
    }

    public static String lockKey(UUID organizationId, UUID tenantId) {
        return "segmentation:" + organizationId + ":" + tenantId;
    }

    public EngineState state(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> state(handle, organizationId, tenantId, false));
    }

    // Only the engine, which locks the row, creates it. Reading the state of a tenant stores nothing.
    public EngineState state(Handle handle, UUID organizationId, UUID tenantId, boolean lock) {
        if (lock) {
            handle.createUpdate("INSERT INTO segmentation_engine_state(organization_id, tenant_id, updated_at) " +
                            "VALUES(:organization_id, :tenant_id, NOW()) " +
                            "ON CONFLICT (organization_id, tenant_id) DO NOTHING")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .execute();
        }

        return handle.createQuery("SELECT * FROM segmentation_engine_state WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id" + (lock ? " FOR UPDATE" : ""))
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> new EngineState(
                        dateTime(rs, "watermark"),
                        dateTime(rs, "carryover_bucket"),
                        dateTime(rs, "restamp_requested_at"),
                        dateTime(rs, "restamp_completed_at"),
                        dateTime(rs, "last_run_at"),
                        number(rs, "last_run_duration_ms"),
                        number(rs, "last_run_sessions"),
                        number(rs, "last_run_flows"),
                        number(rs, "last_run_findings"),
                        dateTime(rs, "limit_reached_at")
                ))
                .findOne()
                .orElse(new EngineState(null, null, null, null, null, null, null, null, null, null));
    }

    public record Progress(UUID organizationId, UUID tenantId, DateTime watermark, DateTime updatedAt) { }

    // Tenants without reporting taps have nothing to evaluate and say nothing about the engine.
    public List<Progress> progress() {
        List<Progress> all = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT s.organization_id, s.tenant_id, s.watermark, s.updated_at " +
                                "FROM segmentation_engine_state AS s WHERE s.watermark IS NOT NULL " +
                                "AND EXISTS (SELECT 1 FROM taps AS t WHERE t.organization_id = s.organization_id " +
                                "AND t.tenant_id = s.tenant_id AND t.last_report > NOW() - INTERVAL '5 minutes')")
                        .map((rs, ctx) -> new Progress(
                                UUID.fromString(rs.getString("organization_id")),
                                UUID.fromString(rs.getString("tenant_id")),
                                new DateTime(rs.getTimestamp("watermark")),
                                new DateTime(rs.getTimestamp("updated_at"))
                        ))
                        .list()
        );

        return all.stream()
                .filter(p -> getConfiguration(p.organizationId(), p.tenantId()).enabled())
                .collect(Collectors.toList());
    }

    @Nullable
    private static DateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : new DateTime(timestamp);
    }

    @Nullable
    private static Long number(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

}
