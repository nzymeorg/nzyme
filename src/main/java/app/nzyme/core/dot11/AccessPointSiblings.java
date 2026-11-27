package app.nzyme.core.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AccessPointSiblings {

    public static final int MAX_ADDRESSES_PER_LOOKUP = 100;
    public static final int MAX_SIBLINGS = 50;
    public static final int MAX_SSIDS = 5;

    // Virtual access points of one device share octets two to five and the upper half of the last octet.
    private static final String CANDIDATES_SQL = "SELECT o.bssid AS own, upper(lpad(to_hex(f), 2, '0')) || " +
            "substr(o.bssid, 3, 14) || upper(to_hex(l)) AS bssid FROM unnest(CAST(:bssids AS text[])) AS o(bssid) " +
            "CROSS JOIN generate_series(0, 255) AS f CROSS JOIN generate_series(0, 15) AS l";

    private static final int CANDIDATES_PER_ADDRESS = 256 * 16;
    private static final int STATEMENT_TIMEOUT_MS = 5000;

    private final NzymeNode nzyme;

    public AccessPointSiblings(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public record Sibling(String bssid, DateTime lastSeen, List<String> ssids) { }

    public record Siblings(List<Sibling> siblings, boolean limited) {

        public List<String> ssids() {
            return siblings.stream()
                    .flatMap(sibling -> sibling.ssids().stream())
                    .distinct()
                    .sorted()
                    .limit(MAX_SSIDS)
                    .toList();
        }

    }

    // Addresses that are no MAC address and those over the limit of a lookup are left out of the result.
    public Map<String, Siblings> find(Collection<String> bssids, TimeRange timeRange, List<UUID> taps) {
        Map<String, Siblings> result = Maps.newHashMap();
        lookup(bssids, timeRange, taps, MAX_SIBLINGS + 1).forEach((bssid, siblings) -> result.put(bssid, new Siblings(
                siblings.stream().limit(MAX_SIBLINGS).toList(), siblings.size() > MAX_SIBLINGS
        )));

        return result;
    }

    // All siblings are read before the limit applies, so that it counts those without an SSID only if asked for.
    public Siblings findOf(String bssid, TimeRange timeRange, List<UUID> taps, boolean withoutSsidOnly) {
        List<Sibling> siblings = lookup(List.of(bssid), timeRange, taps, CANDIDATES_PER_ADDRESS)
                .values()
                .stream()
                .flatMap(Collection::stream)
                .filter(sibling -> !withoutSsidOnly || sibling.ssids().isEmpty())
                .toList();

        return new Siblings(siblings.stream().limit(MAX_SIBLINGS).toList(), siblings.size() > MAX_SIBLINGS);
    }

    private Map<String, List<Sibling>> lookup(Collection<String> bssids, TimeRange timeRange, List<UUID> taps,
                                              int siblingsPerAddress) {
        String[] own = bssids.stream()
                .filter(Tools::isValidMacAddress)
                .map(String::toUpperCase)
                .distinct()
                .limit(MAX_ADDRESSES_PER_LOOKUP)
                .toArray(String[]::new);

        if (own.length == 0 || taps.isEmpty()) {
            return Map.of();
        }

        return nzyme.getDatabase().inTransaction(handle -> {
            handle.execute("SET LOCAL statement_timeout = " + STATEMENT_TIMEOUT_MS);

            // On a small table the planner would scan all of it once per candidate, thousands of times.
            handle.execute("SET LOCAL enable_seqscan = off");

            Map<String, List<Sibling>> found = Maps.newHashMap();
            Map<String, List<List<String>>> ssidsOf = Maps.newHashMap();

            /*
             * Every candidate is one probe, and only the index on address and time answers it in constant time: rows
             * of the address come newest first and the probe stops at the first row of a selected tap. The row
             * comparison cannot be served by another index. The tap is compared as text so that the index on tap and
             * time is no option either. It would read all rows of the time range per probe.
             */
            handle.createQuery("WITH candidates AS (" + CANDIDATES_SQL + "), " +
                            "seen AS (SELECT c.own, c.bssid, x.last_seen FROM candidates c CROSS JOIN LATERAL (" +
                            "SELECT b.created_at AS last_seen FROM dot11_bssids b " +
                            "WHERE (b.bssid, b.created_at) >= (c.bssid, :from) " +
                            "AND (b.bssid, b.created_at) <= (c.bssid, :to) AND b.tap_uuid::text = ANY(:tap_ids) " +
                            "ORDER BY b.bssid DESC, b.created_at DESC LIMIT 1) x WHERE c.bssid <> c.own) " +
                            "SELECT own, bssid, last_seen FROM (SELECT s.*, row_number() OVER (PARTITION BY s.own " +
                            "ORDER BY s.last_seen DESC, s.bssid) AS position FROM seen s) ranked " +
                            "WHERE position <= :limit ORDER BY own, position")
                    .bind("bssids", own)
                    .bind("tap_ids", taps.stream().map(UUID::toString).toArray(String[]::new))
                    .bind("from", timeRange.from())
                    .bind("to", timeRange.to())
                    .bind("limit", siblingsPerAddress)
                    .map((rs, ctx) -> {
                        List<String> ssids = Lists.newArrayList();
                        ssidsOf.computeIfAbsent(rs.getString("bssid"), bssid -> Lists.newArrayList()).add(ssids);
                        found.computeIfAbsent(rs.getString("own"), bssid -> Lists.newArrayList())
                                .add(new Sibling(rs.getString("bssid"), new DateTime(rs.getTimestamp("last_seen")), ssids));

                        return true;
                    })
                    .list();

            if (!ssidsOf.isEmpty()) {
                handle.createQuery("SELECT bssid, ssid FROM (SELECT n.*, row_number() OVER (PARTITION BY n.bssid " +
                                "ORDER BY n.reports DESC, n.ssid) AS position FROM (SELECT s.bssid, s.ssid, " +
                                "COUNT(*) AS reports FROM dot11_ssids s WHERE s.tap_uuid IN (<taps>) " +
                                "AND s.bssid = ANY(:siblings) AND s.created_at >= :from AND s.created_at <= :to " +
                                "GROUP BY s.bssid, s.ssid) n) ranked WHERE position <= :limit ORDER BY bssid, ssid")
                        .bindList("taps", taps)
                        .bind("siblings", ssidsOf.keySet().toArray(new String[0]))
                        .bind("from", timeRange.from())
                        .bind("to", timeRange.to())
                        .bind("limit", MAX_SSIDS)
                        .map((rs, ctx) -> {
                            String ssid = rs.getString("ssid");
                            ssidsOf.get(rs.getString("bssid")).forEach(ssids -> ssids.add(ssid));

                            return true;
                        })
                        .list();
            }

            return found;
        });
    }

}
