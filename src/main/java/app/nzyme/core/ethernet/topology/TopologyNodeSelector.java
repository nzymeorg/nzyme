package app.nzyme.core.ethernet.topology;

import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/*
 * Node IDs as the topology endpoints return and accept them:
 *
 *   host:192.168.0.5        internal address
 *   subnet:192.168.0.0/24   internal network
 *   tap:<uuid>              all internal addresses a tap recorded
 *   ext:203.0.113.7         external address
 *   asn:15169               external autonomous system
 *   noasn:US                external addresses without autonomous system in a country, noasn:- without both
 *   cc:US                   external addresses in a country, cc:- without a known country
 *   loc:52.52,13.40         external addresses at coordinates, rounded to two decimals
 */
public record TopologyNodeSelector(Kind kind, String value) {

    public static final String UNKNOWN = "-";

    private static final Pattern COUNTRY_CODE = Pattern.compile("\\p{L}{2}");

    public enum Side {

        SOURCE("source"),
        DESTINATION("destination");

        private final String prefix;

        Side(String prefix) {
            this.prefix = prefix;
        }

        public String local() {
            return prefix + "_address_is_site_local";
        }

        public String address() {
            return prefix + "_address";
        }

        public String asnNumber() {
            return prefix + "_address_geo_asn_number";
        }

        public String asnName() {
            return prefix + "_address_geo_asn_name";
        }

        public String countryCode() {
            return prefix + "_address_geo_country_code";
        }

        public String city() {
            return prefix + "_address_geo_city";
        }

        public String latitude() {
            return prefix + "_address_geo_latitude";
        }

        public String longitude() {
            return prefix + "_address_geo_longitude";
        }

        public Side other() {
            return this == SOURCE ? DESTINATION : SOURCE;
        }

    }

    public enum Kind {

        HOST("host", true),
        SUBNET("subnet", true),
        TAP("tap", true),
        EXTERNAL_HOST("ext", false),
        ASN("asn", false),
        NO_ASN("noasn", false),
        COUNTRY("cc", false),
        LOCATION("loc", false);

        private final String prefix;
        private final boolean internal;

        Kind(String prefix, boolean internal) {
            this.prefix = prefix;
            this.internal = internal;
        }

        public String prefix() {
            return prefix;
        }

        public boolean internal() {
            return internal;
        }

        public static Kind ofPrefix(String prefix) {
            for (Kind kind : values()) {
                if (kind.prefix.equals(prefix)) {
                    return kind;
                }
            }

            throw new IllegalArgumentException("Unknown node type [" + prefix + "].");
        }

    }

    public static TopologyNodeSelector parse(@Nullable String id) {
        if (id == null) {
            throw new IllegalArgumentException("Missing node ID.");
        }

        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1) {
            throw new IllegalArgumentException("Invalid node ID [" + id + "].");
        }

        String prefix = id.substring(0, separator);
        String value = id.substring(separator + 1);

        Kind kind = Kind.ofPrefix(prefix);
        return new TopologyNodeSelector(kind, validate(kind, value));
    }

    private static String validate(Kind kind, String value) {
        switch (kind) {
            case HOST:
            case EXTERNAL_HOST:
                return address(value);
            case SUBNET: {
                int slash = value.indexOf('/');
                if (slash <= 0) {
                    throw new IllegalArgumentException("Invalid network [" + value + "].");
                }
                String address = address(value.substring(0, slash));
                int length = Integer.parseInt(value.substring(slash + 1));
                if (length < 0 || length > (address.indexOf(':') >= 0 ? 128 : 32)) {
                    throw new IllegalArgumentException("Invalid network [" + value + "].");
                }
                return address + "/" + length;
            }
            case TAP:
                return UUID.fromString(value).toString();
            case ASN:
                return String.valueOf(Integer.parseInt(value));
            case NO_ASN:
            case COUNTRY:
                if (UNKNOWN.equals(value)) {
                    return value;
                }
                if (!COUNTRY_CODE.matcher(value).matches()) {
                    throw new IllegalArgumentException("Invalid country code [" + value + "].");
                }
                return value;
            case LOCATION: {
                String[] parts = value.split(",");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid location [" + value + "].");
                }
                return hundredths(parts[0]) + "," + hundredths(parts[1]);
            }
            default:
                throw new IllegalArgumentException("Unknown node type.");
        }
    }

    // Returned as passed, not normalized: the ID of a node is what the database prints for its address.
    private static String address(String value) {
        // Guava resolves a scoped IPv6 address against the interfaces of this machine, and PostgreSQL takes none.
        if (value.indexOf('%') >= 0) {
            throw new IllegalArgumentException("Invalid address [" + value + "].");
        }

        InetAddresses.forString(value);
        return value;
    }

    // Coordinates are matched as integer hundredths of a degree, like the geo flows are grouped.
    private static long hundredths(String degrees) {
        double parsed = Double.parseDouble(degrees);
        if (Double.isNaN(parsed) || Math.abs(parsed) > 180) {
            throw new IllegalArgumentException("Invalid coordinate [" + degrees + "].");
        }
        return Math.round(parsed * 100);
    }

    public String id() {
        if (kind == Kind.LOCATION) {
            String[] parts = value.split(",");
            return kind.prefix + ":" + (Long.parseLong(parts[0]) / 100.0) + "," + (Long.parseLong(parts[1]) / 100.0);
        }

        return kind.prefix + ":" + value;
    }

    public String sql(Side side, String bind) {
        String local = side.local();
        String external = "NOT " + local;

        switch (kind) {
            case HOST:
                return local + " AND " + side.address() + " = :" + bind + "::inet";
            case SUBNET:
                return local + " AND " + side.address() + " <<= :" + bind + "::inet";
            case TAP:
                return local + " AND tap_uuid = :" + bind + "::uuid";
            case EXTERNAL_HOST:
                return external + " AND " + side.address() + " = :" + bind + "::inet";
            case ASN:
                return external + " AND " + side.asnNumber() + " = :" + bind;
            case NO_ASN:
                return external + " AND " + side.asnNumber() + " IS NULL AND " + (UNKNOWN.equals(value)
                        ? side.countryCode() + " IS NULL"
                        : side.countryCode() + " = :" + bind);
            case COUNTRY:
                return external + " AND " + (UNKNOWN.equals(value)
                        ? side.countryCode() + " IS NULL"
                        : side.countryCode() + " = :" + bind);
            case LOCATION:
                return external + " AND round(" + side.latitude() + " * 100) = :" + bind + "_lat AND round("
                        + side.longitude() + " * 100) = :" + bind + "_lon";
            default:
                throw new IllegalStateException("Unknown node type.");
        }
    }

    public Map<String, Object> bindings(String bind) {
        Map<String, Object> bindings = new HashMap<>();

        switch (kind) {
            case ASN:
                bindings.put(bind, Integer.parseInt(value));
                break;
            case NO_ASN:
            case COUNTRY:
                if (!UNKNOWN.equals(value)) {
                    bindings.put(bind, value);
                }
                break;
            case LOCATION: {
                String[] parts = value.split(",");
                bindings.put(bind + "_lat", Long.parseLong(parts[0]));
                bindings.put(bind + "_lon", Long.parseLong(parts[1]));
                break;
            }
            default:
                bindings.put(bind, value);
        }

        return bindings;
    }

}
