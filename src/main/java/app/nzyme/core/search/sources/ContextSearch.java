package app.nzyme.core.search.sources;

import app.nzyme.core.rest.responses.search.SearchHitResponse;
import app.nzyme.core.search.SearchConditions;
import app.nzyme.core.search.SearchScope;
import app.nzyme.core.search.SearchSource;
import app.nzyme.core.search.SearchTerm;
import app.nzyme.core.search.SqlSearchSource;
import com.google.common.collect.Lists;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.Map;

public class ContextSearch {

    public static List<SearchSource> sources() {
        return List.of(new MacAddresses(), new Networks());
    }

    private static class MacAddresses extends SqlSearchSource {

        @Override
        public String id() {
            return "context_macs";
        }

        @Override
        public String title() {
            return "MAC Address Context";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return true;
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();

            SearchConditions conditions = new SearchConditions(term)
                    .mac("c.mac_address")
                    .uuid("c.uuid")
                    .contains("c.name", "c.description")
                    .custom("EXISTS (SELECT 1 FROM context_mac_addresses_transparent t WHERE t.context_id = c.id "
                            + "AND t.hostname ILIKE :s_contains)")
                    .custom("EXISTS (SELECT 1 FROM context_mac_addresses_transparent t WHERE t.context_id = c.id "
                            + "AND t.ip_address = :s_ip::inet)", SearchTerm.Kind.IP)
                    .custom("EXISTS (SELECT 1 FROM context_mac_addresses_transparent t WHERE t.context_id = c.id "
                            + "AND t.ip_address <<= :s_cidr::inet)", SearchTerm.Kind.CIDR);

            String sql = "SELECT c.uuid, c.mac_address, c.name, c.description, c.updated_at, "
                    + "(SELECT string_agg(x.hostname, ', ') FROM (SELECT DISTINCT t.hostname "
                    + "FROM context_mac_addresses_transparent t WHERE t.context_id = c.id "
                    + "AND t.hostname IS NOT NULL ORDER BY t.hostname LIMIT 3) x) AS hostnames, "
                    + "(SELECT string_agg(x.address, ', ') FROM (SELECT DISTINCT host(t.ip_address) AS address "
                    + "FROM context_mac_addresses_transparent t WHERE t.context_id = c.id "
                    + "AND t.ip_address IS NOT NULL ORDER BY 1 LIMIT 3) x) AS addresses "
                    + "FROM context_mac_addresses c "
                    + "WHERE c.organization_id = :organization_id AND c.tenant_id = :tenant_id "
                    + "AND " + conditions.sql() + " "
                    + "ORDER BY (c.name IS NULL OR c.name = '') ASC, c.name ASC, c.mac_address ASC "
                    + "LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request)) {
                String mac = text(row, "mac_address");
                String name = text(row, "name");

                hits.add(hit(
                        "CONTEXT_MAC",
                        name == null ? mac : name,
                        text(row, "description"),
                        facts(term)
                                .add("MAC Address", name == null ? null : mac)
                                .add("Hostname", row.get("hostnames"))
                                .add("IP Address", row.get("addresses"))
                                .build(),
                        time(row, "updated_at"),
                        score(term, mac, name, text(row, "uuid")),
                        link("CONTEXT_MAC", "uuid", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request);
        }

    }

    private static class Networks extends SqlSearchSource {

        @Override
        public String id() {
            return "context_networks";
        }

        @Override
        public String title() {
            return "Network Context";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return !term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();

            SearchConditions conditions = new SearchConditions(term)
                    .network("n.network")
                    .uuid("n.uuid")
                    .contains("n.name", "n.description")
                    .custom("n.network::text ILIKE :s_contains");

            String sql = "SELECT n.uuid, n.network::text AS network, n.name, n.description, n.updated_at "
                    + "FROM context_networks n "
                    + "WHERE n.organization_id = :organization_id AND n.tenant_id = :tenant_id "
                    + "AND " + conditions.sql() + " "
                    + "ORDER BY masklen(n.network) DESC, n.name ASC, n.uuid ASC LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request)) {
                String name = text(row, "name");
                String network = text(row, "network");

                hits.add(hit(
                        "CONTEXT_NETWORK",
                        name == null ? network : name,
                        text(row, "description"),
                        facts(term).add("Network", network).build(),
                        time(row, "updated_at"),
                        term.isAnyOf(SearchTerm.Kind.IP, SearchTerm.Kind.CIDR)
                                ? SCORE_EXACT : score(term, name, network, text(row, "uuid")),
                        link("CONTEXT_NETWORK", "uuid", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request);
        }

    }

}
