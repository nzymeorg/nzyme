package app.nzyme.core.search;

import app.nzyme.core.search.sources.AlertSearch;
import app.nzyme.core.search.sources.ContextSearch;
import app.nzyme.core.search.sources.DeviceSearch;
import app.nzyme.core.search.sources.Dot11Search;
import app.nzyme.core.search.sources.EthernetSearch;
import app.nzyme.core.search.sources.ManagementSearch;
import app.nzyme.core.search.sources.TrafficSearch;
import com.google.common.collect.Lists;

import java.util.List;

public class SearchSources {

    // In the order results are shown when nothing else ranks them.
    public static List<SearchSource> all() {
        List<SearchSource> sources = Lists.newArrayList();

        sources.addAll(EthernetSearch.sources());
        sources.addAll(ContextSearch.sources());
        sources.addAll(Dot11Search.sources());
        sources.addAll(DeviceSearch.sources());
        sources.add(new AlertSearch());
        sources.addAll(TrafficSearch.sources());
        sources.addAll(ManagementSearch.sources());

        return sources;
    }

}
