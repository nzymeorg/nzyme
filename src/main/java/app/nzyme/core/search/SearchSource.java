package app.nzyme.core.search;

import app.nzyme.core.rest.responses.briefing.BriefingLinkResponse;
import app.nzyme.core.rest.responses.search.SearchHitResponse;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.util.List;

public abstract class SearchSource {

    public enum Tier {
        INSTANT,
        RECORDED
    }

    public record Request(SearchTerm term,
                          SearchScope scope,
                          int limit,
                          int offset,
                          @Nullable SearchWindow window,
                          long deadline) {

        public SearchWindow windowOr(SearchWindow fallback) {
            return window == null ? fallback : window;
        }

        public Request withDeadline(long deadline) {
            return new Request(term, scope, limit, offset, window, deadline);
        }

    }

    public record Result(List<SearchHitResponse> hits,
                         boolean hasMore,
                         @Nullable DateTime searchedFrom,
                         @Nullable BriefingLinkResponse allLink) {

        // Sources request one row more than the limit to learn if there are more.
        public static Result of(List<SearchHitResponse> hits, Request request) {
            return of(hits, request, null, null);
        }

        public static Result of(List<SearchHitResponse> hits,
                                Request request,
                                @Nullable SearchWindow window,
                                @Nullable BriefingLinkResponse allLink) {
            boolean hasMore = hits.size() > request.limit();
            return new Result(
                    hasMore ? List.copyOf(hits.subList(0, request.limit())) : List.copyOf(hits),
                    hasMore,
                    window == null ? null : window.from(),
                    allLink
            );
        }

    }

    public abstract String id();

    public abstract String title();

    public Tier tier() {
        return Tier.INSTANT;
    }

    /*
     * Decides by the kind of query and by what the user may see. A source that does not apply is never searched
     * and never listed, so this is where permissions and subsystems are enforced.
     */
    public abstract boolean applies(SearchTerm term, SearchScope scope);

    public abstract Result search(Handle handle, Request request);

}
