package app.nzyme.core.search.sources;

import app.nzyme.core.briefing.BriefingLinks;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.rest.responses.search.SearchHitResponse;
import app.nzyme.core.search.SearchConditions;
import app.nzyme.core.search.SearchScope;
import app.nzyme.core.search.SearchTerm;
import app.nzyme.core.search.SqlSearchSource;
import com.google.common.collect.Lists;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AlertSearch extends SqlSearchSource {

    @Override
    public String id() {
        return "alerts";
    }

    @Override
    public String title() {
        return "Alerts";
    }

    @Override
    public boolean applies(SearchTerm term, SearchScope scope) {
        return scope.hasPermission("alerts_view");
    }

    @Override
    public Result search(Handle handle, Request request) {
        SearchTerm term = request.term();

        List<String> types = Lists.newArrayList();
        for (DetectionType type : DetectionType.values()) {
            if (type.getTitle().toLowerCase(Locale.ROOT).contains(term.lower())
                    || type.name().toLowerCase(Locale.ROOT).contains(term.lower())) {
                types.add(type.name());
            }
        }

        /*
         * Attributes hold single values and are compared as a whole, which is far cheaper than a pattern over
         * every attribute of every alert. Text is looked for in the details, which name what an alert is about.
         */
        SearchConditions conditions = new SearchConditions(term)
                .uuid("da.uuid")
                .custom("da.details ILIKE '%' || :s_mac || '%'", SearchTerm.Kind.MAC)
                .custom("da.id IN (SELECT a.detection_alert_id FROM detection_alert_attributes a "
                        + "WHERE a.attribute_value = :s_mac)", SearchTerm.Kind.MAC)
                .custom("da.details ~* :s_ip_in_text", SearchTerm.Kind.IP)
                .custom("da.id IN (SELECT a.detection_alert_id FROM detection_alert_attributes a "
                        + "WHERE a.attribute_value = :s_ip)", SearchTerm.Kind.IP);
        if (!term.is(SearchTerm.Kind.IP)) {
            conditions.contains("da.details");
        }
        if (!types.isEmpty()) {
            conditions.custom("da.detection_type IN (<s_types>)");
        }

        String sql = "SELECT da.uuid, da.detection_type, da.details, da.is_resolved, da.last_seen "
                + "FROM detection_alerts da "
                + "WHERE da.organization_id = :organization_id AND da.tenant_id = :tenant_id "
                + "AND " + conditions.sql() + " "
                + "ORDER BY da.is_resolved ASC, da.last_seen DESC, da.uuid ASC LIMIT :limit OFFSET :offset";

        List<SearchHitResponse> hits = Lists.newArrayList();
        for (Map<String, Object> row : rows(handle, sql, conditions, request, null, query -> {
            if (!types.isEmpty()) {
                query.bindList("s_types", types);
            }
        })) {
            String uuid = text(row, "uuid");
            hits.add(hit(
                    "ALERT",
                    title(text(row, "detection_type")),
                    text(row, "details"),
                    facts(term).add("Status", bool(row, "is_resolved") ? "Resolved" : "Active").build(),
                    time(row, "last_seen"),
                    score(term, uuid),
                    BriefingLinks.alert(uuid)
            ));
        }

        return Result.of(hits, request);
    }

    private static String title(String detectionType) {
        try {
            return DetectionType.valueOf(detectionType).getTitle();
        } catch (IllegalArgumentException | NullPointerException e) {
            return detectionType == null ? "Alert" : detectionType;
        }
    }

}
