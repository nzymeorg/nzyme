package app.nzyme.core.search;

import app.nzyme.core.rest.responses.briefing.BriefingLinkResponse;
import app.nzyme.core.rest.responses.search.SearchFactResponse;
import app.nzyme.core.rest.responses.search.SearchHitResponse;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class SqlSearchSource extends SearchSource {

    public static final int SCORE_EXACT = 100;
    public static final int SCORE_PREFIX = 60;
    public static final int SCORE_CONTAINS = 30;

    private static final int MAXIMUM_FACTS = 4;
    private static final int MAXIMUM_TEXT_LENGTH = 160;
    private static final int MAXIMUM_LINK_VALUE_LENGTH = 512;

    /*
     * Runs a statement of a source. Binds what the statement names of: the conditions of the term,
     * :organization_id, :tenant_id, <taps> (<tap_ids> as text), :w_from, :w_to, :limit (one more than requested),
     * :offset and :cap (enough rows for the requested page when hits of several statements are merged).
     */
    protected List<Map<String, Object>> rows(Handle handle,
                                             String sql,
                                             SearchConditions conditions,
                                             Request request,
                                             @Nullable SearchWindow window) {
        return rows(handle, sql, conditions, request, window, query -> { });
    }

    protected List<Map<String, Object>> rows(Handle handle,
                                             String sql,
                                             SearchConditions conditions,
                                             Request request,
                                             @Nullable SearchWindow window,
                                             Consumer<Query> binder) {
        limitTime(handle, request);

        Query query = handle.createQuery(sql);
        conditions.bind(query, sql);
        binder.accept(query);

        if (sql.contains(":organization_id")) {
            query.bind("organization_id", request.scope().organizationId());
        }
        if (sql.contains(":tenant_id")) {
            query.bind("tenant_id", request.scope().tenantId());
        }
        if (sql.contains("<taps>")) {
            query.bindList("taps", request.scope().taps());
        }
        if (sql.contains("<tap_ids>")) {
            query.bindList("tap_ids", request.scope().taps().stream().map(UUID::toString).toList());
        }
        if (sql.contains(":w_from") && window != null) {
            query.bind("w_from", window.boundFrom());
        }
        if (sql.contains(":w_to") && window != null) {
            query.bind("w_to", window.to());
        }
        if (sql.contains(":limit")) {
            query.bind("limit", request.limit() + 1);
        }
        if (sql.contains(":offset")) {
            query.bind("offset", request.offset());
        }
        if (sql.contains(":cap")) {
            query.bind("cap", request.offset() + request.limit() + 1);
        }

        return query.mapToMap().list();
    }

    protected List<Map<String, Object>> rows(Handle handle, String sql, SearchConditions conditions, Request request) {
        return rows(handle, sql, conditions, request, null);
    }

    // The statement timeout is per statement. A source that runs several gives each what is left of its time.
    protected static void limitTime(Handle handle, Request request) {
        long left = request.deadline() - System.currentTimeMillis();
        if (left <= 0) {
            throw new SearchService.OutOfTimeException();
        }

        handle.execute("SET LOCAL statement_timeout = " + left);
    }

    /*
     * For sources that read several small tables, each with LIMIT :cap. The hits keep the order of the statements
     * and the order within each statement, and the page of the request is cut from that. Sorting them again here
     * would break paging: a statement only returns its first rows, so a later page would bring up rows that
     * belong before the ones already shown.
     */
    protected Result merged(List<SearchHitResponse> hits, Request request) {
        if (request.offset() >= hits.size()) {
            return new Result(List.of(), false, null, null);
        }

        return Result.of(hits.subList(request.offset(), hits.size()), request);
    }

    protected static String bestFirst(String column) {
        return "(lower(" + column + ") = :s_lower) DESC NULLS LAST, "
                + "(lower(" + column + ") LIKE :s_starts) DESC NULLS LAST";
    }

    // Names are optional for some of what is found, like floors. A hit still needs something to show.
    protected static SearchHitResponse hit(String type,
                                           @Nullable String title,
                                           @Nullable String subtitle,
                                           List<SearchFactResponse> facts,
                                           @Nullable DateTime timestamp,
                                           int score,
                                           BriefingLinkResponse link) {
        return SearchHitResponse.create(type, title == null ? "Unnamed" : title, subtitle, facts, timestamp,
                score, link);
    }

    // Values of a link are not cut like text is: a page filters by them. One that is too long is left out.
    public static BriefingLinkResponse link(String target, String... keysAndValues) {
        Map<String, String> params = new TreeMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            if (keysAndValues[i + 1] != null && keysAndValues[i + 1].length() <= MAXIMUM_LINK_VALUE_LENGTH) {
                params.put(keysAndValues[i], keysAndValues[i + 1]);
            }
        }

        return BriefingLinkResponse.create(target, params);
    }

    protected static String text(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        return text.length() > MAXIMUM_TEXT_LENGTH ? text.substring(0, MAXIMUM_TEXT_LENGTH) + "…" : text;
    }

    protected static String text(Map<String, Object> row, String column) {
        return text(row.get(column));
    }

    protected static DateTime time(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof DateTime dateTime) {
            return dateTime;
        }

        return value instanceof Date date ? new DateTime(date.getTime()) : null;
    }

    protected static long number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value instanceof Number number ? number.longValue() : 0;
    }

    protected static boolean bool(Map<String, Object> row, String column) {
        return Boolean.TRUE.equals(row.get(column));
    }

    protected static String count(long count, String singular, String plural) {
        return String.format(Locale.US, "%,d %s", count, count == 1 ? singular : plural);
    }

    // Sources match addresses and UUIDs exactly, so any hit for one is an exact match of some attribute.
    protected static int score(SearchTerm term, String... candidates) {
        if (term.isIdentifier()) {
            return SCORE_EXACT;
        }

        int best = SCORE_CONTAINS;

        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }

            String value = candidate.toLowerCase(Locale.ROOT);
            if (value.equals(term.lower())) {
                return SCORE_EXACT;
            }
            if (value.startsWith(term.lower())) {
                best = SCORE_PREFIX;
            }
        }

        return best;
    }

    protected static Facts facts(SearchTerm term) {
        return new Facts(term);
    }

    protected static class Facts {

        private final SearchTerm term;
        private final List<SearchFactResponse> matched = Lists.newArrayList();
        private final List<SearchFactResponse> other = Lists.newArrayList();

        private Facts(SearchTerm term) {
            this.term = term;
        }

        public Facts add(String label, Object value) {
            String text = text(value);
            if (text == null) {
                return this;
            }

            SearchFactResponse fact = SearchFactResponse.create(label, text);
            if (matches(text)) {
                matched.add(fact);
            } else {
                other.add(fact);
            }

            return this;
        }

        private boolean matches(String text) {
            String lower = text.toLowerCase(Locale.ROOT);
            return lower.contains(term.lower())
                    || (term.is(SearchTerm.Kind.MAC) && lower.contains(term.mac().toLowerCase(Locale.ROOT)))
                    || (term.is(SearchTerm.Kind.MAC_PREFIX)
                            && lower.contains(term.macPrefix().toLowerCase(Locale.ROOT)))
                    || (term.is(SearchTerm.Kind.IP) && lower.contains(term.address()));
        }

        public List<SearchFactResponse> build() {
            List<SearchFactResponse> all = Lists.newArrayList(matched);
            all.addAll(other);

            return List.copyOf(all.size() > MAXIMUM_FACTS ? all.subList(0, MAXIMUM_FACTS) : all);
        }

    }

}
