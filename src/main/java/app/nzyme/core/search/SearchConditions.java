package app.nzyme.core.search;

import org.jdbi.v3.core.statement.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Collects the conditions a term can match a row by and binds the values they use. Column names come from the
 * sources and never from the request. Everything of the request is bound.
 *
 * Statements of a source may use the bindings s_contains, s_starts, s_lower and s_raw, and s_mac, s_mac_prefix,
 * s_ip, s_ip_in_text (a regular expression), s_cidr and s_uuid if the term is of that kind.
 */
public class SearchConditions {

    private final SearchTerm term;
    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> bindings = new LinkedHashMap<>();

    public SearchConditions(SearchTerm term) {
        this.term = term;

        bindings.put("s_contains", term.contains());
        bindings.put("s_starts", term.startsWith());
        bindings.put("s_lower", term.lower());
        bindings.put("s_raw", term.raw());

        if (term.is(SearchTerm.Kind.MAC)) {
            bindings.put("s_mac", term.mac());
        }
        if (term.is(SearchTerm.Kind.MAC_PREFIX)) {
            bindings.put("s_mac_prefix", term.macStartsWith());
        }
        if (term.is(SearchTerm.Kind.IP)) {
            bindings.put("s_ip", term.address());
            bindings.put("s_ip_in_text", term.addressInText());
        }
        if (term.is(SearchTerm.Kind.CIDR)) {
            bindings.put("s_cidr", term.cidr());
        }
        if (term.is(SearchTerm.Kind.UUID)) {
            bindings.put("s_uuid", term.uuid());
        }
    }

    public SearchConditions mac(String... columns) {
        for (String column : columns) {
            if (term.is(SearchTerm.Kind.MAC)) {
                conditions.add(column + " = :s_mac");
            } else if (term.is(SearchTerm.Kind.MAC_PREFIX)) {
                conditions.add(column + " LIKE :s_mac_prefix");
            }
        }

        return this;
    }

    public SearchConditions address(String... columns) {
        for (String column : columns) {
            if (term.is(SearchTerm.Kind.IP)) {
                conditions.add(column + " = :s_ip::inet");
            } else if (term.is(SearchTerm.Kind.CIDR)) {
                conditions.add(column + " <<= :s_cidr::inet");
            }
        }

        return this;
    }

    // For columns that hold a network: the networks an address is in, or that overlap a network.
    public SearchConditions network(String column) {
        if (term.is(SearchTerm.Kind.IP)) {
            conditions.add(column + " >>= :s_ip::inet");
        } else if (term.is(SearchTerm.Kind.CIDR)) {
            conditions.add(column + " && :s_cidr::inet");
        }

        return this;
    }

    // For text columns that hold a network in CIDR notation, as the exclusions do. Other text never reaches the cast.
    public SearchConditions networkText(String... columns) {
        for (String column : columns) {
            String guarded = "CASE WHEN " + column + " ~ '^[0-9a-fA-F:.]+(/[0-9]{1,3})?$' THEN " + column + "::inet ";

            if (term.is(SearchTerm.Kind.IP)) {
                conditions.add(guarded + ">>= :s_ip::inet ELSE FALSE END");
            } else if (term.is(SearchTerm.Kind.CIDR)) {
                conditions.add(guarded + "&& :s_cidr::inet ELSE FALSE END");
            }
        }

        return this;
    }

    public SearchConditions uuid(String... columns) {
        if (term.is(SearchTerm.Kind.UUID)) {
            for (String column : columns) {
                conditions.add(column + " = :s_uuid");
            }
        }

        return this;
    }

    public SearchConditions contains(String... columns) {
        for (String column : columns) {
            conditions.add(column + " ILIKE :s_contains");
        }

        return this;
    }

    public SearchConditions custom(String condition, SearchTerm.Kind... kinds) {
        if (kinds.length == 0 || term.isAnyOf(kinds)) {
            conditions.add(condition);
        }

        return this;
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    public String sql() {
        if (conditions.isEmpty()) {
            return "FALSE";
        }

        return "(" + String.join(" OR ", conditions) + ")";
    }

    public Query bind(Query query, String sql) {
        for (Map.Entry<String, Object> binding : bindings.entrySet()) {
            if (sql.contains(":" + binding.getKey())) {
                query.bind(binding.getKey(), binding.getValue());
            }
        }

        return query;
    }

}
