package app.nzyme.core.assets.profile;

import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.taps.Tap;
import com.google.common.base.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.*;

public class AssetProfileBatch {

    private static final Logger LOG = LogManager.getLogger(AssetProfileBatch.class);

    public static final String NULL_MAC = "00:00:00:00:00:00";

    private static final Set<String> CAPPED_DIMENSIONS = Set.of(
            AssetAttributeDimension.INBOUND_PORT.name(), AssetAttributeDimension.INBOUND_PORT_PROTOCOL.name()
    );
    private static final int MAX_PORT_VALUES = 1000;
    private static final int MAX_PORTS_PER_EDGE = 64;
    public static final String BROADCAST_MAC = "FF:FF:FF:FF:FF:FF";

    public enum ActivityKind { ARP, DHCP, DNS, TCP, UDP }
    public enum Direction { OUTBOUND, INBOUND }
    public enum L4Type { TCP, UDP }
    public enum PeerType { INTERNAL, EXTERNAL }

    private final UUID organizationId;
    private final UUID tenantId;
    private final UUID tapUuid;

    private final Map<String, long[]> activity = new HashMap<>();
    private final Map<String, Object[]> attributes = new HashMap<>();
    private final Map<String, Object[]> communication = new HashMap<>();

    public AssetProfileBatch(Tap tap) {
        this(tap.organizationId(), tap.tenantId(), tap.uuid());
    }

    public AssetProfileBatch(UUID organizationId, UUID tenantId, UUID tapUuid) {
        this.organizationId = organizationId;
        this.tenantId = tenantId;
        this.tapUuid = tapUuid;
    }

    public static boolean isUsableMac(@Nullable String mac) {
        return !Strings.isNullOrEmpty(mac)
                && !NULL_MAC.equals(mac)
                && !BROADCAST_MAC.equalsIgnoreCase(mac)
                && !mac.startsWith("01:00:5E")  // IPv4 multicast.
                && !mac.startsWith("01:00:5e")
                && !mac.startsWith("33:33");    // IPv6 multicast.
    }

    public void activity(@Nullable String mac, @Nullable DateTime timestamp, ActivityKind kind, long count,
                         long bytesRx, long bytesTx) {
        if (!isUsableMac(mac) || timestamp == null) {
            return;
        }

        DateTime bucket = timestamp.withZone(DateTimeZone.UTC).hourOfDay().roundFloorCopy();
        String key = mac + "|" + bucket.getMillis();

        // Columns: arp, dhcp, dns, tcp, udp, bytes_rx, bytes_tx, bucket millis.
        long[] counters = activity.computeIfAbsent(key, k -> new long[]{0, 0, 0, 0, 0, 0, 0, bucket.getMillis()});
        counters[kind.ordinal()] += count;
        counters[5] += bytesRx;
        counters[6] += bytesTx;
    }

    public void attribute(@Nullable String mac, AssetAttributeDimension dimension, @Nullable String value,
                          @Nullable DateTime timestamp) {
        attribute(mac, dimension, value, timestamp, 1);
    }

    public void attribute(@Nullable String mac, AssetAttributeDimension dimension, @Nullable String value,
                          @Nullable DateTime timestamp, long count) {
        attribute(mac, dimension, value, timestamp, count, 0);
    }

    public void attribute(@Nullable String mac, AssetAttributeDimension dimension, @Nullable String value,
                          @Nullable DateTime timestamp, long count, long bytes) {
        if (!isUsableMac(mac) || Strings.isNullOrEmpty(value) || timestamp == null) {
            return;
        }

        String key = mac + "|" + dimension.name() + "|" + value;
        Object[] existing = attributes.get(key);
        if (existing == null) {
            // mac, dimension, value, count, first_seen, last_seen, bytes
            attributes.put(key, new Object[]{mac, dimension.name(), value, count, timestamp, timestamp, bytes});
        } else {
            existing[3] = (long) existing[3] + count;
            existing[6] = (long) existing[6] + bytes;
            if (timestamp.isBefore((DateTime) existing[4])) {
                existing[4] = timestamp;
            }
            if (timestamp.isAfter((DateTime) existing[5])) {
                existing[5] = timestamp;
            }
        }
    }

    public void tap(@Nullable String mac, @Nullable DateTime timestamp, long count) {
        attribute(mac, AssetAttributeDimension.TAP, tapUuid.toString(), timestamp, count);
    }

    public void communication(@Nullable String mac,
                              Direction direction,
                              L4Type l4Type,
                              PeerType peerType,
                              String peerKey,
                              @Nullable String peerMac,
                              @Nullable String peerAddress,
                              @Nullable Integer peerAsnNumber,
                              @Nullable String peerAsnName,
                              @Nullable String peerCountryCode,
                              @Nullable String peerCity,
                              @Nullable Double peerLatitude,
                              @Nullable Double peerLongitude,
                              int port,
                              String protocol,
                              @Nullable DateTime timestamp,
                              long sessions,
                              long bytesRx,
                              long bytesTx) {
        if (!isUsableMac(mac) || Strings.isNullOrEmpty(peerKey) || timestamp == null) {
            return;
        }

        LocalDate bucket = timestamp.withZone(DateTimeZone.UTC).toLocalDate();
        String key = mac + "|" + direction + "|" + l4Type + "|" + peerType + "|" + peerKey + "|" + port + "|"
                + protocol + "|" + bucket;

        Object[] existing = communication.get(key);
        if (existing == null) {
            communication.put(key, new Object[]{
                    mac, direction.name(), l4Type.name(), peerType.name(), peerKey, peerMac, peerAddress,
                    peerAsnNumber, peerAsnName, peerCountryCode, bucket, sessions, bytesRx, bytesTx,
                    timestamp, timestamp, port, protocol, peerCity, peerLatitude, peerLongitude
            });
        } else {
            existing[11] = (long) existing[11] + sessions;
            existing[12] = (long) existing[12] + bytesRx;
            existing[13] = (long) existing[13] + bytesTx;
            if (timestamp.isBefore((DateTime) existing[14])) {
                existing[14] = timestamp;
            }
            if (timestamp.isAfter((DateTime) existing[15])) {
                existing[15] = timestamp;
            }
            if (existing[5] == null && peerMac != null) {
                existing[5] = peerMac;
            }
            // The first location seen for the network sticks, like in the database upsert.
            if (existing[19] == null && peerLatitude != null && peerLongitude != null) {
                existing[18] = peerCity;
                existing[19] = peerLatitude;
                existing[20] = peerLongitude;
            }
        }
    }

    // Called for every session in a report, including ones reported before: counts only grow for new sessions,
    // bytes by the incremental counters.
    public void l4Session(L4Type l4Type,
                          @Nullable String sourceMac,
                          @Nullable String destinationMac,
                          @Nullable InetAddress sourceAddress,
                          @Nullable InetAddress destinationAddress,
                          int destinationPort,
                          @Nullable List<String> tags,
                          boolean isNew,
                          long bytesRxIncremental,
                          long bytesTxIncremental,
                          @Nullable DateTime startTime,
                          @Nullable DateTime mostRecentSegmentTime,
                          Optional<GeoIpLookupResult> sourceGeo,
                          Optional<GeoIpLookupResult> destinationGeo,
                          @Nullable String tcpFingerprint) {
        if (sourceAddress == null || destinationAddress == null) {
            return;
        }

        DateTime timestamp = mostRecentSegmentTime != null ? mostRecentSegmentTime : startTime;
        if (timestamp == null) {
            return;
        }

        ActivityKind activityKind = l4Type == L4Type.TCP ? ActivityKind.TCP : ActivityKind.UDP;
        long sessionCount = isNew ? 1 : 0;
        long sessionBytes = bytesRxIncremental + bytesTxIncremental;
        // The tags are joined into one value, so a session is never counted twice.
        String protocol = protocolOf(tags);

        boolean sourceUsable = isUsableMac(sourceMac);
        boolean destinationUsable = isUsableMac(destinationMac)
                && !destinationAddress.isMulticastAddress()
                && !destinationAddress.isLoopbackAddress()
                && !isBroadcastAddress(destinationAddress);
        boolean destinationInternal = destinationAddress.isSiteLocalAddress()
                || destinationAddress.isLinkLocalAddress();
        boolean sourceInternal = sourceAddress.isSiteLocalAddress() || sourceAddress.isLinkLocalAddress();

        if (sourceUsable) {
            activity(sourceMac, timestamp, activityKind, sessionCount, bytesRxIncremental, bytesTxIncremental);
            tap(sourceMac, timestamp, sessionCount);

            if (l4Type == L4Type.TCP && !Strings.isNullOrEmpty(tcpFingerprint)) {
                attribute(sourceMac, AssetAttributeDimension.TCP_FINGERPRINT, tcpFingerprint, timestamp, sessionCount);
            }

            // Multicast and broadcast sessions are not client protocol use: mDNS is tagged DNS by the tap but never
            // reaches the DNS log, and name resolution protocols have their own dimension.
            if (destinationAddress.isMulticastAddress() || isBroadcastAddress(destinationAddress)
                    || destinationAddress.isLoopbackAddress()) {
                return;
            }

            if (tags != null) {
                for (String tag : tags) {
                    if ("Unencrypted".equals(tag)) {
                        continue;
                    }
                    attribute(sourceMac, AssetAttributeDimension.PROTOCOL_CLIENT, tag, timestamp, sessionCount, sessionBytes);
                }
            }

            if (destinationInternal) {
                communication(sourceMac, Direction.OUTBOUND, l4Type, PeerType.INTERNAL,
                        destinationAddress.getHostAddress(), isUsableMac(destinationMac) ? destinationMac : null,
                        destinationAddress.getHostAddress(), null, null, null, null, null, null, destinationPort,
                        protocol, timestamp, sessionCount, bytesRxIncremental, bytesTxIncremental);
            } else {
                Integer asnNumber = destinationGeo.map(g -> g.asn() == null ? null : g.asn().number()).orElse(null);
                String asnName = destinationGeo.map(g -> g.asn() == null ? null : g.asn().name()).orElse(null);
                String countryCode = destinationGeo.map(g -> g.geo() == null ? null : g.geo().countryCode()).orElse(null);
                String city = destinationGeo.map(g -> g.geo() == null ? null : g.geo().city()).orElse(null);
                Double latitude = destinationGeo.map(g -> g.geo() == null ? null : doubleOf(g.geo().latitude())).orElse(null);
                Double longitude = destinationGeo.map(g -> g.geo() == null ? null : doubleOf(g.geo().longitude())).orElse(null);

                communication(sourceMac, Direction.OUTBOUND, l4Type, PeerType.EXTERNAL,
                        externalPeerKey(asnNumber, countryCode), null, null, asnNumber, asnName, countryCode,
                        city, latitude, longitude, destinationPort, protocol, timestamp, sessionCount,
                        bytesRxIncremental, bytesTxIncremental);

                if (asnNumber != null) {
                    attribute(sourceMac, AssetAttributeDimension.EXTERNAL_ASN, externalAsnValue(asnNumber, asnName),
                            timestamp, sessionCount);
                }
                if (!Strings.isNullOrEmpty(countryCode)) {
                    attribute(sourceMac, AssetAttributeDimension.EXTERNAL_COUNTRY, countryCode, timestamp, sessionCount);
                }

                // Traffic to the outside leaves through the destination MAC: the gateway.
                if (isUsableMac(destinationMac)) {
                    attribute(sourceMac, AssetAttributeDimension.GATEWAY_MAC, destinationMac, timestamp, sessionCount);
                    attribute(destinationMac, AssetAttributeDimension.ROLE, AssetRole.GATEWAY.name(), timestamp, sessionCount);
                    tap(destinationMac, timestamp, sessionCount);
                }
            }
        }

        if (destinationUsable && destinationInternal) {
            // The destination asset received this session: its RX is the session TX.
            activity(destinationMac, timestamp, activityKind, sessionCount, bytesTxIncremental, bytesRxIncremental);
            tap(destinationMac, timestamp, sessionCount);
            attribute(destinationMac, AssetAttributeDimension.INBOUND_PORT, l4Type.name() + "/" + destinationPort,
                    timestamp, sessionCount, sessionBytes);

            if (tags != null) {
                for (String tag : tags) {
                    if ("Unencrypted".equals(tag)) {
                        continue;
                    }
                    attribute(destinationMac, AssetAttributeDimension.PROTOCOL_SERVER, tag, timestamp, sessionCount, sessionBytes);
                    attribute(destinationMac, AssetAttributeDimension.INBOUND_PORT_PROTOCOL,
                            l4Type.name() + "/" + destinationPort + " " + tag, timestamp, sessionCount, sessionBytes);
                    AssetRole role = AssetRole.fromServerSideTag(tag);
                    if (role != null) {
                        attribute(destinationMac, AssetAttributeDimension.ROLE, role.name(), timestamp, sessionCount);
                    }
                }
            }

            if (sourceInternal) {
                communication(destinationMac, Direction.INBOUND, l4Type, PeerType.INTERNAL,
                        sourceAddress.getHostAddress(), sourceUsable ? sourceMac : null,
                        sourceAddress.getHostAddress(), null, null, null, null, null, null, destinationPort,
                        protocol, timestamp, sessionCount, bytesTxIncremental, bytesRxIncremental);
            } else if (!sourceAddress.isLoopbackAddress() && !sourceAddress.isMulticastAddress()) {
                Integer asnNumber = sourceGeo.map(g -> g.asn() == null ? null : g.asn().number()).orElse(null);
                String asnName = sourceGeo.map(g -> g.asn() == null ? null : g.asn().name()).orElse(null);
                String countryCode = sourceGeo.map(g -> g.geo() == null ? null : g.geo().countryCode()).orElse(null);
                String city = sourceGeo.map(g -> g.geo() == null ? null : g.geo().city()).orElse(null);
                Double latitude = sourceGeo.map(g -> g.geo() == null ? null : doubleOf(g.geo().latitude())).orElse(null);
                Double longitude = sourceGeo.map(g -> g.geo() == null ? null : doubleOf(g.geo().longitude())).orElse(null);

                communication(destinationMac, Direction.INBOUND, l4Type, PeerType.EXTERNAL,
                        externalPeerKey(asnNumber, countryCode), null, null, asnNumber, asnName, countryCode,
                        city, latitude, longitude, destinationPort, protocol, timestamp, sessionCount,
                        bytesTxIncremental, bytesRxIncremental);
            }
        }
    }

    @Nullable
    private static Double doubleOf(@Nullable Float f) {
        return f == null ? null : f.doubleValue();
    }

    public static String protocolOf(@Nullable List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }

        TreeSet<String> sorted = new TreeSet<>();
        for (String tag : tags) {
            if (!Strings.isNullOrEmpty(tag) && !"Unencrypted".equals(tag)) {
                sorted.add(tag);
            }
        }

        return String.join("/", sorted);
    }

    public static String externalPeerKey(@Nullable Integer asnNumber, @Nullable String countryCode) {
        if (asnNumber != null) {
            return "asn:" + asnNumber;
        }
        if (!Strings.isNullOrEmpty(countryCode)) {
            return "cc:" + countryCode;
        }
        return "unknown";
    }

    public static String externalAsnValue(int asnNumber, @Nullable String asnName) {
        return Strings.isNullOrEmpty(asnName) ? "AS" + asnNumber : "AS" + asnNumber + " " + asnName;
    }

    private static boolean isBroadcastAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        return bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFF && bytes[2] == (byte) 0xFF && bytes[3] == (byte) 0xFF;
    }

    public boolean isEmpty() {
        return activity.isEmpty() && attributes.isEmpty() && communication.isEmpty();
    }

    public void flush(Handle handle) {
        if (isEmpty()) {
            return;
        }

        try {
            flushActivity(handle);
        } catch (Exception e) {
            LOG.error("Could not write asset activity.", e);
        }

        try {
            flushAttributes(handle);
        } catch (Exception e) {
            LOG.error("Could not write asset attributes.", e);
        }

        try {
            flushCommunication(handle);
        } catch (Exception e) {
            LOG.error("Could not write asset communication.", e);
        }

        activity.clear();
        attributes.clear();
        communication.clear();
    }

    private void flushActivity(Handle handle) {
        if (activity.isEmpty()) {
            return;
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO assets_activity(organization_id, tenant_id, mac, " +
                "tap_uuid, bucket, arp_packets, dhcp_transactions, dns_queries, tcp_sessions, udp_sessions, " +
                "bytes_rx, bytes_tx) VALUES(:organization_id, :tenant_id, :mac, :tap_uuid, :bucket, :arp_packets, " +
                ":dhcp_transactions, :dns_queries, :tcp_sessions, :udp_sessions, :bytes_rx, :bytes_tx) " +
                "ON CONFLICT (organization_id, tenant_id, mac, tap_uuid, bucket) DO UPDATE SET " +
                "arp_packets = assets_activity.arp_packets + EXCLUDED.arp_packets, " +
                "dhcp_transactions = assets_activity.dhcp_transactions + EXCLUDED.dhcp_transactions, " +
                "dns_queries = assets_activity.dns_queries + EXCLUDED.dns_queries, " +
                "tcp_sessions = assets_activity.tcp_sessions + EXCLUDED.tcp_sessions, " +
                "udp_sessions = assets_activity.udp_sessions + EXCLUDED.udp_sessions, " +
                "bytes_rx = assets_activity.bytes_rx + EXCLUDED.bytes_rx, " +
                "bytes_tx = assets_activity.bytes_tx + EXCLUDED.bytes_tx");

        for (Map.Entry<String, long[]> entry : activity.entrySet()) {
            String mac = entry.getKey().substring(0, entry.getKey().indexOf('|'));
            long[] c = entry.getValue();
            batch.bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("mac", mac)
                    .bind("tap_uuid", tapUuid)
                    .bind("bucket", new DateTime(c[7], DateTimeZone.UTC))
                    .bind("arp_packets", c[0])
                    .bind("dhcp_transactions", c[1])
                    .bind("dns_queries", c[2])
                    .bind("tcp_sessions", c[3])
                    .bind("udp_sessions", c[4])
                    .bind("bytes_rx", c[5])
                    .bind("bytes_tx", c[6])
                    .add();
        }

        batch.execute();
    }

    private void flushAttributes(Handle handle) {
        if (attributes.isEmpty()) {
            return;
        }

        capPortAttributes(handle);
        if (attributes.isEmpty()) {
            return;
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO assets_attributes(organization_id, tenant_id, mac, " +
                "dimension, value, count, first_seen, last_seen, bytes) VALUES(:organization_id, :tenant_id, :mac, " +
                ":dimension, :value, :count, :first_seen, :last_seen, :bytes) " +
                "ON CONFLICT (organization_id, tenant_id, mac, dimension, value) DO UPDATE SET " +
                "count = assets_attributes.count + EXCLUDED.count, " +
                "bytes = assets_attributes.bytes + EXCLUDED.bytes, " +
                "first_seen = LEAST(assets_attributes.first_seen, EXCLUDED.first_seen), " +
                "last_seen = GREATEST(assets_attributes.last_seen, EXCLUDED.last_seen)");

        for (Object[] a : attributes.values()) {
            batch.bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("mac", (String) a[0])
                    .bind("dimension", (String) a[1])
                    .bind("value", (String) a[2])
                    .bind("count", (long) a[3])
                    .bind("first_seen", (DateTime) a[4])
                    .bind("last_seen", (DateTime) a[5])
                    .bind("bytes", (long) a[6])
                    .add();
        }

        batch.execute();
    }

    private void flushCommunication(Handle handle) {
        if (communication.isEmpty()) {
            return;
        }

        capCommunicationPorts(handle);

        PreparedBatch batch = handle.prepareBatch("INSERT INTO assets_communication(organization_id, tenant_id, " +
                "mac, direction, l4_type, peer_type, peer_key, peer_mac, peer_address, peer_asn_number, " +
                "peer_asn_name, peer_country_code, bucket, sessions, bytes_rx, bytes_tx, first_seen, last_seen, " +
                "port, protocol, peer_city, peer_latitude, peer_longitude) " +
                "VALUES(:organization_id, :tenant_id, :mac, :direction, :l4_type, :peer_type, :peer_key, " +
                ":peer_mac, :peer_address::inet, :peer_asn_number, :peer_asn_name, :peer_country_code, :bucket::date, " +
                ":sessions, :bytes_rx, :bytes_tx, :first_seen, :last_seen, :port, :protocol, :peer_city, " +
                ":peer_latitude, :peer_longitude) " +
                "ON CONFLICT (organization_id, tenant_id, mac, direction, l4_type, peer_type, peer_key, port, " +
                "protocol, bucket) " +
                "DO UPDATE SET " +
                "sessions = assets_communication.sessions + EXCLUDED.sessions, " +
                "bytes_rx = assets_communication.bytes_rx + EXCLUDED.bytes_rx, " +
                "bytes_tx = assets_communication.bytes_tx + EXCLUDED.bytes_tx, " +
                "peer_mac = COALESCE(EXCLUDED.peer_mac, assets_communication.peer_mac), " +
                "peer_asn_name = COALESCE(EXCLUDED.peer_asn_name, assets_communication.peer_asn_name), " +
                "peer_country_code = COALESCE(EXCLUDED.peer_country_code, assets_communication.peer_country_code), " +
                "peer_city = COALESCE(assets_communication.peer_city, EXCLUDED.peer_city), " +
                "peer_latitude = COALESCE(assets_communication.peer_latitude, EXCLUDED.peer_latitude), " +
                "peer_longitude = COALESCE(assets_communication.peer_longitude, EXCLUDED.peer_longitude), " +
                "first_seen = LEAST(assets_communication.first_seen, EXCLUDED.first_seen), " +
                "last_seen = GREATEST(assets_communication.last_seen, EXCLUDED.last_seen)");

        for (Object[] c : communication.values()) {
            batch.bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("mac", (String) c[0])
                    .bind("direction", (String) c[1])
                    .bind("l4_type", (String) c[2])
                    .bind("peer_type", (String) c[3])
                    .bind("peer_key", (String) c[4])
                    .bind("peer_mac", (String) c[5])
                    .bind("peer_address", (String) c[6])
                    .bind("peer_asn_number", (Integer) c[7])
                    .bind("peer_asn_name", (String) c[8])
                    .bind("peer_country_code", (String) c[9])
                    .bind("bucket", c[10].toString())
                    .bind("sessions", (long) c[11])
                    .bind("bytes_rx", (long) c[12])
                    .bind("bytes_tx", (long) c[13])
                    .bind("first_seen", (DateTime) c[14])
                    .bind("last_seen", (DateTime) c[15])
                    .bind("port", (int) c[16])
                    .bind("protocol", (String) c[17])
                    .bind("peer_city", (String) c[18])
                    .bind("peer_latitude", (Double) c[19])
                    .bind("peer_longitude", (Double) c[20])
                    .add();
        }

        batch.execute();
    }

    // Past MAX_PORT_VALUES values per asset and dimension, new values are dropped and existing ones still updated.
    private void capPortAttributes(Handle handle) {
        Set<String> macs = new HashSet<>();
        for (Object[] a : attributes.values()) {
            if (CAPPED_DIMENSIONS.contains((String) a[1])) {
                macs.add((String) a[0]);
            }
        }

        if (macs.isEmpty()) {
            return;
        }

        List<Map<String, Object>> saturated = handle.createQuery("SELECT mac, dimension FROM assets_attributes " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND dimension IN (<dimensions>) AND mac IN (<macs>) " +
                        "GROUP BY mac, dimension HAVING COUNT(*) >= :cap")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bindList("dimensions", new ArrayList<>(CAPPED_DIMENSIONS))
                .bindList("macs", new ArrayList<>(macs))
                .bind("cap", MAX_PORT_VALUES)
                .mapToMap()
                .list();

        for (Map<String, Object> row : saturated) {
            String mac = (String) row.get("mac");
            String dimension = (String) row.get("dimension");
            Set<String> existing = handle.createQuery("SELECT value FROM assets_attributes " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND mac = :mac AND dimension = :dimension")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("mac", mac)
                    .bind("dimension", dimension)
                    .mapTo(String.class)
                    .set();

            attributes.values().removeIf(a -> mac.equals(a[0]) && dimension.equals(a[1]) && !existing.contains((String) a[2]));
        }
    }

    // Past MAX_PORTS_PER_EDGE ports per edge and day, further ports fold into port 0. A flush accepts at most that
    // many new ports per edge, so an edge saturates after two flushes.
    private void capCommunicationPorts(Handle handle) {
        Set<String> macs = new HashSet<>();
        LocalDate minBucket = null;
        for (Object[] c : communication.values()) {
            macs.add((String) c[0]);
            LocalDate bucket = (LocalDate) c[10];
            if (minBucket == null || bucket.isBefore(minBucket)) {
                minBucket = bucket;
            }
        }

        if (macs.isEmpty()) {
            return;
        }

        Set<String> saturated = new HashSet<>();
        for (Map<String, Object> row : handle.createQuery("SELECT mac, direction, l4_type, peer_type, peer_key, " +
                        "bucket FROM assets_communication WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND mac IN (<macs>) AND bucket >= :min_bucket::date " +
                        "GROUP BY mac, direction, l4_type, peer_type, peer_key, bucket " +
                        "HAVING COUNT(DISTINCT port) >= :cap")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bindList("macs", new ArrayList<>(macs))
                .bind("min_bucket", minBucket.toString())
                .bind("cap", MAX_PORTS_PER_EDGE)
                .mapToMap()
                .list()) {
            saturated.add(row.get("mac") + "|" + row.get("direction") + "|" + row.get("l4_type") + "|"
                    + row.get("peer_type") + "|" + row.get("peer_key") + "|" + row.get("bucket"));
        }

        Map<String, Set<Integer>> acceptedPorts = new HashMap<>();
        List<Object[]> folded = new ArrayList<>();
        Iterator<Map.Entry<String, Object[]>> it = communication.entrySet().iterator();
        while (it.hasNext()) {
            Object[] c = it.next().getValue();
            int port = (int) c[16];
            if (port == 0) {
                continue;
            }

            String edge = c[0] + "|" + c[1] + "|" + c[2] + "|" + c[3] + "|" + c[4] + "|" + c[10];
            Set<Integer> accepted = acceptedPorts.computeIfAbsent(edge, k -> new HashSet<>());
            if (!saturated.contains(edge) && (accepted.contains(port) || accepted.size() < MAX_PORTS_PER_EDGE)) {
                accepted.add(port);
                continue;
            }

            it.remove();
            c[16] = 0;
            folded.add(c);
        }

        for (Object[] c : folded) {
            String key = c[0] + "|" + c[1] + "|" + c[2] + "|" + c[3] + "|" + c[4] + "|0|" + c[17] + "|" + c[10];
            Object[] existing = communication.get(key);
            if (existing == null) {
                communication.put(key, c);
            } else {
                existing[11] = (long) existing[11] + (long) c[11];
                existing[12] = (long) existing[12] + (long) c[12];
                existing[13] = (long) existing[13] + (long) c[13];
                if (((DateTime) c[14]).isBefore((DateTime) existing[14])) {
                    existing[14] = c[14];
                }
                if (((DateTime) c[15]).isAfter((DateTime) existing[15])) {
                    existing[15] = c[15];
                }
            }
        }
    }

}
